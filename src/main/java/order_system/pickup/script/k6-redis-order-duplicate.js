import http from "k6/http";
import { check, sleep } from "k6";

export const options = {
    vus: __ENV.VUS ? parseInt(__ENV.VUS) : 100,
    duration: __ENV.DURATION || "20s",
};

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const STORE_ID = __ENV.STORE_ID ? parseInt(__ENV.STORE_ID) : 1;

const IDEMPOTENCY_KEY = __ENV.KEY || "hotkey-001";

export default function () {
    const url = `${BASE_URL}/api/orders`;

    const payload = JSON.stringify({
        storeId: STORE_ID,
        totalPrice: 1000,
    });

    const params = {
        headers: {
            "Content-Type": "application/json",
            "Idempotency-Key": IDEMPOTENCY_KEY,
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        "status is 201 or 200": (r) => r.status === 201 || r.status === 200,
    });
}
