# Order Integration Platform

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
- k6 1.5.0

### Database / Infra
- MySQL 8.0.43 (Docker)
- Redis 7.4-alpine (Docker)
- Docker Compose

---



# 개발 진행 체크리스트

### Store API (JdbcTemplate)
- [X] Store 생성/조회 API 구현
- [x] 요청 검증 및 기본 예외 처리(404 등)
- [x] README에 Store API 문서화

<details>
  <summary><b>📝 Store API</b></summary>
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
- [x] 주문 생성 API 구현
- [x] Idempotency-Key 기반 중복 방지 정책 적용
- [x] 공통적으로 예외 처리를 위해 GlobalExceptionHandler 추가
- [x] README에 멱등 정책 및 주문 API 문서화

<details>
  <summary><b>📝 Idempotency 정책 (Idempotency-Key)</b></summary>
  <div>
    <h3>요청 방식</h3>
    <ul>
      <li>클라이언트는 주문 생성 요청마다 고유한 <code>Idempotency-Key</code> 값을 생성하여 요청 헤더에 포함해야 합니다.</li>
      <li>동일 주문에 대해 재시도 요청을 보낼 때는 <b>반드시 동일한 Idempotency-Key를 재사용</b>해야 합니다.</li>
    </ul>
    <h4>Header</h4>
    <ul>
      <li><code>Idempotency-Key: 멱등키 값</code></li>
    </ul>
    <h3>처리 규칙</h3>
    <ol>
      <li>
        <b>Idempotency-Key가 누락/공백인 경우</b>
        <ul>
          <li><code>400 Bad Request</code></li>
          <li>응답: <code>{ "code": "IDEMPOTENCY_KEY_MISSING", "message": "..." }</code></li>
        </ul>
      </li>
      <li>
        <b>동일 Idempotency-Key로 이미 생성된 주문이 존재하는 경우</b>
        <ul>
          <li>주문을 새로 생성하지 않고 기존 주문을 반환합니다. (중복 생성 방지)</li>
        </ul>
      </li>
      <li>
        <b>동시 요청으로 UNIQUE 충돌이 발생하는 경우</b>
        <ul>
          <li>DB의 <code>UNIQUE(idempotency_key)</code> 제약으로 중복 삽입을 방지합니다.</li>
          <li>충돌 시 기존 주문을 <code>idempotency_key</code>로 재조회하여 기존 주문을 반환합니다.</li>
        </ul>
      </li>
    </ol>
  </div>
</details>

<details>
  <summary><b>📝 Order API</b></summary>
  <div>
    <h3>주문 생성</h3>
    <ul>
      <li><b>Method</b>: <code>POST</code></li>
      <li><b>Path</b>: <code>/api/orders</code></li>
    </ul>
    <b>Request Headers</b>
    <ul>
      <li><code>Content-Type: application/json</code></li>
      <li><code>Idempotency-Key: 멱등키 값</code></li>
    </ul>
    <b>Request Body</b>
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
          <td><code>storeId</code></td>
          <td>number</td>
          <td>O</td>
          <td>매장 ID</td>
        </tr>
        <tr>
          <td><code>totalPrice</code></td>
          <td>number</td>
          <td>O</td>
          <td>총 결제 금액</td>
        </tr>
      </tbody>
    </table>
    <b>Example Request</b>
    <pre><code>curl -i -X POST http://localhost:8080/api/orders \
