#!/bin/bash
# Run all test suites

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_DIR"

echo "========================================"
echo "Schema Compliance Checker - Full Test Run"
echo "========================================"
echo ""

# Check prerequisites
echo "Checking prerequisites..."

# Check Java
if ! command -v java &> /dev/null; then
    echo "ERROR: Java is not installed"
    exit 1
fi
java_version=$(java -version 2>&1 | head -n 1)
echo "  Java:  $java_version"

# Check Docker
if ! docker info > /dev/null 2>&1; then
    echo "ERROR: Docker is not running"
    exit 1
fi
docker_version=$(docker --version)
echo "  Docker: $docker_version"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven is not installed"
    exit 1
fi
mvn_version=$(mvn --version | head -n 1)
echo "  Maven: $mvn_version"

echo ""
echo "Prerequisites OK!"
echo ""

# Create reports directory
mkdir -p target/test-reports

# Run tests
echo "Starting test execution..."
echo ""

START_TIME=$(date +%s)

mvn clean test \
    -Dgroups="infrastructure,mysql-functional" \
    -DfailIfNoTests=false \
    -Dsurefire.reportFormat=plain \
    -Dsurefire.useFile=true \
    2>&1 | tee target/test-reports/test-output.log

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo ""
echo "========================================"
echo "Test Execution Complete"
echo "========================================"
echo "Duration: ${DURATION} seconds"
echo "Reports:  target/test-reports/"
echo "Logs: target/test-logs/"
echo ""

# Show summary
if [ -f target/surefire-reports/TEST-*. xml ]; then
    echo "Test Results Summary:"
    grep -h "testsuite" target/surefire-reports/*. xml | head -5
fi