#!/usr/bin/env python3
"""Send a fixed number of writes and report completed writes, errors, and latency."""
import argparse
import concurrent.futures
import json
import time
import urllib.error
import urllib.request
from collections import Counter
from uuid import uuid4

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--url', default='http://localhost:8080')
parser.add_argument('--requests', type=int, default=1000)
parser.add_argument('--concurrency', type=int, default=8)
args = parser.parse_args()
if args.requests < 1 or args.concurrency < 1:
    parser.error('requests and concurrency must be positive')
run = str(uuid4())

def write(index):
    body = json.dumps(dict(eventType='LOAD_TEST', actorId='load-' + run,
                           resourceType='TEST', resourceId=str(index),
                           payload=dict(run=run, index=index))).encode()
    request = urllib.request.Request(args.url.rstrip('/') + '/api/v1/audit/events',
                                     data=body, headers={'Content-Type': 'application/json'})
    start = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            response.read()
            status = str(response.status)
    except urllib.error.HTTPError as error:
        status = str(error.code)
        error.close()
    except (OSError, TimeoutError):
        status = 'transport_error'
    return status, (time.perf_counter() - start) * 1000

start = time.perf_counter()
with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
    results = list(pool.map(write, range(args.requests)))
elapsed = time.perf_counter() - start
statuses = Counter(status for status, _ in results)
success = sorted(ms for status, ms in results if status == '201')
def percentile(p):
    return round(success[min(len(success) - 1, int((len(success) - 1) * p))], 2) if success else None
print(json.dumps(dict(run=run, requests=args.requests, concurrency=args.concurrency,
                      seconds=round(elapsed, 2), committed_tps=round(statuses['201'] / elapsed, 2),
                      statuses=dict(statuses), success_latency_ms=dict(p50=percentile(.50),
                      p95=percentile(.95), p99=percentile(.99))), indent=2))
