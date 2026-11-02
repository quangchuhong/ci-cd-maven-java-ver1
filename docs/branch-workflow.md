# Quy trình nhánh và phát hành `shopping-cart`

Pipeline: `Jenkinsfile` gọi `mavenPipeline` của Shared Library `ci-lib@v1.1.0`. Pipeline tự kiểm tra version trong `pom.xml` theo nhánh, nên chỉ cần làm đúng các bước dưới đây. Chuẩn đầy đủ: `terraform-eks-devops-tools/docs/jenkins/09-quy-trinh-nhanh-va-phat-hanh.md`.

## Bảng tóm tắt

| Nhánh | Mục đích | Tạo từ → merge vào | Version `pom.xml` | Pipeline làm gì | Môi trường (khi bật CD) |
|---|---|---|---|---|---|
| `feature/*`, MR | Phát triển, review | `develop` → MR vào `develop` | `x.y.z-SNAPSHOT` | Build, test, Sonar, Quality Gate, Docker build, Trivy. **Không** publish, **không** push | Không deploy |
| `develop` | Tích hợp hằng ngày | Nhận MR từ `feature/*`, merge ngược từ `release/*`, `hotfix/*` | `1.0.0-SNAPSHOT` | Như trên + `mvn deploy` → `maven-snapshots`, push image `1.0.0-SNAPSHOT-<commit>` | test |
| `release/x.y` | Ứng viên phát hành: UAT, staging, kiểm thử bảo mật | `develop` → MR vào `main` khi đạt | `1.0.0-rc.1`, `rc.2`… rồi `1.0.0` | Bản `-rc.N`: `mvn deploy` → `maven-releases` (không ghi đè), push image `1.0.0-rc.1-<commit>`. Bản `1.0.0` cuối: chỉ kiểm tra (publish từ `main`) | staging |
| `main` | Production | Chỉ MR từ `release/*`, `hotfix/*` | `x.y.z` | Như `release/*`, **không waiver Trivy**, tạo tag Git `vX.Y.Z` | prod |
| `hotfix/x.y.z` | Sửa gấp production | `main` → MR vào `main` và `develop` | `x.y.z-rc.N`, rồi `x.y.z` | Như `release/*`, không waiver | staging → prod |

Thứ tự stage: Checkout (kiểm tra nhánh/version) → Build & Test → SonarQube → Quality Gate → Docker Build → Trivy → Publish jar → Push image → Tag (chỉ `main`). Jar chỉ lên Nexus khi mọi cổng đều đạt. Mỗi version chính thức `x.y.z` chỉ được publish **một lần, từ `main`**; trên `release/*`/`hotfix/*` chỉ bản `-rc.N` được publish.

Pipeline dừng ngay ở Checkout khi:
- `feature/*`, `develop` không có `-SNAPSHOT`;
- `release/x.y` không phải `x.y.z-rc.N` hoặc `x.y.z`, hoặc version không bắt đầu bằng `x.y` của tên nhánh;
- `main` không phải `x.y.z`;
- MR vào `main` không đến từ `release/*`/`hotfix/*`, hoặc còn mang `-rc.N`.

Đổi version: `mvn versions:set -DnewVersion=<version> -DgenerateBackupPoms=false` (hoặc sửa thẻ `<version>` đầu tiên trong `pom.xml`).

## 1. Phát triển tính năng (`feature/*`)

```bash
git checkout develop && git pull gitlab develop
git checkout -b feature/cart-discount
git commit -am "Cart: discount for VIP customers"
git push gitlab feature/cart-discount
```

Jenkins build nhánh `feature/cart-discount` (version `1.0.0-SNAPSHOT` kế thừa từ `develop`): chỉ kiểm tra, không đẩy gì lên Nexus. Tạo MR vào `develop`; Jenkins build thêm `MR-<n>` và báo trạng thái về GitLab.

## 2. Tích hợp (`develop`)

Merge MR vào `develop` → Jenkins build `develop`:
- jar `com.reljicd:shopping-cart:1.0.0-SNAPSHOT` vào `maven-snapshots` (mỗi lần có timestamp riêng);
- image `docker-internal.gitlabonlinecom.click/dev-backend/shopping-cart:1.0.0-SNAPSHOT-<commit>`.

## 3. Ứng viên phát hành (`release/1.0`)