-H "Content-Type: application/json" \
-H "Idempotency-Key: key-001" \
-d '{"storeId":1,"totalPrice":50000}'</code></pre>
    <b>Success Response</b>
    <ul>
      <li><b>Status</b>: <code>201 Created</code></li>
    </ul>
    <p><b>Body Example</b></p>
    <pre><code>{
"id": 1,
"storeId": 1,
"status": "CREATED",
"totalPrice": 50000,
"createdAt": "2026-01-09T06:00:00Z"
}</code></pre>
    <b>Error Responses</b>
    <h4>400 Bad Request (멱등키 누락)</h4>
    <pre><code>{
"code": "IDEMPOTENCY_KEY_MISSING",
"message": "멱등키 헤더가 필요합니다."
}</code></pre>
    <b>404 Not Found (매장 없음)</b>
    <pre><code>{
"code": "STORE_NOT_FOUND",
"message": "Store 아이디가 1인 Store는 찾을 수 없습니다."
}</code></pre>
    <b>500 Internal Server Error (예상치 못한 서버 오류)</b>
    <pre><code>{
"code": "INTERNAL_SERVER_ERROR",
"message": "Unexpected error"
}</code></pre>
    <hr/>
    <h3>주문 단건 조회</h3>
    <ul>
      <li><b>Method</b>: <code>GET</code></li>
      <li><b>Path</b>: <code>/api/orders/{id}</code></li>
    </ul>
    <b>Example Request</b>
    <pre><code>curl -i http://localhost:8080/api/orders/1</code></pre>
    <b>Success Response</b>
    <ul>
      <li><b>Status</b>: <code>200 OK</code></li>
    </ul>
    <p><b>Body Example</b></p>
    <pre><code>{
"id": 1,
"storeId": 1,
"status": "CREATED",
"totalPrice": 50000,
"createdAt": "2026-01-09T06:00:00Z"
}</code></pre>
    <b>Error Responses</b>
    <h4>404 Not Found (주문 없음)</h4>
    <pre><code>{
"code": "ORDER_NOT_FOUND",
"message": "orderId: 999에 대한 주문 정보를 찾을 수 없습니다."
}</code></pre>
  </div>
</details>


---

### Outbox 패턴 적용
- [x] 주문 생성 트랜잭션에 outbox 이벤트 저장
- [x] 이벤트 스키마/페이로드 형식 정의
- [x] README에 Outbox 패턴 및 흐름 문서화

<details>
  <summary><b>📝 Outbox 패턴 적용</b></summary>
  <div>

<h3>목표</h3>
  <p>
    주문 생성 과정에서 발생하는 후속 작업(파트너 전송, VAN 승인 요청 등)을 <b>트랜잭션 정합성</b>을 유지하면서
    안정적으로 처리하기 위해 <b>Outbox 패턴</b>을 적용했습니다.
  </p>

<h3>왜 Outbox 패턴?</h3>
  <ul>
    <li>
      <b>이벤트 유실 방지</b>: 주문 저장(DB commit)과 이벤트 저장(outbox)을 같은 트랜잭션으로 묶어
      “주문은 저장됐는데 이벤트가 없는” 상황을 방지합니다.
    </li>
    <li>
      <b>외부 연동 장애 격리</b>: 파트너/VAN 등 외부 시스템 장애가 있어도 주문 생성 API를 안정적으로 유지하고,
      후속 작업은 별도 디스패처가 재시도하며 처리합니다.
    </li>
    <li>
      <b>운영 가능성</b>: 이벤트 처리 상태(PENDING/PROCESSED/FAILED)와 재시도 횟수(retry_count)가 DB에 남아
      모니터링 및 복구가 가능합니다.
    </li>
  </ul>

<h3>구현 내용</h3>
  <ul>
    <li><b>주문 생성 트랜잭션에 outbox 이벤트 저장</b>
      <ul>
        <li>주문 생성 성공 시 <code>outbox_events</code> 테이블에 <code>ORDER_CREATED</code> 이벤트를 함께 저장</li>
        <li>주문 INSERT와 outbox INSERT가 동일 트랜잭션에서 커밋/롤백되도록 구성</li>
        <li>멱등키(Idempotency-Key)로 인해 “기존 주문 반환”인 경우 outbox 이벤트를 재적재하지 않도록 처리</li>
      </ul>
    </li>
    <li><b>이벤트 스키마/페이로드 형식 정의</b>
      <ul>
        <li><code>event_type</code>, <code>aggregate_type</code>, <code>aggregate_id</code>, <code>payload(JSON)</code> 기반으로 표준화</li>
        <li>payload는 이벤트별 DTO로 정의하여 스키마를 명확히 유지</li>
      </ul>
    </li>
  </ul>

