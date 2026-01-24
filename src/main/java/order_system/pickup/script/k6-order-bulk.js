import http from "k6/http";
import { check } from "k6";

export const options = {
    vus: __ENV.VUS ? parseInt(__ENV.VUS) : 300,
    duration: __ENV.DURATION || "20s",
};

const BASE_URL = __ENV.BASE_URL || "http://127.0.0.1:8080";
const STORE_ID = __ENV.STORE_ID ? parseInt(__ENV.STORE_ID) : 1;
const BULK_SIZE = __ENV.BULK_SIZE ? parseInt(__ENV.BULK_SIZE) : 10;
const KEY_PREFIX = __ENV.KEY_PREFIX || "bulk-key";

export default function () {
    const url = `${BASE_URL}/api/orders`;

    for (let i = 0; i < BULK_SIZE; i++) {
        const key = `${KEY_PREFIX}-${__VU}-${__ITER}-${i}`;

        const payload = JSON.stringify({
            storeId: STORE_ID,
            totalPrice: 1000,
        });

        const params = {
            headers: {
                "Content-Type": "application/json",
                "Idempotency-Key": key,
            },
        };

        const res = http.post(url, payload, params);

        check(res, {
            "status is 201 or 200": (r) => r.status === 201 || r.status === 200,
        });
    }
}