```bash
git checkout develop && git pull gitlab develop
git checkout -b release/1.0
mvn versions:set -DnewVersion=1.0.0-rc.1 -DgenerateBackupPoms=false
git commit -am "Release candidate 1.0.0-rc.1"
git push gitlab release/1.0
```

Jenkins build `release/1.0`: jar `1.0.0-rc.1` vào `maven-releases`, image `1.0.0-rc.1-<commit>`. UAT và kiểm thử bảo mật trên staging.

Có lỗi cần sửa trên nhánh release: **mỗi lần push phải tăng rc** (`maven-releases` không nhận cùng version hai lần; pipeline báo rõ nếu quên):

```bash
git checkout release/1.0
git commit -am "Fix: ..."
mvn versions:set -DnewVersion=1.0.0-rc.2 -DgenerateBackupPoms=false
git commit -am "Release candidate 1.0.0-rc.2"
git push gitlab release/1.0
```

Merge các bản sửa ngược về `develop` (MR `release/1.0` → `develop`; giữ version `-SNAPSHOT` của `develop` khi giải quyết xung đột ở `pom.xml`).

## 4. Phát hành (`main`)

Khi UAT đạt:

```bash
git checkout release/1.0
mvn versions:set -DnewVersion=1.0.0 -DgenerateBackupPoms=false
git commit -am "Release 1.0.0"
git push gitlab release/1.0
```

1. Jenkins build `release/1.0` với `1.0.0`: chỉ kiểm tra, **không** publish/push (log: "1.0.0 is a final version: verified here, published and pushed from main after the merge"). Tạo MR `release/1.0` → `main` (2 người duyệt). Build `MR-<n>` kiểm tra nguồn là `release/*`, version là `1.0.0` và Trivy **không có waiver**.
2. Merge → Jenkins build `main`: jar `1.0.0` vào `maven-releases`, image `1.0.0-<commit>`, tag Git `v1.0.0`.
3. Merge `main` về `develop`, rồi tăng `develop` lên version kế tiếp:

```bash
git checkout develop && git pull gitlab develop
git merge --no-ff origin/main
mvn versions:set -DnewVersion=1.1.0-SNAPSHOT -DgenerateBackupPoms=false
git commit -am "Start 1.1.0-SNAPSHOT"
git push gitlab develop
```

Nếu quên bước tăng version, build `develop` dừng ở Checkout với thông báo "develop builds use x.y.z-SNAPSHOT".

## 5. Sửa gấp production (`hotfix/*`)

```bash
git checkout main && git pull gitlab main
git checkout -b hotfix/1.0.1
mvn versions:set -DnewVersion=1.0.1-rc.1 -DgenerateBackupPoms=false
git commit -am "Hotfix 1.0.1-rc.1: ..."
git push gitlab hotfix/1.0.1
```

Build `hotfix/1.0.1`: publish `1.0.1-rc.1` và push image `1.0.1-rc.1-<commit>` (không waiver) để kiểm tra trên staging. Đạt thì đặt `1.0.1`, push (chỉ kiểm tra), MR vào `main` → `main` publish `1.0.1`, push image, tag `v1.0.1`; merge `main` về `develop`.

## Lưu ý cho `shopping-cart`

- **Spring Boot 1.5.3 có nhiều CVE HIGH/CRITICAL.** `trivyWaiverUntil: '2026-12-31'` cho phép `feature/*`, `develop`, `release/*` chạy tiếp (build UNSTABLE). Waiver **không áp dụng** cho MR vào `main`, `main`, `hotfix/*`: trước lần phát hành đầu tiên phải nâng Spring Boot hoặc ghi ngoại lệ từng CVE trong `.trivyignore` có Security duyệt. Waiver hết hạn ngày 2026-12-31: sau ngày đó cả `develop` cũng FAILED.
- **Tag `vX.Y.Z`** do Jenkins tạo bằng credential `gitlab-token-dev-group`. Nếu `v*` là protected tag, token phải có quyền tạo tag (Maintainer) trên project.
- **GitLab**: protected branches `main`, `release/*`, `develop`; protected tags `v*`; "Pipelines must succeed" cho MR. **Jenkins**: bộ lọc nhánh `main develop release/* hotfix/* feature/*` và bật *Discover merge requests from origin*.
