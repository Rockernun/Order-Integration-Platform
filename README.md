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
- [x] 주문 생성 API 구현
- [x] Idempotency-Key 기반 중복 방지 정책 적용
- [x] 공통적으로 예외 처리를 위해 GlobalExceptionHandler 추가
- [x] README에 멱등 정책 및 주문 API 문서화

<details>
  <summary><b>Idempotency 정책 (Idempotency-Key)</b></summary>
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
  <summary><b>Order API</b></summary>
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
  <summary><b>Outbox 패턴 적용</b></summary>
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
  <summary><b>Outbox 워커 + 전송 처리</b></summary>
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
  <summary><b>Partner Mock + Adapter 구조</b></summary>
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


