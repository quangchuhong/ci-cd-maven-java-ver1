// CI for shopping-cart with the shared library ci-lib (GitLab devops/ci-cd/jenkins-shared-library,
// guide: terraform-eks-devops-tools docs/jenkins/07-shared-library.md, branch model: docs/jenkins/09).
//
// Checkout (branch + version rules) -> Build & Test (JDK 8) -> SonarQube (JDK 17) -> Quality Gate
// -> Docker Build -> Trivy (reports + SBOM) -> Publish jar -> Push Image -> Tag vX.Y.Z (main only).
//
//   feature/*, MR       x.y.z-SNAPSHOT        verify only (no publish, no push)
//   develop             x.y.z-SNAPSHOT        maven-snapshots + image x.y.z-SNAPSHOT-<commit>
//   release/x.y         x.y.z-rc.N / x.y.z    maven-releases + image x.y.z-rc.N-<commit> (bump rc every build)
//   main, hotfix/*      x.y.z                 maven-releases + image, no Trivy waiver; main tags vX.Y.Z
// The version lives in pom.xml; see docs/branch-workflow.md for the commands of each step.
//
// trivyWaiverUntil: Spring Boot 1.5 has many HIGH/CRITICAL CVEs. Until this date (approved by Security)
// findings make feature/develop/release builds UNSTABLE instead of FAILED. The waiver does not apply to
// main and hotfix/*: upgrade Spring Boot (or record per-CVE exceptions) before the first release to main.
@Library('ci-lib@v1.1.0') _

mavenPipeline(
  name: 'shopping-cart',
  javaVersion: '8',
  trivyWaiverUntil: '2026-12-31'
)
