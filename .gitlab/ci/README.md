# GitLab CI (test)

`.gitlab-ci.yml` runs the same flow and branch rules as the Jenkins pipeline (`ci-lib`) on the platform
GitLab Runner. It is meant for a **separate GitLab test project** (for example
`devops/ci-cd/shopping-cart-gitlab-ci`), not for `devops/ci-cd/shopping-cart` that Jenkins builds:
two CI systems on one project would build, scan and publish twice.

Results go to their own names: image `dev-backend/shopping-cart-gitlab`, SonarQube project
`shopping-cart-gitlab`; the jar is not deployed (`PUBLISH_JAR: "false"`).

Guide (Vietnamese): terraform-eks-devops-tools `docs/gitlab/10-gitlab-ci.md`, section 10.11.
