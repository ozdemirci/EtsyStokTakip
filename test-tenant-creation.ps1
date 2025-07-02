# PowerShell Script to Test Tenant Creation
# Bu script yeni tenant oluşturma işlemini test eder

Write-Host "🧪 STOCKIFY TENANT CREATION TEST SCRIPT" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan

# 1. Uygulama durumunu kontrol et
Write-Host "`n📋 1. Checking application status..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -Method GET -TimeoutSec 5
    if ($response.StatusCode -eq 200) {
        Write-Host "✅ Application is running" -ForegroundColor Green
    }
} catch {
    Write-Host "❌ Application is not running. Please start it first." -ForegroundColor Red
    Write-Host "Run: mvn clean compile spring-boot:run -Dspring-boot.run.profiles=dev" -ForegroundColor Yellow
    exit 1
}

# 2. Mevcut şemaları listele
Write-Host "`n📋 2. Listing current PostgreSQL schemas..." -ForegroundColor Yellow
try {
    $env:PGPASSWORD = "postgres"
    $schemas = psql -h localhost -U postgres -d stockify -t -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast', 'pg_temp_1', 'pg_toast_temp_1') ORDER BY schema_name;"
    Write-Host "Current schemas:" -ForegroundColor Green
    $schemas | ForEach-Object { Write-Host "  - $_" -ForegroundColor White }
} catch {
    Write-Host "❌ Could not connect to PostgreSQL. Make sure it's running." -ForegroundColor Red
}

# 3. Test tenant bilgileri
$testTenantName = "testcompany$(Get-Date -Format 'MMddHHmm')"
$testEmail = "admin@$testTenantName.com"
$testPassword = "Test123!"

Write-Host "`n📋 3. Creating test tenant..." -ForegroundColor Yellow
Write-Host "  Tenant Name: $testTenantName" -ForegroundColor White
Write-Host "  Admin Email: $testEmail" -ForegroundColor White

# 4. Register POST request
Write-Host "`n📋 4. Sending register request..." -ForegroundColor Yellow

$registerData = @{
    tenantName = $testTenantName
    adminEmail = $testEmail
    adminPassword = $testPassword
    confirmPassword = $testPassword
} | ConvertTo-Json

try {
    $registerResponse = Invoke-WebRequest -Uri "http://localhost:8080/register" -Method POST -Body $registerData -ContentType "application/json" -TimeoutSec 30
    Write-Host "✅ Register request sent successfully" -ForegroundColor Green
    Write-Host "Response Status: $($registerResponse.StatusCode)" -ForegroundColor White
} catch {
    Write-Host "❌ Register request failed: $($_.Exception.Message)" -ForegroundColor Red
    
    # Try with form data instead
    Write-Host "`n🔄 Trying with form data..." -ForegroundColor Yellow
    try {
        $formData = @{
            tenantName = $testTenantName
            adminEmail = $testEmail
            adminPassword = $testPassword
            confirmPassword = $testPassword
        }
        $registerResponse = Invoke-WebRequest -Uri "http://localhost:8080/register" -Method POST -Body $formData -TimeoutSec 30
        Write-Host "✅ Register request with form data sent successfully" -ForegroundColor Green
    } catch {
        Write-Host "❌ Both JSON and form requests failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# 5. Kısa bekleme
Write-Host "`n📋 5. Waiting for schema creation..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# 6. Yeni şema oluşturulup oluşturulmadığını kontrol et
Write-Host "`n📋 6. Checking if new schema was created..." -ForegroundColor Yellow
try {
    $env:PGPASSWORD = "postgres"
    $newSchemas = psql -h localhost -U postgres -d stockify -t -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name = '$testTenantName';"
    
    if ($newSchemas -match $testTenantName) {
        Write-Host "✅ Schema '$testTenantName' was created successfully!" -ForegroundColor Green
        
        # 7. Şemadaki tabloları kontrol et
        Write-Host "`n📋 7. Checking tables in new schema..." -ForegroundColor Yellow
        $tables = psql -h localhost -U postgres -d stockify -t -c "SELECT table_name FROM information_schema.tables WHERE table_schema = '$testTenantName' ORDER BY table_name;"
        
        if ($tables) {
            Write-Host "✅ Tables found in schema '$testTenantName':" -ForegroundColor Green
            $tables | ForEach-Object { Write-Host "  - $_" -ForegroundColor White }
        } else {
            Write-Host "❌ No tables found in schema '$testTenantName'!" -ForegroundColor Red
            Write-Host "This indicates the schema migration failed." -ForegroundColor Red
        }
    } else {
        Write-Host "❌ Schema '$testTenantName' was not created!" -ForegroundColor Red
    }
} catch {
    Write-Host "❌ Could not check schema creation: $($_.Exception.Message)" -ForegroundColor Red
}

# 8. Log dosyasını kontrol et
Write-Host "`n📋 8. Checking recent logs..." -ForegroundColor Yellow
if (Test-Path "logs\stockify.log") {
    Write-Host "Recent log entries related to tenant creation:" -ForegroundColor Green
    Get-Content "logs\stockify.log" -Tail 20 | Where-Object { $_ -match $testTenantName -or $_ -match "TenantManagementService" -or $_ -match "Flyway" }
} else {
    Write-Host "❌ Log file not found at logs\stockify.log" -ForegroundColor Red
}

Write-Host "`n🏁 Test completed!" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan
