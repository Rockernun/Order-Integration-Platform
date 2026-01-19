import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    vus: 100,
    duration: '20s',
};

export default function () {
    const url = 'http://localhost:8080/api/orders';

    const idempotencyKey = `key-${__VU}-${__ITER}-${Date.now()}`;

    const payload = JSON.stringify({
        storeId: 2,
        totalPrice: 1000
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotencyKey,
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        'status is 201 or 200': (r) => r.status === 201 || r.status === 200,
    });

    sleep(0.1);
}

/**
 *      execution: local
 *         script: k6-order-normal.js
 *         output: -
 *
 *      scenarios: (100.00%) 1 scenario, 100 max VUs, 50s max duration (incl. graceful stop):
 *               * default: 100 looping VUs for 20s (gracefulStop: 30s)
 *
 *
 *
 *   █ TOTAL RESULTS
 *
 *     checks_total.......: 19000   945.186778/s
 *     checks_succeeded...: 100.00% 19000 out of 19000
 *     checks_failed......: 0.00%   0 out of 19000
 *
 *     ✓ status is 201 or 200
 *
 *     HTTP
 *     http_req_duration..............: avg=5.45ms   min=1.53ms   med=5.06ms   max=73.29ms  p(90)=6.93ms   p(95)=8.02ms
 *       { expected_response:true }...: avg=5.45ms   min=1.53ms   med=5.06ms   max=73.29ms  p(90)=6.93ms   p(95)=8.02ms
 *     http_req_failed................: 0.00%  0 out of 19000
 *     http_reqs......................: 19000  945.186778/s
 *
 *     EXECUTION
 *     iteration_duration.............: avg=105.59ms min=101.63ms med=105.18ms max=175.84ms p(90)=107.05ms p(95)=108.16ms
 *     iterations.....................: 19000  945.186778/s
 *     vus............................: 100    min=100        max=100
 *     vus_max........................: 100    min=100        max=100
 *
 *     NETWORK
 *     data_received..................: 4.2 MB 209 kB/s
 *     data_sent......................: 3.9 MB 195 kB/s
 */