<h3>Outbox 이벤트 테이블(outbox_events)</h3>
  <ul>
    <li><b>status</b>: <code>PENDING</code> → <code>PROCESSED</code> / <code>FAILED</code></li>
    <li><b>retry_count</b>: 실패 시 재시도 횟수 증가</li>
    <li><b>processed_at</b>: 성공/최종 실패 시점 기록</li>
  </ul>

<h3>흐름 요약</h3>
  <ol>
    <li>클라이언트가 주문 생성 요청(멱등키 포함)</li>
    <li>서버가 주문을 저장</li>
    <li>같은 트랜잭션에서 outbox 이벤트(<code>ORDER_CREATED</code>)를 <code>PENDING</code> 상태로 저장</li>
    <li>커밋 완료 후 outbox 디스패처가 이벤트를 처리(전송/후속 작업)</li>
  </ol>

  </div>
</details>

---

### Outbox 워커 + 전송 처리
- [x] outbox 이벤트 폴링 워커 구현
- [x] 전송 성공/실패 처리 및 재시도 기본 정책 적용
- [x] README에 outbox 상태 전이/워커 동작 문서화


<details>
  <summary><b>📝 Outbox 워커 + 전송 처리</b></summary>
  <div>

<h3>목표</h3>
  <p>
    Outbox 테이블에 쌓인 <code>PENDING</code> 이벤트를 <b>Dispatcher</b>가 폴링하여 처리하고,
    결과를 <code>PROCESSED</code> / <code>FAILED</code>로 기록해 결국 처리되도록 합니다.
  </p>

<h3>구현 내용</h3>
  <ul>
    <li><b>outbox 이벤트 폴링 워커 구현</b>
      <ul>
        <li>일정 주기마다 <code>status='PENDING'</code> 이벤트를 배치로 조회(<code>findPending(limit)</code>)</li>
        <li>이벤트 타입 기반으로 처리 로직 분기(현재는 mock 처리, 추후 파트너/VAN 호출로 확장할 예정)</li>
      </ul>
    </li>
    <li><b>전송 성공/실패 처리 및 재시도 기본 정책 적용</b>
      <ul>
        <li>성공 시: <code>status='PROCESSED'</code>, <code>processed_at=NOW()</code> 업데이트</li>
        <li>실패 시: <code>retry_count</code> 증가</li>
        <li>최대 재시도 횟수 초과 시: <code>status='FAILED'</code>로 전환(자동 처리 대상에서 제외)</li>
      </ul>
    </li>
  </ul>

<h3>이벤트 상태 변경</h3>
  <ul>
    <li><code>PENDING</code> → <code>PROCESSED</code> : 처리 성공</li>
    <li><code>PENDING</code> → <code>PENDING</code> : 처리 실패(재시도 횟수 증가 후 재시도 대기)</li>
    <li><code>PENDING</code> → <code>FAILED</code> : 재시도 한계 초과(수동 조치 필요)</li>
  </ul>

<h3>디스패처 동작</h3>
  <ol>
    <li><code>PENDING</code> 이벤트를 일정 개수(batch) 조회</li>
    <li>이벤트별 처리 수행(전송/후속 작업)</li>
    <li>성공 시 <code>markProcessed(id)</code>로 상태 갱신</li>
    <li>실패 시 <code>retry_count</code> 증가, 한계 초과 시 <code>FAILED</code> 전환</li>
  </ol>

  </div>
</details>

---

### Partner Mock + Adapter 구조
- [x] 임시로 Partner Mock 구현
- [x] 파트너별 어댑터/클라이언트 구조 도입
- [x] README에 파트너 계약(Contract) 및 Adapter 구조 문서화

