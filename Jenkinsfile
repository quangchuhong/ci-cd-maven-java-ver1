// CI/CD for shopping-cart (Spring Boot 1.5, Java 8) on EKS:
// Maven build/test -> SonarQube + Quality Gate -> Nexus (jar) -> Docker image
// -> Trivy (server mode) -> Nexus docker registry -> GitOps repo (Argo CD).
//
// Branch -> environment
//   develop    -> test     (GitOps branch develop, folder test), then optional promote to staging
//   release/*  -> staging  (GitOps branch staging, folder staging)
//   main       -> prod     (GitOps branch main,    folder prod)

def APP_NAME          = 'shopping-cart'
def APP_VERSION       = '1.0.0'                 // release/<APP_VERSION> branch in the GitOps repo

def SONARQUBE_SERVER  = 'SonarQube'             // Manage Jenkins > System > SonarQube servers
def SONAR_PROJECT_KEY = 'shopping-cart'

// In-cluster endpoints (no ALB hop)
def NEXUS_URL         = 'http://nexus.nexus.svc.cluster.local:8081'
def TRIVY_SERVER      = 'http://trivy.trivy.svc.cluster.local:9090'

def NEXUS_REGISTRY    = 'docker-internal.gitlabonlinecom.click'   // Nexus docker-hosted (HTTP connector 8000)
def IMAGE_REPO        = "${NEXUS_REGISTRY}/dev-backend/${APP_NAME}"

def GITOPS_REPO_URL       = 'http://gitlab.gitlabonlinecom.click/dev/test/gitops-repo-deploy-argocd-test.git'
def GITOPS_CREDENTIALS_ID = 'gitlab-token-dev-group'
def RELEASE_BRANCH        = "release/${APP_VERSION}"

def NEXUS_CREDENTIALS_ID  = 'nexus-creds'           // read maven-public, deploy maven-snapshots/releases
def DOCKER_CREDENTIALS_ID = 'docker-user-internal'  // push dev-backend/* on docker-hosted

// '1' = fail the build on HIGH/CRITICAL findings. Spring Boot 1.5 has many, keep '0' until it is upgraded.
def TRIVY_EXIT_CODE = '0'

def YQ_VERSION = 'v4.44.3'

def podSpec = [
  containers: [
    containerTemplate(
      name: 'jnlp',
      image: 'jenkins/inbound-agent:latest-jdk17',
      args: '${computer.jnlpmac} ${computer.name}',
      resourceRequestCpu: '200m',
      resourceRequestMemory: '256Mi',
      resourceLimitCpu: '500m',
      resourceLimitMemory: '512Mi'
    ),
    containerTemplate(
      // JDK 8: Spring Boot 1.5 / Hibernate 5.0 do not start on JDK 11+ (javax.xml.bind removed)
      name: 'maven',
      image: 'maven:3.9.9-eclipse-temurin-8',
      command: 'cat',
      ttyEnabled: true,
      resourceRequestCpu: '500m',
      resourceRequestMemory: '1Gi',
      resourceLimitCpu: '1000m',
      resourceLimitMemory: '2Gi'
    ),
    containerTemplate(
      // SonarQube 2025.x needs the scanner on Java 17+ (analysis only, the build stays on JDK 8)
      name: 'maven17',
      image: 'maven:3.9.9-eclipse-temurin-17',
      command: 'cat',
      ttyEnabled: true,
      resourceRequestCpu: '500m',
      resourceRequestMemory: '1Gi',
      resourceLimitCpu: '1000m',
      resourceLimitMemory: '2Gi'
    ),
    containerTemplate(
      name: 'docker',
      image: 'docker:24-dind',
      privileged: true,
      ttyEnabled: true,
      command: 'dockerd-entrypoint.sh',
      args: "--insecure-registry=${NEXUS_REGISTRY}"
    ),
    containerTemplate(
      // Same version as the Trivy server (chart trivy 0.20.1, appVersion 0.68.2)
      name: 'trivy',
      image: 'aquasec/trivy:0.68.2',
      command: 'cat',
      ttyEnabled: true
    )
  ]
]

// Maven settings without secrets: credentials come from env vars bound by withCredentials
def MAVEN_SETTINGS = """<settings>
  <servers>
    <server><id>nexus</id><username>\${env.NX_USER}</username><password>\${env.NX_PASS}</password></server>
    <server><id>maven-releases</id><username>\${env.NX_USER}</username><password>\${env.NX_PASS}</password></server>
    <server><id>maven-snapshots</id><username>\${env.NX_USER}</username><password>\${env.NX_PASS}</password></server>
  </servers>
  <mirrors>
    <mirror><id>nexus</id><mirrorOf>*</mirrorOf><url>${NEXUS_URL}/repository/maven-public/</url></mirror>
  </mirrors>
</settings>
"""

