#!/bin/bash
# Paytm Money Take-Home Assignment - One-Command Burst Runner
# Usage: ./burst.sh <BASE_URL>
# Example: ./burst.sh https://paytmbookingsystem.onrender.com

TARGET_URL="${1:-https://paytmbookingsystem.onrender.com}"
TOTAL="${2:-20000}"
CONCURRENCY="${3:-30}"

echo "======================================================================"
echo " PAYTM MONEY LOAD TEST RUNNER"
echo " Target URL   : $TARGET_URL"
echo " Total Bursts : $TOTAL requests"
echo " Concurrency  : $CONCURRENCY parallel workers"
echo "======================================================================"

if ! command -v node &> /dev/null; then
    echo "Error: Node.js is required to run the load test."
    echo "Please install Node.js 18+ and try again."
    exit 1
fi

node burst_test.js "$TARGET_URL" --total "$TOTAL" --concurrency "$CONCURRENCY"
