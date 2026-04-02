
podTemplate(
  label: 'maven-pod',
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
      name: 'maven',
      image: 'maven:3.8.8-eclipse-temurin-17',
      command: 'cat',
      ttyEnabled: true,
      resourceRequestCpu: '500m',
      resourceRequestMemory: '1Gi',
      resourceLimitCpu: '1000m',
      resourceLimitMemory: '2Gi',
      envVars: [
        envVar(key: 'MAVEN_OPTS', value: '-Dmaven.repo.local=/home/jenkins/.m2/repository')
      ]
    ),
    containerTemplate(
      name: 'aws',
      image: 'amazon/aws-cli:2.17.0', // ví dụ
      command: 'cat',
      ttyEnabled: true
    ),
    containerTemplate(
      name: 'docker',
      image: 'docker:24-dind',
      privileged: true,
      ttyEnabled: true,
      command: 'dockerd-entrypoint.sh',
      args: '--insecure-registry=docker-internal.gitlabonlinecom.click'

    ),
    containerTemplate(
      name: 'trivy',
      image: 'aquasec/trivy:0.69.3',
      command: 'cat',
      ttyEnabled: true

    )
  ],
) {
  def SONARQUBE_SERVER  = "SonarQube"
  def SONAR_PROJECT_KEY = "shopping-cart"
  def APP_VERSION       = "1.0.0"
  def APP_NAME          = "shopping-cart"

  def NEXUS_REGISTRY   = "docker-internal.gitlabonlinecom.click"
  def IMAGE_NAME       = "dev-backend/${APP_NAME}"   // path trên nexus-hosted

  // URL HTTPS của GitOps repo
  def GITOPS_REPO_URL       = "http://gitlab.gitlabonlinecom.click/dev/test/gitops-repo-deploy-argocd-test.git"  // đổi cho đúng
  def GITOPS_CREDENTIALS_ID = "gitlab-token-dev-group"

  node('maven-pod') {
    stage('Checkout') {
      echo '[Checkout] Lấy source từ SCM'
      checkout scm
      // TÍNH GIT_SHORT SAU KHI CHECKOUT
      script {
        try {
          GIT_SHORT_SHA = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
        } catch (e) {
          GIT_SHORT_SHA = env.BUILD_NUMBER
        }
        //TARFILE_NAME   = "image-${ECR_REPO_NAME}-${IMAGE_TAG}.tar"
        //echo "GIT_SHORT = ${GIT_SHORT}, IMAGE_TAG = ${IMAGE_TAG}, TARFILE_NAME = ${TARFILE_NAME}"
      }
    }
    
    stage('Maven Build') {
      container('maven') {
        sh 'mvn -B clean compile'
      }
    }

    stage('Maven Test') {
      container('maven') {
        sh 'mvn test -DskipTests=true'
      }
      // junit 'target/surefire-reports/*.xml'
    }
    
    stage('Scan Source (SonarQube)') {
      container('maven') {
        withSonarQubeEnv(SONARQUBE_SERVER) {
          sh """
            mvn org.sonarsource.scanner.maven:sonar-maven-plugin:3.11.0.3922:sonar \
              -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
              -Dsonar.projectVersion=${APP_NAME}-${GIT_SHORT_SHA} \
              -Dsonar.java.binaries=.
          """
        }
      }
    }

    stage('Quality Gate') {
      script {
        timeout(time: 5, unit: 'MINUTES') {
          def qg = waitForQualityGate()
          if (qg.status != 'OK') {
            error "Quality Gate failed: ${qg.status}"
          }
        }
      }
    }

    stage('Build Source Code') {
      container('maven') {
          echo 'Building Source code...'
          // Build the source code (skipping tests)
          sh 'mvn package -DskipTests=true'
          sh 'ls -lh target/'
      }
    }

    stage('Docker Build & Upload FILE IMAGE TAR TO S3') {
      container('docker') {
        script {
          def branch = env.BRANCH_NAME ?: ''   // nếu null thì thành chuỗi rỗng
          def envPrefix
          if (branch == 'develop') {
            envPrefix = 'test'
          } else if (branch.startsWith('release/')) {
            envPrefix = 'stg'
          } else if (branch == 'main') {
            envPrefix = 'prod'
          } else {
            envPrefix = 'test'
          }

          IMAGE_TAG      = "${envPrefix}-${GIT_SHORT_SHA}"
          TARFILE_NAME = "image-${APP_NAME}-${IMAGE_TAG}.tar"
        }

        echo "[Docker] Build & save TAR ${TARFILE_NAME}"

        withCredentials([usernamePassword(
          credentialsId: 'docker-user-internal',   // dùng luôn user docker nội bộ
          usernameVariable: 'REG_USER',
          passwordVariable: 'REG_PASS'
        ),
        usernamePassword(
          credentialsId: 'nexus-raw-creds',
          usernameVariable: 'NX_USER',
          passwordVariable: 'NX_PASS'
    )]) {
          sh """
            echo "${REG_PASS}" | docker login docker-internal.gitlabonlinecom.click -u "${REG_USER}" --password-stdin

            # 1. Build image
            docker build -t docker-internal.gitlabonlinecom.click/dev-backend/${APP_NAME}:${IMAGE_TAG} .

            # 2. Save ra file TAR
            docker save docker-internal.gitlabonlinecom.click/dev-backend/${APP_NAME}:${IMAGE_TAG} -o ${TARFILE_NAME}
            ls -lh ${TARFILE_NAME}

            docker logout docker-internal.gitlabonlinecom.click

            # Cài curl nếu image docker:dind không có
            if ! command -v curl >/dev/null 2>&1; then
              apk add --no-cache curl
            fi

            echo "[RAW] Upload ${TARFILE_NAME} lên Nexus RAW"
            ls -lh
            curl -v -w "\nHTTP_CODE:%{http_code}\n" \
              -u "${NX_USER}:${NX_PASS}" \
              --upload-file ${TARFILE_NAME} \
              "http://nexus.gitlabonlinecom.click/repository/raw-artifacts/dev-backend/docker-tar/${TARFILE_NAME}"
          """
          }


      }
    }

    stage('Trivy Scan Image TAR FILE') {
      container('trivy') {
        withCredentials([usernamePassword(
          credentialsId: 'nexus-raw-creds',   // user có quyền trên raw-artifacts
          usernameVariable: 'NX_USER',
          passwordVariable: 'NX_PASS'
      )]) {
          sh """

            # Cài curl nếu chưa có (alpine base)
            if ! command -v curl >/dev/null 2>&1; then
              apk add --no-cache curl
            fi

            echo "[Trivy] Download TAR from Nexus RAW: ${TARFILE_NAME}"
            curl -u "${NX_USER}:${NX_PASS}" \\
              -o ${TARFILE_NAME} \\
              "http://nexus.gitlabonlinecom.click/repository/raw-artifacts/dev-backend/docker-tar/${TARFILE_NAME}"

            echo "[Trivy] Scan image TAR ${TARFILE_NAME}"

            # Scan LOW + MEDIUM (không fail pipeline)
            trivy image --input ${TARFILE_NAME} \\
              --exit-code 0 \\
              --severity LOW,MEDIUM

            # Scan HIGH + CRITICAL (tạm để exit-code 0, đổi 1 nếu muốn fail)
            trivy image --input ${TARFILE_NAME} \\
              --exit-code 0 \\
              --severity HIGH,CRITICAL
          """
        }
      }
    }

    // 5. Push image lên ECR (CHỈ CHẠY nếu các stage trước SUCCESS)
    stage('Push Docker Image to ECR') {
      container('docker') {
        withCredentials([usernamePassword(
          credentialsId: 'docker-user-internal',   // user có quyền push dev-backend/*
          usernameVariable: 'REG_USER',
          passwordVariable: 'REG_PASS'
      )]) {
        sh """
            echo "${REG_PASS}" | docker login docker-internal.gitlabonlinecom.click -u "${REG_USER}" --password-stdin

            echo "[Nexus] Push image docker-internal.gitlabonlinecom.click/dev-backend/${APP_NAME}:${IMAGE_TAG}"
            docker push docker-internal.gitlabonlinecom.click/dev-backend/${APP_NAME}:${IMAGE_TAG}

            docker logout docker-internal.gitlabonlinecom.click
          """
        }
      }
    }

  stage('Update GitOps Repo Argocd') {
    container('maven') {
      script {

        // Map branch -> folder env
        def branch = env.BRANCH_NAME ?: ''
        def envPath
        def gitTargetBranch

        if (branch == 'develop') {
          envPath = 'test'
          gitTargetBranch = 'develop' 
        } else if (branch.startsWith('release/')) {
          envPath = 'staging'
          gitTargetBranch = 'staging' 
        } else if (branch == 'main') {
          envPath = 'prod'
          gitTargetBranch = 'main'
        } else {
          envPath = 'test'
          gitTargetBranch = 'develop' 
        }

        echo "[GitOps] Update ${envPath}/values.yaml với image tag: ${IMAGE_TAG}"

        withCredentials([usernamePassword(
          credentialsId: GITOPS_CREDENTIALS_ID,
          usernameVariable: 'GIT_USER',
          passwordVariable: 'GIT_PASS'
        )]) {
          sh """
            mkdir -p gitops-work
            cd gitops-work

            # clone với user + token
            git config --global credential.helper store
            printf "http://${GIT_USER}:${GIT_PASS}@${GITOPS_REPO_URL.replace('http://','')}\n" > ~/.git-credentials

            git clone ${GITOPS_REPO_URL} .
            git checkout ${gitTargetBranch} || git checkout -b ${gitTargetBranch}
            cd ${envPath}

            # cài yq nếu chưa có
            if ! command -v yq >/dev/null 2>&1; then
              apt-get update -y
              wget -qO /usr/local/bin/yq https://github.com/mikefarah/yq/releases/latest/download/yq_linux_amd64
              chmod +x /usr/local/bin/yq
            fi

            yq e '.image.tag = "${IMAGE_TAG}"' -i values.yaml

            git config user.email "jenkins-bot@example.com"
            git config user.name  "Jenkins Bot"

            git status
            git commit -am "Update ${APP_NAME} image tag to ${IMAGE_TAG} for ${envPath}" || echo "No changes to commit"
            git push origin ${gitTargetBranch}
          """
        }
      }
    }
  }


  }
}