<details>
  <summary><b>📝 Partner Mock + Adapter 구조</b></summary>
  <div>
    <h3>목표</h3>
    <ul>
      <li>OutboxDispatcher가 <code>ORDER_CREATED</code> 이벤트를 처리할 때, 파트너 시스템으로 주문을 전송하는 흐름을 검증합니다.</li>
      <li>실제 외부 파트너 API 대신, 프로젝트 내부에 <b>Partner Mock 엔드포인트</b>를 두어 안정적으로 통합 테스트가 가능하도록 했습니다.</li>
      <li>파트너별 계약이 달라질 수 있으므로, <b>Adapter(변환)</b> + <b>Client(전송)</b>로 책임을 분리했습니다.</li>
    </ul>
    <hr/>
    <h3>임시 Partner Mock</h3>
    <p>
      실제 외부 파트너 서버 대신, 로컬에서 동작하는 Mock 엔드포인트로 주문 전송을 검증합니다.
      OutboxDispatcher는 PartnerClient를 통해 Mock URL로 주문을 전송합니다.
    </p>
    <h4>PartnerA Mock</h4>
    <ul>
      <li><b>Method</b>: <code>POST</code></li>
      <li><b>Path</b>: <code>/mock/partner-a/orders</code></li>
      <li><b>Description</b>: PartnerA가 주문을 수신하는 상황을 로컬에서 Mock합니다.</li>
    </ul>
    <h4>요청 예시</h4>
    <pre><code class="language-bash">curl -i -X POST http://localhost:8080/mock/partner-a/orders \
-H "Content-Type: application/json" \
-d '{"externalOrderId": 10, "partnerStoreId":"A-101", "amount": 15000}'</code></pre>
    <hr/>
    <h3>Adapter / Client 책임 분리</h3>
    <h4>분리한 이유</h4>
    <ul>
      <li><b>Partner Contract 변화 대응</b>: 파트너별 요청 필드/형식이 바뀌어도 Adapter만 수정하면 됩니다.</li>
      <li><b>전송 로직 격리</b>: HTTP 호출/에러 처리/타임아웃 등은 Client가 책임집니다.</li>
      <li><b>Outbox 로직 단순화</b>: Dispatcher는 이벤트 처리와 라우팅에 집중하고, 변환/전송은 하위 컴포넌트에 위임합니다.</li>
    </ul>
    <h4>구성 요소</h4>
    <ul>
      <li><b>Adapter</b>: Canonical(Order/Store) → Partner Contract DTO로 변환</li>
      <li><b>Client</b>: Partner Contract DTO를 실제 전송(HTTP)</li>
      <li><b>Dispatcher</b>: Outbox 이벤트 폴링 후, partner에 맞는 Adapter/Client로 라우팅</li>
    </ul>
    <hr/>
    <h3>PartnerA Contract(요청 스키마)</h3>
    <h4>PartnerAOrderRequest</h4>
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
          <td><code>externalOrderId</code></td>
          <td>number</td>
          <td>O</td>
          <td>내부 시스템의 주문 ID(외부 전송용)</td>
        </tr>
        <tr>
          <td><code>partnerStoreId</code></td>
          <td>string</td>
          <td>O</td>
          <td>파트너 시스템의 매장 식별자(<code>stores.partner_store_id</code>)</td>
        </tr>
        <tr>
          <td><code>amount</code></td>
          <td>number</td>
          <td>O</td>
          <td>주문 금액(예: <code>orders.total_price</code>)</td>
        </tr>
      </tbody>
    </table>
    <hr/>
    <h3>처리 흐름(Outbox → Partner 전송)</h3>
    <ol>
      <li>주문 생성 트랜잭션에서 <code>outbox_events</code>에 <code>ORDER_CREATED</code> 이벤트 저장</li>
      <li>OutboxDispatcher가 <code>PENDING</code> 이벤트 폴링</li>
      <li>이벤트의 <code>aggregate_id</code>(orderId)로 주문 조회</li>
      <li>주문의 <code>store_id</code>로 매장 조회</li>
      <li>매장의 <code>partner</code> 값에 따라 Adapter/Client 선택</li>
      <li>Adapter가 PartnerA 요청 DTO로 변환</li>
      <li>Client가 Partner Mock(또는 실제 파트너 API)로 HTTP 전송</li>
      <li>성공 시 outbox <code>PROCESSED</code>, 실패 시 재시도 후 <code>FAILED</code></li>
    </ol>
  </div>
