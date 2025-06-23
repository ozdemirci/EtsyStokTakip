# Stockify Application Health Check Script - PowerShell Version
# Run this script to verify the application is working correctly after fixes

Write-Host "🚀 Stockify Application Health Check" -ForegroundColor Cyan
Write-Host "===================================" -ForegroundColor Cyan

# Wait for application to start
Write-Host "⏳ Waiting for application to start..." -ForegroundColor Yellow
Start-Sleep 5

# Check if containers are running
Write-Host "📦 Checking Docker containers..." -ForegroundColor Yellow
docker compose ps

Write-Host ""
Write-Host "🏥 Checking application health..." -ForegroundColor Yellow

# Health check
try {
    $healthResponse = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -UseBasicParsing
    if ($healthResponse.StatusCode -eq 200) {
        Write-Host "✅ Health check: PASSED (HTTP $($healthResponse.StatusCode))" -ForegroundColor Green
        $healthOk = $true
    } else {
        Write-Host "❌ Health check: FAILED (HTTP $($healthResponse.StatusCode))" -ForegroundColor Red
        $healthOk = $false
    }
} catch {
    Write-Host "❌ Health check: FAILED (Connection error)" -ForegroundColor Red
    $healthOk = $false
}

# Check if schemas are created
Write-Host ""
Write-Host "🗄️ Checking tenant schemas..." -ForegroundColor Yellow
try {
    $schemasResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/demo/schemas" -UseBasicParsing
    $schemasContent = $schemasResponse.Content
    if ($schemasContent -like "*global_trade*") {
        Write-Host "✅ Tenant schemas: AVAILABLE" -ForegroundColor Green
        $schemasOk = $true
        # Try to parse JSON for count
        try {
            $schemasJson = $schemasContent | ConvertFrom-Json
            Write-Host "📋 Schema count: $($schemasJson.totalCount)" -ForegroundColor Cyan
        } catch {
            Write-Host "📋 Schema count: N/A" -ForegroundColor Gray
        }
    } else {
        Write-Host "❌ Tenant schemas: NOT FOUND" -ForegroundColor Red
        $schemasOk = $false
    }
} catch {
    Write-Host "❌ Tenant schemas: CONNECTION ERROR" -ForegroundColor Red
    $schemasOk = $false
}

# Check specific tenant data
Write-Host ""
Write-Host "🏢 Checking specific tenant (global_trade)..." -ForegroundColor Yellow
try {
    $headers = @{ "X-TenantId" = "global_trade" }
    $tenantResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/demo/tenant/global_trade/data" -Headers $headers -UseBasicParsing
    if ($tenantResponse.Content -like "*global_trade*") {
        Write-Host "✅ Tenant isolation: WORKING" -ForegroundColor Green
        $tenantOk = $true
    } else {
        Write-Host "❌ Tenant isolation: FAILED" -ForegroundColor Red
        $tenantOk = $false
    }
} catch {
    Write-Host "❌ Tenant isolation: CONNECTION ERROR" -ForegroundColor Red
    $tenantOk = $false
}

Write-Host ""
Write-Host "📊 Application Status Summary:" -ForegroundColor Cyan
Write-Host "==============================" -ForegroundColor Cyan
Write-Host "• Health endpoint: $(if ($healthOk) { "✅ OK" } else { "❌ FAILED" })" -ForegroundColor $(if ($healthOk) { "Green" } else { "Red" })
Write-Host "• Tenant schemas: $(if ($schemasOk) { "✅ OK" } else { "❌ FAILED" })" -ForegroundColor $(if ($schemasOk) { "Green" } else { "Red" })
Write-Host "• Tenant isolation: $(if ($tenantOk) { "✅ OK" } else { "❌ FAILED" })" -ForegroundColor $(if ($tenantOk) { "Green" } else { "Red" })

if ($healthOk -and $schemasOk) {
    Write-Host ""
    Write-Host "🎉 All checks passed! Application is working correctly." -ForegroundColor Green
    exit 0
} else {
    Write-Host ""
    Write-Host "⚠️ Some checks failed. Please check the logs:" -ForegroundColor Yellow
    Write-Host "   docker compose logs stockify-app" -ForegroundColor Gray
    exit 1
}
