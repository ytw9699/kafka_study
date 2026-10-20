# KS-30: README 실행 절차에 "먼저 빌드" 단계 추가

| | |
|---|---|
| **타입** | DOCS |
| **상태** | DONE |
| **단계** | - (문서 보강) |
| **브랜치** | `docs/KS-30-readme-build-first` |
| **PR** | - |

## 배경

[KS-28](KS-28-readme-onboarding.md) 의 README 대로 백엔드 터미널 4개를 **거의 동시에** 띄우자 2개가 `BUILD FAILED` 로 죽었다.

```
java.nio.file.NoSuchFileException:
  backend/common/build/kotlin/compileKotlin/cacheable/dirty-sources.txt
Cannot access output property ... of task ':common:compileKotlin'.
```

`bootRun` 은 실행 전에 컴파일을 한다. 아직 빌드 결과가 없는 상태에서 Gradle 4개가 **같은 `common/build` 폴더를 동시에 컴파일**하다 서로의 파일을 지우고 덮어썼다.

KS-28 검증 때는 드러나지 않았다. 그때는 이미 `./gradlew build` 를 한 번 해 둔 상태였기 때문이다.

## 작업 내용

- [x] README 에 "③ 백엔드 빌드 — 한 번만" 단계 추가, 이후 번호 조정
- [x] GitHub 에서 **새로 클론한 사본**으로 README 순서 그대로 재검증

## 검증 — 새로 클론한 사본에서

| 단계 | 결과 |
|---|---|
| `git clone` (dev, `75808fe`) | - |
| 인프라 · 토픽 생성 | 정상 |
| `./gradlew build` | `BUILD SUCCESSFUL` (테스트 포함) |
| 백엔드 4개 + 프론트 동시 기동 | 5개 모두 기동, 워커가 파티션 `[0]` `[1]` `[2]` 하나씩 배정 |
| 실험 A — `USER_ID` 연속 거래 | **일치 ✓** |
| 실험 A — `RANDOM` 전환 후 연속 거래 | **불일치 5명 😱** |

## 탈락시킨 대안

| 대안 | 탈락 이유 |
|---|---|
| "터미널을 하나씩 천천히 띄우라"고 적는다 | 얼마나 기다려야 하는지 모호하고, 그래도 겹칠 수 있다 |
| 실행용 스크립트로 묶는다 | 워커를 하나씩 띄우고 죽이는 것이 실험 자체라 터미널을 나눠 두는 편이 낫다 |

## 배운 것

- **검증 환경이 깨끗하지 않으면 검증이 아니다.** KS-28 에서 "깨끗한 환경"이라고 한 것은 Docker 볼륨뿐이었고, 빌드 폴더는 이미 차 있었다.
  처음 보는 사람의 환경은 **클론 직후**다. 이번에는 GitHub 에서 새로 클론해 확인했다.
- **같은 빌드 폴더를 쓰는 프로세스 여러 개는 공유 자원을 다투는 동시 작업이다.** 워커들이 같은 잔액 행을 다투던 lost update 와 구조가 같다 — 먼저 한 번 만들어 두면 다툴 일이 없다.
