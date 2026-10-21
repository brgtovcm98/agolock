#!/usr/bin/env python3
"""image.process.dlq 메시지를 조회해 agolock.events로 재발행합니다.

RabbitMQ Management HTTP API만 사용합니다. 기본값은 dry-run입니다.
환경 변수:
  RABBITMQ_MANAGEMENT_URL (기본 http://localhost:15672)
  RABBITMQ_USER (기본 agolock)
  RABBITMQ_PASSWORD (기본 agolock-local-secret)
"""
from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import urllib.error
import urllib.request

QUEUE = "image.process.dlq"
EXCHANGE = "agolock.events"
ROUTING_KEY = "image.uploaded"


def request(method: str, url: str, body: dict | None = None) -> tuple[int, bytes]:
    credentials = base64.b64encode(
        f"{os.environ.get('RABBITMQ_USER', 'agolock')}:{os.environ.get('RABBITMQ_PASSWORD', 'agolock-local-secret')}".encode()
    ).decode()
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Authorization": f"Basic {credentials}",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()
    except urllib.error.URLError as error:
        return 0, str(error).encode()


def main() -> int:
    parser = argparse.ArgumentParser(description="image.process.dlq replay")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--dry-run", action="store_true", help="메시지를 소비하지 않고 건수만 확인")
    args = parser.parse_args()
    base = os.environ.get("RABBITMQ_MANAGEMENT_URL", "http://localhost:15672").rstrip("/")

    status, body = request("GET", f"{base}/api/queues/%2F/{QUEUE}")
    if status != 200:
        print(f"DLQ 조회 실패: HTTP {status} {body[:200]!r}", file=sys.stderr)
        return 1
    info = json.loads(body)
    count = min(info.get("messages", 0), args.limit)
    print(f"{QUEUE} messages={info.get('messages', 0)} replay={count} dry_run={args.dry_run}")
    if args.dry_run or count == 0:
        return 0

    status, body = request(
        "POST",
        f"{base}/api/queues/%2F/{QUEUE}/get",
        {"count": count, "ackmode": "ack_requeue_false", "encoding": "auto"},
    )
    if status != 200:
        print(f"DLQ consume 실패: HTTP {status} {body[:200]!r}", file=sys.stderr)
        return 1
    messages = json.loads(body)
    for message in messages:
        payload = message.get("payload", "")
        status, result = request(
            "POST",
            f"{base}/api/exchanges/%2F/{EXCHANGE}/publish",
            {
                "properties": {"delivery_mode": 2, "content_type": "application/json"},
                "routing_key": ROUTING_KEY,
                "payload": payload,
                "payload_encoding": "string",
            },
        )
        if status != 200 or not json.loads(result).get("routed", False):
            print(f"재발행 실패: HTTP {status} {result[:200]!r}", file=sys.stderr)
            return 1
    print(f"재발행 완료: {len(messages)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
