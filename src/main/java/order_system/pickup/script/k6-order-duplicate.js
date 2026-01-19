import http from 'k6/http';
import { check } from 'k6';

export const options = {
    vus: 100,
    duration: '20s',
};

const url = 'http://127.0.0.1:8080/api/orders';
const fixedKey = 'fixed-key-001';

export default function () {
    const payload = JSON.stringify({
        storeId: 2,
        totalPrice: 1000
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': fixedKey,
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
    });
}

/**
 *      execution: local
 *         script: ./k6-order-duplicate.js
 *         output: -
 *
 *      scenarios: (100.00%) 1 scenario, 100 max VUs, 50s max duration (incl. graceful stop):
 *               * default: 100 looping VUs for 20s (gracefulStop: 30s)
 *
 *
 *
 *   █ TOTAL RESULTS
 *
 *     checks_total.......: 120563 6023.796301/s
 *     checks_succeeded...: 99.99% 120554 out of 120563
 *     checks_failed......: 0.00%  9 out of 120563
 *
 *     ✗ status is 201 or 200
 *       ↳  99% — ✓ 120554 / ✗ 9
 *
 *     HTTP
 *     http_req_duration..............: avg=16.54ms min=1.14ms med=15.97ms max=181.95ms p(90)=22.34ms p(95)=30.31ms
 *       { expected_response:true }...: avg=16.53ms min=1.14ms med=15.97ms max=181.95ms p(90)=22.33ms p(95)=30.3ms
 *     http_req_failed................: 0.00%  9 out of 120563
 *     http_reqs......................: 120563 6023.796301/s
 *
 *     EXECUTION
 *     iteration_duration.............: avg=16.58ms min=1.16ms med=16.01ms max=183.91ms p(90)=22.4ms  p(95)=30.35ms
 *     iterations.....................: 120563 6023.796301/s
 *     vus............................: 100    min=100         max=100
 *     vus_max........................: 100    min=100         max=100
 *
 *     NETWORK
 *     data_received..................: 27 MB  1.3 MB/s
 *     data_sent......................: 24 MB  1.2 MB/s
 */
