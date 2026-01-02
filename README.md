# Pickup Order Integration Platform

외부 파트너(브랜드/POS/주문 시스템)와 안정적으로 주문을 연동하기 위한 **주문 연동 플랫폼**입니다. 고객 주문을 표준(Canonical) 주문 모델로 저장한 뒤, 파트너별 어댑터를 통해 주문을 전송하고 파트너의 상태 업데이트(Webhook)를 받아 주문 상태를 동기화합니다.
이 프로젝트는 단순한 주문 앱이 아니라, 연동/운영 관점에서 안정성을 갖춘 주문 연동 시스템을 구현하는 것입니다.

---

## 핵심 기능

- 표준 주문 모델(Canonical Model) 기반 주문 저장
- 파트너 시스템으로 주문 전송(파트너별 변환/전송 어댑터)
- 파트너 Webhook으로 상태 동기화
- 멱등성(Idempotency) / 중복 방지
- Outbox 패턴 기반 이벤트 발행(DB 트랜잭션 정합성)
- 실패 시 재시도 / 백오프 / DLQ(실패 적재)
- 운영을 위한 로그/모니터링(추후 예정)

---

## 기술 스택 및 버전

### Backend
- Java 17.0.12
- Spring Boot 3.5.9
- Spring Web (REST API)
- Spring JDBC
- Flyway 11.7.2

### Database / Infra
- MySQL **8.0.43** (Docker)
- Docker Compose

---



# 개발 진행 체크리스트

### Store API (JdbcTemplate)
- [X] Store 생성/조회 API 구현
- [x] 요청 검증 및 기본 예외 처리(404 등)
- [x] README에 Store API 문서화

<details>
  <summary><b>Store API</b></summary>
  <div>
    <h3>매장 생성</h3>
    <ul>
      <li><b>Method</b>: <code>POST</code></li>
      <li><b>Path</b>: <code>/api/stores</code></li>
      <li><b>Description</b>: 매장을 생성합니다.</li>
    </ul>
    <h4>Request Headers</h4>
    <ul>
      <li><code>Content-Type: application/json</code></li>
    </ul>
    <h4>Request Body</h4>
    <table>
      <thead>
        <tr>
          <th>Field</th>
          <th>Type</th>
          <th>Required</th>
          <th>Description</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><code>name</code></td>
          <td>string</td>
          <td>O</td>
          <td>매장명 (공백/빈 문자열 불가)</td>
        </tr>
        <tr>
          <td><code>partner</code></td>
          <td>string</td>
          <td>X</td>
          <td>연동 파트너명(예: PartnerA)</td>
        </tr>
        <tr>
          <td><code>partnerStoreId</code></td>
          <td>string</td>
          <td>X</td>
          <td>파트너 시스템의 매장 식별자</td>
        </tr>
      </tbody>
    </table>
    <h4>Example Request</h4>
    <pre><code>curl -i -X POST http://localhost:8080/api/stores \
-H "Content-Type: application/json" \
-d '{"name":"홍대점","partner":"PartnerA","partnerStoreId":"A-101"}'</code></pre>

  </div>
</details>


---

### Order 생성 + 멱등키(Idempotency-Key)
- [ ] 주문 생성 API 구현
- [ ] Idempotency-Key 기반 중복 방지 정책 적용
- [ ] 주문 조회(필요 시) 및 상태 모델(초기) 정리
- [ ] README에 멱등 정책 및 주문 API 문서화

---

### Outbox 패턴 적용
- [ ] 주문 생성 트랜잭션에 outbox 이벤트 저장
- [ ] 이벤트 스키마/페이로드 형식 정의
- [ ] README에 Outbox 패턴 및 흐름 문서화

---

### Outbox 워커 + 전송 처리
- [ ] outbox 이벤트 폴링 워커 구현
- [ ] 전송 성공/실패 처리 및 재시도 기본 정책 적용
- [ ] README에 outbox 상태 전이/워커 동작 문서화

---

### Partner Mock + Adapter 구조
- [ ] Partner Mock 서버(또는 엔드포인트) 구현
- [ ] 파트너별 어댑터/클라이언트 구조 도입(변환/전송)
- [ ] README에 파트너 계약(Contract) 및 Adapter 구조 문서화

---

### Webhook 수신 + 중복 방지
- [ ] 파트너 webhook 수신 엔드포인트 구현
- [ ] (선택) 간단한 검증(토큰/HMAC 등) 추가
- [ ] webhook 중복 처리 방지(dedup) 적용
- [ ] README에 webhook 처리/검증/중복 방지 문서화

---

### 재시도/백오프/DLQ + 운영 기능
- [ ] 재시도 정책 고도화(백오프/최대 횟수)
- [ ] DLQ(실패 적재) 설계/적용
- [ ] 운영용 조회/재처리 API(또는 관리 방식) 추가
- [ ] 로그/추적(correlation) 기반 운영 가이드 작성


