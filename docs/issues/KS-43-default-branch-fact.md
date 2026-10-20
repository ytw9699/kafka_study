# KS-43: "GitHub 첫 화면은 main" 서술 정정 — 기본 브랜치는 dev

[← 문서 목록](../README.md) · [README](../../README.md)

| | |
|---|---|
| **타입** | DOCS |
| **상태** | DONE |
| **단계** | - (문서 정정) |
| **브랜치** | `docs/KS-43-default-branch-fact` |
| **PR** | - |

## 배경

[KS-42](KS-42-expected-balance-history.md)를 `main` 에 반영할지 정하려고 저장소 설정을 확인했다.

```
gh repo view --json defaultBranchRef  →  기본 브랜치: dev
```

그런데 [브랜치 규칙](../conventions/03-branch-strategy.md)과 [KS-38](KS-38-step7-tag-docs.md)에는
**"GitHub 에서 처음 보이는 것은 `main` 의 README"** 라고 적혀 있었다. 사실과 다르다.
방문자가 처음 보는 README 도, `git clone` 으로 받는 코드도 `dev` 기준이다.

## 작업 내용

- [x] 브랜치 규칙 — 기본 브랜치가 `dev` 라는 사실을 적고, 문서 보강은 `dev` 머지로 바로 보인다고 정정. `main` 반영 예외는 "`main` 을 직접 보는 사람을 위해서"로 이유를 고침
- [x] KS-38 — 판단 기록은 그대로 두고 정정 안내 추가

## 판단

KS-42(화면 수정)는 `dev` 에 이미 있으므로 방문자에게 그대로 보인다. **`main` 에 반영하지 않는다** — 학습 단계가 끝난 것이 아니기 때문이다.

## 배운 것

- **"당연히 그럴 것"이라는 전제가 규칙이 되면 오래 남는다.** 대부분의 저장소는 기본 브랜치가 `main` 이지만, 이 저장소는 처음부터 `dev` 였다.
  설정 한 줄을 확인하지 않고 쓴 문장이 규칙 문서에 들어가 있었다.
