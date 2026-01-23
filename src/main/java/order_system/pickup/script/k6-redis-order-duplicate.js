import http from "k6/http";
import { check } from "k6";

export const options = {
    vus: 100,
    duration: "20s",
};

const BASE_URL = "http://127.0.0.1:8080";
const url = `${BASE_URL}/api/orders`;

const IDEMPOTENCY_KEY = "fixed-key-duplicate-001";

export default function () {
    const payload = JSON.stringify({
        storeId: 2,
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