</details>


---

### Webhook 수신 + 중복 방지
- [x] 파트너 webhook 수신 엔드포인트 구현
- [x] 토큰 검증 방식 추가
- [x] webhook 중복 처리 방지 적용
- [x] README에 webhook 처리/검증/중복 방지 문서화
<details>
  <summary><b>📝 Webhook 수신 + 중복 방지</b></summary>
  <div>
    <h3>목표</h3>
    <ul>
      <li>파트너(PartnerA)가 주문 처리 결과(상태 변경)를 우리 시스템에 알리기 위해 Webhook을 호출합니다.</li>
      <li>Webhook 수신 시 <code>orders.status</code>를 업데이트하여 파트너와 주문 상태를 동기화합니다.</li>
      <li>같은 Webhook 이벤트가 여러 번 전달되어도(재전송/중복) 한 번만 처리하도록 중복 방지를 적용합니다.</li>
      <li>간단한 토큰 검증으로 파트너 인증을 수행합니다.</li>
    </ul>
    <hr/>
    <h3>1) PartnerA Webhook 엔드포인트</h3>
    <ul>
      <li><b>Method</b>: <code>POST</code></li>
      <li><b>Path</b>: <code>/webhooks/partner-a/orders</code></li>
    </ul>
    <h4>Request Headers</h4>
    <table>
      <thead>
        <tr>
          <th>Header</th>
          <th>Required</th>
          <th>Description</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><code>X-Event-Id</code></td>
          <td>O</td>
          <td>Webhook 이벤트의 고유 ID (중복 방지 키)</td>
        </tr>
        <tr>
          <td><code>X-Partner-Token</code></td>
          <td>O</td>
          <td>파트너 인증 토큰 (간단 검증 방식)</td>
        </tr>
      </tbody>
    </table>
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
          <td><code>orderId</code></td>
          <td>number</td>
          <td>O</td>
          <td>우리 시스템 주문 ID (<code>orders.id</code>)</td>
        </tr>
        <tr>
          <td><code>status</code></td>
          <td>string</td>
          <td>O</td>
          <td>파트너가 통지하는 주문 상태 값</td>
        </tr>
      </tbody>
    </table>
    <h4>Example Request</h4>
    <pre><code class="language-bash">curl -i -X POST http://localhost:8080/webhooks/partner-a/orders \
-H "Content-Type: application/json" \
-H "X-Event-Id: evt-100" \
-H "X-Partner-Token: partner-a-secret" \
-d '{"orderId": 1, "status": "ACCEPTED"}'</code></pre>
    <hr/>
    <h3>2) 토큰 검증 방식</h3>
    <p>
      Webhook 엔드포인트는 외부에서 호출 가능하므로, 무분별한 호출로 주문 상태가 변경되는 것을 막기 위해 임시로
      <code>X-Partner-Token</code> 기반 검증을 적용했습니다.
    </p>
    <ul>
      <li>요청의 <code>X-Partner-Token</code> 값이 서버 설정의 토큰과 일치하지 않으면 <code>401 Unauthorized</code>를 반환합니다.</li>
      <li>토큰은 로컬에서 <code>.env</code>에 환경변수로 관리할 수 있도록 구성했습니다.</li>
    </ul>
    <h4>환경변수 예시</h4>
    <pre><code class="language-bash"># .env
