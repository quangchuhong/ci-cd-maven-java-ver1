// CI for shopping-cart with the shared library ci-lib (GitLab devops/ci-cd/jenkins-shared-library,
// guide: terraform-eks-devops-tools docs/jenkins/07-shared-library.md).
//
// Checkout -> Build & Test (JDK 8) -> SonarQube (JDK 17) -> Quality Gate -> Publish jar -> Docker Build
// -> Trivy (reports + SBOM) -> Push Image. Publish and Push only run on main, develop and release/*.
//
// trivyWaiverUntil: Spring Boot 1.5 has many HIGH/CRITICAL CVEs. Until this date (approved by Security)
// findings make the build UNSTABLE instead of FAILED; upgrade Spring Boot and remove the waiver.
// CD (GitOps update, promotion, rollback) is not in ci-lib v1.0.0 yet: see the previous Jenkinsfile in git history.
@Library('ci-lib@v1.0.0') _

mavenPipeline(
  name: 'shopping-cart',
  javaVersion: '8',
  trivyWaiverUntil: '2026-12-31'
)
