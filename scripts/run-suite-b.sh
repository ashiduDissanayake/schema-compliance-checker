#!/bin/bash
# Run Suite B: MySQL Functional Tests

set -e

echo "========================================"
echo "Running Suite B: MySQL Functional Tests"
echo "========================================"

# Check Docker is running
if ! docker info > /dev/null 2>&1; then
    echo "ERROR: Docker is not running.  Please start Docker first."
    exit 1
fi

# Pull MySQL image if needed
echo "Ensuring MySQL image is available..."
docker pull mysql:8.0 > /dev/null 2>&1 || true

mvn test -Dtest="SuiteBMySqlFunctionalTest" \
    -Dgroups="mysql-functional" \
    -DfailIfNoTests=false \
    -Dsurefire.useFile=false

echo ""
echo "Suite B completed!"