// Clone the GitOps repo, set image repository/tag in <folder>/values.yaml, push <branch>.
// The token is passed through a credential helper reading env vars: it never lands in a file or the log.
def updateGitOps = { String repoUrl, String credId, String branch, String baseBranch, String folder,
                     String imageRepo, String imageTag, String yqVersion ->
  withCredentials([usernamePassword(credentialsId: credId, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
    sh """
      set -e
      if ! command -v yq >/dev/null 2>&1; then
        curl -sSfL -o /usr/local/bin/yq https://github.com/mikefarah/yq/releases/download/${yqVersion}/yq_linux_amd64
        chmod +x /usr/local/bin/yq
      fi

      git config --global credential.helper '!f() { echo username=\$GIT_USER; echo password=\$GIT_PASS; }; f'
      git config --global user.email 'jenkins-bot@gitlabonlinecom.click'
      git config --global user.name  'Jenkins Bot'

      rm -rf gitops-work
      git clone ${repoUrl} gitops-work
      cd gitops-work
      if git ls-remote --exit-code --heads origin ${branch} >/dev/null 2>&1; then
        git checkout ${branch}
      else
        echo "[GitOps] ${branch} does not exist yet, creating it from ${baseBranch}"
        git checkout ${baseBranch}
        git checkout -b ${branch}
      fi

      yq e '.image.repository = "${imageRepo}"' -i ${folder}/values.yaml
      yq e '.image.tag = "${imageTag}"' -i ${folder}/values.yaml

      git add ${folder}/values.yaml
      git commit -m "Deploy ${APP_NAME} ${imageTag} to ${folder}" || echo "[GitOps] No changes to commit"
      git push origin ${branch}
    """
  }
}

def branch = env.BRANCH_NAME ?: 'develop'
def envFolder
def gitopsBranch
if (branch == 'main') {
  envFolder = 'prod';    gitopsBranch = 'main'
} else if (branch.startsWith('release/')) {
  envFolder = 'staging'; gitopsBranch = 'staging'
} else {
  envFolder = 'test';    gitopsBranch = 'develop'
}

def IMAGE_TAG
def TARFILE

podTemplate(podSpec) {
  node(POD_LABEL) {
    stage('Checkout') {
      checkout scm
      IMAGE_TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim()
      TARFILE   = "image-${APP_NAME}-${IMAGE_TAG}.tar"
      echo "[Checkout] branch=${branch} env=${envFolder} tag=${IMAGE_TAG}"
    }

    withCredentials([usernamePassword(credentialsId: NEXUS_CREDENTIALS_ID, usernameVariable: 'NX_USER', passwordVariable: 'NX_PASS')]) {
      writeFile file: '.ci-settings.xml', text: MAVEN_SETTINGS

      stage('Build & Unit Test') {
        container('maven') {
          // verify = compile + test + package + JaCoCo report (target/site/jacoco/jacoco.xml)
          sh 'mvn -B -s .ci-settings.xml clean verify'
        }
        junit 'target/surefire-reports/*.xml'
      }

      stage('SonarQube Analysis') {
        container('maven17') {
          withSonarQubeEnv(SONARQUBE_SERVER) {
            sh """
              mvn -B -s .ci-settings.xml org.sonarsource.scanner.maven:sonar-maven-plugin:5.1.0.4751:sonar \
                -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                -Dsonar.projectVersion=${IMAGE_TAG} \
                -Dsonar.java.binaries=target/classes \
                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
            """
          }
        }
      }

      stage('Quality Gate') {
        // Needs the SonarQube webhook http://jenkins.jenkins.svc.cluster.local:8080/sonarqube-webhook/
        timeout(time: 5, unit: 'MINUTES') {
          def qg = waitForQualityGate()
          if (qg.status != 'OK') {
            error "Quality Gate failed: ${qg.status}"
          }
        }
      }

      stage('Publish Jar to Nexus') {
        container('maven') {
          sh 'mvn -B -s .ci-settings.xml deploy -DskipTests'
        }
      }
    }

    stage('Docker Build') {
      container('docker') {
        sh """
          timeout 60 sh -c 'until docker info >/dev/null 2>&1; do sleep 2; done'
          docker build -t ${IMAGE_REPO}:${IMAGE_TAG} .
          docker save ${IMAGE_REPO}:${IMAGE_TAG} -o ${TARFILE}
          ls -lh ${TARFILE}
        """
      }
    }

    stage('Trivy Scan Image') {
      // The workspace is shared by all containers of the pod: scan the tar directly
      container('trivy') {
        sh """
          trivy image --server ${TRIVY_SERVER} --input ${TARFILE} \
            --severity LOW,MEDIUM --exit-code 0 --no-progress
          trivy image --server ${TRIVY_SERVER} --input ${TARFILE} \
            --severity HIGH,CRITICAL --exit-code ${TRIVY_EXIT_CODE} --no-progress
        """
      }
    }

    stage('Push Image to Nexus') {
      container('docker') {
        withCredentials([usernamePassword(credentialsId: DOCKER_CREDENTIALS_ID, usernameVariable: 'REG_USER', passwordVariable: 'REG_PASS')]) {
          sh """
            echo "\$REG_PASS" | docker login ${NEXUS_REGISTRY} -u "\$REG_USER" --password-stdin
            docker push ${IMAGE_REPO}:${IMAGE_TAG}
            docker logout ${NEXUS_REGISTRY}
          """
        }
      }
    }

    stage("Update GitOps (${envFolder})") {
      container('maven') {
        updateGitOps(GITOPS_REPO_URL, GITOPS_CREDENTIALS_ID, gitopsBranch, gitopsBranch, envFolder,
                     IMAGE_REPO, IMAGE_TAG, YQ_VERSION)
      }
    }
  }
}

// Promotion test -> staging only for develop builds.
// The approvals run outside of any node, so no agent pod is held while waiting.
if (envFolder == 'test') {
  def promote = false
  stage('Approve Promote to Staging') {
    try {
      timeout(time: 30, unit: 'MINUTES') {
        input message: "Promote ${APP_NAME}:${IMAGE_TAG} to STAGING (${RELEASE_BRANCH})?", ok: 'Promote'
      }
      promote = true
    } catch (err) {
      echo "[Promote] Not approved (${err}), staging unchanged."
    }
  }

  if (promote) {
    podTemplate(podSpec) {
      node(POD_LABEL) {
        stage('Deploy Staging (GitOps)') {
          container('maven') {
            updateGitOps(GITOPS_REPO_URL, GITOPS_CREDENTIALS_ID, RELEASE_BRANCH, 'develop', 'staging',
                         IMAGE_REPO, IMAGE_TAG, YQ_VERSION)
          }
        }
      }
    }

    def rollback = false
    stage('Approve Rollback Staging') {
      try {
        timeout(time: 30, unit: 'MINUTES') {
          rollback = input(
            message: "Rollback STAGING from ${APP_NAME}:${IMAGE_TAG}? (Argo CD returns to the previous version)",
            ok: 'Submit',
            parameters: [booleanParam(name: 'CONFIRM_ROLLBACK', defaultValue: false, description: 'Tick to confirm the rollback')]
          )
        }
      } catch (err) {
        echo "[Rollback] No answer (${err}), keeping ${IMAGE_TAG} on staging."
      }
    }

    if (rollback) {
      podTemplate(podSpec) {
        node(POD_LABEL) {
          stage('Rollback Staging (git revert)') {
            container('maven') {
              withCredentials([usernamePassword(credentialsId: GITOPS_CREDENTIALS_ID, usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
                sh """
                  set -e
                  git config --global credential.helper '!f() { echo username=\$GIT_USER; echo password=\$GIT_PASS; }; f'
                  git config --global user.email 'jenkins-bot@gitlabonlinecom.click'
                  git config --global user.name  'Jenkins Bot'

                  rm -rf gitops-rollback
                  git clone --branch ${RELEASE_BRANCH} ${GITOPS_REPO_URL} gitops-rollback
                  cd gitops-rollback

                  # Revert only if HEAD is the deploy commit of this build
                  if git log -1 --format=%s | grep -q "Deploy ${APP_NAME} ${IMAGE_TAG} to staging"; then
                    git revert --no-edit HEAD
                    git push origin ${RELEASE_BRANCH}
                  else
                    echo "[Rollback] HEAD is not the deploy commit of ${IMAGE_TAG}, nothing reverted:"
                    git log -1 --oneline
                    exit 1
                  fi
                """
              }
            }
          }
        }
      }
    } else {
      echo '[Rollback] Not requested.'
    }
  }
}
