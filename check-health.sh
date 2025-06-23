#!/bin/bash

# Stockify Application Health Check Script
# Run this script to verify the application is working correctly after fixes

echo "🚀 Stockify Application Health Check"
echo "==================================="

# Wait for application to start
echo "⏳ Waiting for application to start..."
sleep 5

# Check if containers are running
echo "📦 Checking Docker containers..."
docker-compose ps

echo ""
echo "🏥 Checking application health..."

# Health check
HEALTH_RESPONSE=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health)
if [ "$HEALTH_RESPONSE" = "200" ]; then
    echo "✅ Health check: PASSED (HTTP $HEALTH_RESPONSE)"
else
    echo "❌ Health check: FAILED (HTTP $HEALTH_RESPONSE)"
fi

# Check if schemas are created
echo ""
echo "🗄️ Checking tenant schemas..."
SCHEMAS_RESPONSE=$(curl -s http://localhost:8080/api/demo/schemas)
if echo "$SCHEMAS_RESPONSE" | grep -q "global_trade"; then
    echo "✅ Tenant schemas: AVAILABLE"
    echo "📋 Schema count: $(echo "$SCHEMAS_RESPONSE" | jq -r '.totalCount' 2>/dev/null || echo 'N/A')"
else
    echo "❌ Tenant schemas: NOT FOUND"
fi

# Check specific tenant data
echo ""
echo "🏢 Checking specific tenant (global_trade)..."
TENANT_RESPONSE=$(curl -s -H "X-TenantId: global_trade" http://localhost:8080/api/demo/tenant/global_trade/data)
if echo "$TENANT_RESPONSE" | grep -q "global_trade"; then
    echo "✅ Tenant isolation: WORKING"
else
    echo "❌ Tenant isolation: FAILED"
fi

echo ""
echo "📊 Application Status Summary:"
echo "=============================="
echo "• Health endpoint: $([ "$HEALTH_RESPONSE" = "200" ] && echo "✅ OK" || echo "❌ FAILED")"
echo "• Tenant schemas: $(echo "$SCHEMAS_RESPONSE" | grep -q "global_trade" && echo "✅ OK" || echo "❌ FAILED")"
echo "• Tenant isolation: $(echo "$TENANT_RESPONSE" | grep -q "global_trade" && echo "✅ OK" || echo "❌ FAILED")"

if [ "$HEALTH_RESPONSE" = "200" ] && echo "$SCHEMAS_RESPONSE" | grep -q "global_trade"; then
    echo ""
    echo "🎉 All checks passed! Application is working correctly."
    exit 0
else
    echo ""
    echo "⚠️ Some checks failed. Please check the logs:"
    echo "   docker-compose logs stockify-app"
    exit 1
fi
