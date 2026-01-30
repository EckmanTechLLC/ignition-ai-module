#!/bin/bash

# Test script for POST route debugging
# Replace with your actual Ignition gateway address
GATEWAY="http://192.168.50.12:8088"

echo "=========================================="
echo "Testing POST Routes for Ignition AI Module"
echo "=========================================="
echo ""

# Test payload
PAYLOAD='{
  "conversationId": null,
  "userName": "test_user",
  "projectName": "test_project",
  "message": "Hello, this is a test message"
}'

echo "Test Payload:"
echo "$PAYLOAD"
echo ""

# Test 1: POST to /main/data/ignitionai/sendMessage (current JavaScript path)
echo "----------------------------------------"
echo "Test 1: POST to /main/data/ignitionai/sendMessage"
echo "----------------------------------------"
curl -X POST \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" \
  -v \
  "${GATEWAY}/main/data/ignitionai/sendMessage" 2>&1 | head -30
echo ""
echo ""

# Test 2: POST to /data/ignitionai/sendMessage (without /main/)
echo "----------------------------------------"
echo "Test 2: POST to /data/ignitionai/sendMessage"
echo "----------------------------------------"
curl -X POST \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" \
  -v \
  "${GATEWAY}/data/ignitionai/sendMessage" 2>&1 | head -30
echo ""
echo ""

# Test 3: GET to /main/data/ignitionai/test (we know this works)
echo "----------------------------------------"
echo "Test 3: GET to /main/data/ignitionai/test (baseline)"
echo "----------------------------------------"
curl -X GET \
  -v \
  "${GATEWAY}/main/data/ignitionai/test" 2>&1 | head -30
echo ""
echo ""

# Test 4: GET to /data/ignitionai/test (without /main/)
echo "----------------------------------------"
echo "Test 4: GET to /data/ignitionai/test"
echo "----------------------------------------"
curl -X GET \
  -v \
  "${GATEWAY}/data/ignitionai/test" 2>&1 | head -30
echo ""
echo ""

# Test 5: POST with follow redirects
echo "----------------------------------------"
echo "Test 5: POST to /main/data/ignitionai/sendMessage (follow redirects)"
echo "----------------------------------------"
curl -X POST \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD" \
  -L \
  -v \
  "${GATEWAY}/main/data/ignitionai/sendMessage" 2>&1 | head -30
echo ""
echo ""

echo "=========================================="
echo "Tests Complete"
echo "=========================================="
echo ""
echo "What to look for:"
echo "  - HTTP 200 = Success"
echo "  - HTTP 404 = Route not found"
echo "  - HTTP 301/302 = Redirect (POST may fail)"
echo "  - HTTP 405 = Method not allowed"
echo ""
echo "Check Gateway logs for handler messages:"
echo "  'sendMessage endpoint called' = Handler executed"
echo "  No message = Handler never reached"
