#!/bin/bash
# Run Suite A:  Infrastructure Tests

set -e

echo "========================================"
echo "Running Suite A:  Infrastructure Tests"
echo "========================================"

mvn test -Dtest="SuiteAInfrastructureTest" \
    -Dgroups="infrastructure" \
    -DfailIfNoTests=false \
    -Dsurefire.useFile=false

echo ""
echo "Suite A completed!"