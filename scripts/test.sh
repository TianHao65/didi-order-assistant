#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
task_test_dir="$(mktemp -d)"
trap 'rm -rf "$task_test_dir"' EXIT
javac -encoding UTF-8 -d "$task_test_dir" "$task_root/app/src/main/java/local/ridewatch/OrderEngine.java" "$task_root/tests/OrderEngineTest.java"
java -cp "$task_test_dir" local.ridewatch.OrderEngineTest
