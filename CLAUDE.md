# 이 프로젝트의 작업 규칙

Kafka 학습용 토이 프로젝트. **산출물은 코드가 아니라 이해다.**

## 반드시 지킬 것

이 프로젝트는 **Jira를 쓰지 않는다.** 모든 기획·이슈·결정·트러블슈팅은 `docs/`에 남긴다.

작업 전에 아래 규칙 문서를 읽는다.

- [`docs/conventions/01-doc-structure.md`](docs/conventions/01-doc-structure.md) — 어떤 문서를 어디에 쓰는가
- [`docs/conventions/02-ticket.md`](docs/conventions/02-ticket.md) — `KS-N` 티켓 발급과 양식
- [`docs/conventions/03-branch-strategy.md`](docs/conventions/03-branch-strategy.md) — 브랜치·커밋·PR 규칙

## 요약

### 티켓
- 작업 단위 하나 = `docs/issues/KS-{번호}-{요약}.md` 파일 하나
- 번호는 순차 증가, **재사용 금지**. 다음 번호는 `docs/README.md` 티켓 보드에서 확인
- 새 티켓을 만들면 `docs/README.md` 보드에 한 줄 추가

### 브랜치
```
main ← dev ← {타입}/KS-{번호}-{요약}
```
- `main`, `dev`에 직접 커밋하지 않는다
- feature 브랜치는 `dev`에서 분기해 `dev`로 PR
- `dev` → `main`은 한 학습 단계가 끝났을 때만, 태그(`step-N`)를 붙인다

### 커밋
```
{타입}: [KS-{번호}] {한 줄 요약}

{왜 이렇게 했는지}
```

### PR
- 제목: `[KS-{번호}] {제목}`
- 본문에 **티켓 문서 링크**와 **눈으로 확인하는 검증 절차**를 반드시 넣는다

## 문서를 쓸 때

- **무엇을 골랐는지보다 무엇을 왜 탈락시켰는지**를 남긴다
- 문서는 코드와 **같은 PR**에 넣는다. 나중에 몰아 쓰지 않는다
- 티켓의 **"배운 것" 섹션을 안 쓰면 티켓을 닫지 않는다**
- 한참 붙잡은 문제는 `docs/troubleshooting/`에 문제/원인/해결/배운 것 4단으로 남긴다

## 코드를 쓸 때

- 언어는 **Kotlin**, JDK 17 (Gradle toolchain 고정)
- **JPA 엔티티는 `data class`로 만들지 않는다** — `equals`/`hashCode`가 lazy 프록시와 충돌.
  `data class`는 Kafka 메시지 DTO에만 사용
- `kotlin-spring`(allopen), `kotlin-jpa`(noarg) 플러그인 필수
- H2는 **인메모리 모드**로 둔다. 파일 모드로 두면 "Kafka 재생으로 상태를 재구축한다"는
  이 프로젝트의 핵심 그림이 흐려진다

## 학습 설계상 의도적으로 미룬 것

**3단계에서는 `processed_event` 테이블(멱등성 방어)을 만들지 않는다.**
4단계에서 offset을 리셋했을 때 집계가 2배가 되는 문제를 먼저 겪어야 한다.
미리 방어 코드를 넣으면 학습 기회가 사라진다. 편의를 위해 앞당기지 말 것.