PARTNER_A_WEBHOOK_TOKEN=partner-a-secret</code></pre>
    <hr/>
    <h3>3) 중복 방지 정책</h3>
    <p>
      파트너 시스템은 네트워크 장애/타임아웃 발생 시 동일 이벤트를 재전송할 수 있습니다.
      따라서 <code>X-Event-Id</code>를 중복 방지 키로 사용하고, DB 레벨에서 <b>UNIQUE 제약</b>으로 중복 처리를 방지합니다.
    </p>
    <h4>중복 방지를 위한 저장 테이블: webhook_events</h4>
    <ul>
      <li><code>webhook_events.event_id</code>에 <b>UNIQUE 제약</b> 적용</li>
      <li>새로운 <code>X-Event-Id</code>는 INSERT 성공 → 최초 이벤트로 처리</li>
      <li>이미 존재하는 <code>X-Event-Id</code>는 INSERT 실패(Duplicate) → 중복 이벤트로 판단하고 종료</li>
    </ul>
    <hr/>
    <h3>4) 처리 흐름</h3>
    <ol>
      <li>PartnerA가 Webhook 호출: <code>POST /webhooks/partner-a/orders</code></li>
      <li>Controller에서 <code>X-Partner-Token</code> 검증 (불일치 시 401)</li>
      <li>Service에서 <code>X-Event-Id</code>를 <code>webhook_events</code>에 INSERT 시도</li>
      <li>INSERT 성공: 최초 이벤트 → 주문 상태 업데이트 수행</li>
      <li>INSERT 실패(중복): 중복 이벤트 → 상태 업데이트 생략 후 종료</li>
      <li>존재하지 않는 주문이면 404 반환</li>
    </ol>
    <hr/>
    <h3>5) 응답 코드</h3>
    <table>
      <thead>
        <tr>
          <th>Status</th>
          <th>When</th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td><code>200 OK</code></td>
          <td>정상 처리 또는 중복 이벤트 처리</td>
        </tr>
        <tr>
          <td><code>401 Unauthorized</code></td>
          <td>토큰 검증 실패</td>
        </tr>
        <tr>
          <td><code>404 Not Found</code></td>
          <td>존재하지 않는 주문 ID</td>
        </tr>
      </tbody>
    </table>
  </div>
</details>


---

### 재시도/백오프/DLQ
- [x] 재시도 정책 고도화(백오프/최대 횟수)
- [x] DLQ(실패 적재) 설계/적용
- [x] 운영용 조회/재처리 API 추가

<details>
  <summary><b>📝 재시도/백오프/DLQ + 운영 기능</b></summary>
  <div>
    <h3>개요</h3>
    <p>
      Outbox 패턴은 이벤트를 DB(outbox_events)에 저장한 뒤, 별도의 워커(OutboxDispatcher)가 주기적으로 폴링하여
      외부 시스템(파트너)으로 전송합니다. 이 과정에서 네트워크 장애, 파트너 응답 실패 등이 발생할 수 있으므로
      <b>재시도 정책</b>, <b>백오프</b>, <b>DLQ(실패 적재)</b>, <b>운영용 조회/재처리</b> 기능이 필요합니다.
    </p>
    <hr />
    <h3>1) 재시도 정책 고도화 (백오프 + 최대 횟수)</h3>
    <ul>
      <li>
        OutboxDispatcher는 <code>PENDING</code> 상태의 이벤트를 주기적으로 조회하여 처리합니다.
      </li>
      <li>
        전송 실패 시 <code>retry_count</code>를 증가시키고, 재시도 스케줄을 위해 <code>next_run_at</code>을 설정합니다.
      </li>
      <li>
        최대 재시도 횟수(<code>MAX_RETRY_COUNT</code>)를 초과하면 이벤트는 <code>FAILED</code>로 전환되어 더 이상 자동 재시도되지 않습니다.
      </li>
    </ul>
    <h4>상태/필드</h4>
    <ul>
      <li><code>status</code>: <b>PENDING</b> / <b>PROCESSED</b> / <b>FAILED</b></li>
      <li><code>retry_count</code>: 재시도 횟수</li>
      <li><code>next_run_at</code>: 다음 재시도 가능 시각 (백오프 적용)</li>
      <li><code>last_error</code>: 최근 실패 원인(예외 메시지)</li>
    </ul>
    <h4>백오프 예시 정책</h4>
    <p>재시도 횟수에 따라 다음 실행 지연 시간이 증가합니다.</p>
    <ul>
      <li>0회 → 5초</li>
      <li>1회 → 15초</li>
      <li>2회 → 30초</li>
      <li>3회 → 60초</li>
      <li>4회 이상 → 120초</li>
    </ul>
    <hr />
    <h3>2) DLQ(실패 적재) 설계/적용</h3>
    <p>
      본 프로젝트에서는 별도의 DLQ 테이블/큐를 두지 않고,
      <b>outbox_events의 status를 FAILED로 유지</b>하는 방식으로 DLQ를 구성합니다.
    </p>
    <ul>
      <li>
        <b>FAILED</b> 이벤트는 자동 재시도 대상에서 제외됩니다.
      </li>
      <li>
        운영자는 Ops API를 통해 FAILED 이벤트를 확인하고 원인을 파악할 수 있습니다.
      </li>
      <li>
        필요 시 강제로 <code>PENDING</code>으로 되돌려 재처리할 수 있습니다.
      </li>
    </ul>
    <hr />
    <h3>3) 운영용 조회/재처리 API</h3>
    <p>
      운영자는 Outbox 이벤트를 조회하고, 실패한 이벤트를 수동으로 재처리할 수 있습니다.
      (관리자용 API로 가정)
    </p>
    <h4>✅ Outbox 이벤트 조회</h4>
    <ul>
      <li><b>Method</b>: <code>GET</code></li>
      <li><b>Path</b>: <code>/ops/outbox</code></li>
      <li><b>Query</b>:
        <ul>
          <li><code>status</code> (default: FAILED)</li>
          <li><code>limit</code> (default: 50, max: 200)</li>
        </ul>
      </li>
    </ul>
    <pre><code class="language-bash">curl -i "http://localhost:8080/ops/outbox?status=FAILED&amp;limit=50"</code></pre>
    <h4>✅ Outbox 이벤트 강제 재처리</h4>
    <ul>
      <li><b>Method</b>: <code>POST</code></li>
      <li><b>Path</b>: <code>/ops/outbox/{id}/retry</code></li>
      <li><b>Description</b>: 특정 outbox 이벤트를 <code>PENDING</code>으로 되돌려 즉시 재처리 대상으로 등록합니다.</li>
    </ul>
    <pre><code class="language-bash">curl -i -X POST "http://localhost:8080/ops/outbox/10/retry"</code></pre>
    <h4>재처리 동작</h4>
    <ul>
      <li><code>status</code> → <b>PENDING</b></li>
      <li><code>next_run_at</code> → <b>NOW()</b></li>
      <li><code>processed_at</code> → <b>NULL</b></li>
    </ul>
    <hr />
  </div>
</details>

---

### DB Unique 기반 vs Redis 기반 멱등 처리 비교 결론 (k6 측정 기반)

주문 생성 API의 **Idempotency-Key(중복 방지)** 구현을 다음 두 방식으로 구현하고, `k6` 부하 테스트로 성능 차이를 직접 측정했습니다.
Redis 멱등 처리 구현 및 성능 측정은 **`feature/idempotency-redis` 브랜치**에서 진행했습니다.



### 1) 비교 대상

#### ✅ a. DB Unique 기반 멱등 처리 (기존 방식)
- `orders.idempotency_key` 컬럼에 **UNIQUE 제약**을 부여
- 중복 요청 시 `DuplicateKeyException` 발생 → 기존 주문을 조회하여 동일 응답 반환

**장점**
- DB가 원자적으로 중복을 막으므로 **정확성과 일관성이 매우 높음**
- 인프라 단순

**단점**
- 중복 요청이 많아질수록 DB insert 충돌 및 조회로 DB 부하가 증가할 수 있음

&nbsp;

#### ✅ B. Redis 기반 멱등 처리 (실험/확장 방식)
- Redis에 멱등키를 `SETNX(setIfAbsent)`로 **선점(IN_PROGRESS 락)**
- 처리 완료 시 `DONE:<responseJson>` 형태로 결과 저장
- 중복 요청은 Redis에서 빠르게 차단 및 동일 응답 제공 가능

**장점**
- 중복 요청 폭주 상황에서 DB로 유입되는 트래픽을 줄일 수 있음
- 분산환경에서 멱등 처리 확장에 유리

**단점**
- Redis 장애/TTL/상태 불일치 관리 등으로 운영 복잡도가 증가
- 정상 처리 흐름은 결국 DB insert가 필요하여 체감 성능 개선 폭이 제한적일 수 있음

---

### 2) k6 성능 측정 결과 요약

#### ✅ 정상 주문(고유 Idempotency-Key 요청)
- **DB 기반과 Redis 기반의 성능 차이는 거의 없었음**
- 정상 주문은 결국 DB INSERT가 발생하므로 병목은 DB 트랜잭션 비용 영향이 큼



#### ✅ 중복 주문(동일 Idempotency-Key 반복 요청)
중복 요청 테스트에서는 일부 조건에서 Redis 방식이 더 좋아 보이기도 했지만,  
테스트 스크립트의 `sleep(0.1)` 존재 여부에 따라 TPS가 크게 달라지는 문제가 있었고,  
최종적으로 동일한 조건(100 VUs / 20s / sleep 제거)으로 맞춘 결과는 아래와 같았습니다.

- **DB 기반 중복 요청**
    - avg: **18.38ms**
    - p(90): **20.23ms**
    - p(95): **33.71ms**

- **Redis 기반 중복 요청**
    - avg: **18.29ms**
    - p(90): **19.9ms**
    - p(95): **33.52ms**

결론적으로 **중복 요청에서도 Redis 도입에 따른 유의미한 성능 차이는 거의 없었음**  
(로컬 환경 / 단일 서버 / 단일 DB 구성에서는 병목이 네트워크/서버 처리 비용에 더 크게 좌우됨)

---

### 3) 어떤 방식으로 유지?

현재 프로젝트 구성(단일 인스턴스 + 단일 DB + 로컬 규모)에서는  
DB Unique 기반 멱등 처리만으로도 안정성과 단순성을 충분히 확보할 수 있어 기본 방식으로 유지하는 것이 합리적인 것으로 보인다. 다만, Redis 방식은 다음 상황에서 효과가 커질 수 있으므로 확장 옵션으로 유지하는 것으로 결정했다.

✅ Redis 방식이 의미 있는 경우
- 중복 요청이 폭증하는 트래픽 패턴(모바일 재시도, 네트워크 불안정)
- 애플리케이션 인스턴스가 여러 대인 분산 환경
- DB 부하를 줄이기 위해 중복 처리 응답을 Redis에서 캐싱하고 빠르게 반환해야 하는 경우

---

### 성능 개선 요약 (Outbox 폴링 최적화)

### 1) 문제
Outbox Dispatcher의 폴링 쿼리(findAndLockPending)는 아래 조건으로 지금 처리 가능한 이벤트를 **id 오름차순으로 50개** 가져오고 있다.

- `status='PENDING'`
- `next_run_at IS NULL OR next_run_at <= NOW()`
- `ORDER BY id LIMIT 50`

인덱스가 없으면 이러한 이벤트가 테이블 뒤쪽(id가 큰 쪽)에 몰려 있는 상황에서  
50개를 찾기 위해 PK(=PRIMARY) 스캔으로 수십만 ~ 수백만 행을 스캔하는 일이 발생한다.

### 2) 해결
폴링 패턴에 맞춘 복합 인덱스를 추가해, 조건 필터링 + 정렬/limit을 인덱스에서 처리하도록 유도했다. 측정 결과 인덱스 도입 전의 걸린 시간 평균 340ms에서 인덱스 도입 후 0.09ms로 대폭 줄일 수 있었다.

```sql
CREATE INDEX idx_outbox_pending_pick
ON outbox_events (status, next_run_at, id);