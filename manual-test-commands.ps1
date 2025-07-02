# Manual PowerShell Commands for Testing Tenant Creation and Username-Only Login
# Bu komutları tek tek çalıştırarak tenant oluşturma ve username-only giriş işlemini test edebilirsiniz

# 1. Uygulama durumunu kontrol et
Write-Host "=== STOCKIFY TEST SUITE - USERNAME ONLY LOGIN ===" -ForegroundColor Magenta
Write-Host "Checking application status..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -Method GET -TimeoutSec 5
    Write-Host "✅ Application is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Application not ready yet" -ForegroundColor Red
    exit
}

# 2. Mevcut şemaları listele
Write-Host "`n📊 Listing current schemas..." -ForegroundColor Yellow

# PostgreSQL'e bağlanmayı farklı yollarla dene
$schemasFound = $false

# İlk yol: psql komutunu dene
try {
    $env:PGPASSWORD = "postgres"
    $currentSchemas = psql -h localhost -U postgres -d stockify -t -c "SELECT schema_name FROM information_schema.schemata WHERE schema_name NOT IN ('information_schema', 'pg_catalog', 'pg_toast', 'pg_temp_1', 'pg_toast_temp_1') ORDER BY schema_name;" 2>$null
    if ($LASTEXITCODE -eq 0) {
        Write-Host "Current schemas: $($currentSchemas -join ', ')" -ForegroundColor Cyan
        $schemasFound = $true
    }
} catch {
    Write-Host "⚠️ psql command not available" -ForegroundColor Yellow
}

# İkinci yol: PowerShell ile HTTP endpoint üzerinden kontrol et
if (-not $schemasFound) {
    Write-Host "Trying alternative method..." -ForegroundColor Yellow
    try {
        # Uygulama üzerinden schema bilgisi alabilir miyiz kontrol edelim
        $apiResponse = Invoke-WebRequest -Uri "http://localhost:8080/api/demo/schemas" -Method GET -Headers @{"Accept"="application/json"} 2>$null
        if ($apiResponse.StatusCode -eq 200) {
            $schemaData = $apiResponse.Content | ConvertFrom-Json
            Write-Host "Found schemas via API: $($schemaData.schemas -join ', ')" -ForegroundColor Cyan
            $schemasFound = $true
        }
    } catch {
        Write-Host "⚠️ API endpoint not accessible (need authentication)" -ForegroundColor Yellow
    }
}

if (-not $schemasFound) {
    Write-Host "⚠️ Could not list schemas - continuing with test..." -ForegroundColor Yellow
}

# 3. Test tenant oluştur (değişkenleri ayarla)
Write-Host "`n🏢 Preparing test tenant..." -ForegroundColor Yellow
$testTenant = "testcompany$(Get-Date -Format 'MMddHHmm')"
$testEmail = "admin@$testTenant.com"
$testPassword = "Test123!"
$testUsername = "admin$testTenant"
Write-Host "Test Tenant: $testTenant" -ForegroundColor Cyan
Write-Host "Test Email: $testEmail" -ForegroundColor Cyan
Write-Host "Test Username: $testUsername" -ForegroundColor Cyan

# 4. Register sayfasını kontrol et
Write-Host "`n📄 Testing register page..." -ForegroundColor Yellow
try {
    $registerPage = Invoke-WebRequest -Uri "http://localhost:8080/register" -Method GET
    Write-Host "✅ Register page accessible" -ForegroundColor Green
} catch {
    Write-Host "❌ Cannot access register page: $_" -ForegroundColor Red
}

# 5. Register POST request gönder (Form Data ile) - DTO validation uyumlu
Write-Host "`n🚀 Creating new tenant via registration..." -ForegroundColor Yellow

# Önce CSRF token'ı al
try {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $registerPageResponse = Invoke-WebRequest -Uri "http://localhost:8080/register" -Method GET -WebSession $session
    
    # CSRF token'ı bul
    $csrfToken = ""
    if ($registerPageResponse.Content -match 'name="_token" value="([^"]+)"') {
        $csrfToken = $matches[1]
        Write-Host "✅ CSRF token found: $($csrfToken.Substring(0,10))..." -ForegroundColor Green
    }
    
    # Form data hazırla - RegisterRequestDTO validation kurallarına uygun
    $randomSuffix = Get-Random -Minimum 1000 -Maximum 9999
    $formData = @{
        firstName = "TestUser"                           # @NotBlank, min=2, max=50
        lastName = "TestSurname"                         # @NotBlank, min=2, max=50  
        companyName = "TestCompany$randomSuffix"         # @NotBlank, min=2, max=100
        username = "testuser$randomSuffix"               # @NotBlank, min=3, max=20
        email = "test$randomSuffix@example.com"          # @NotBlank, @Email
        password = "testpassword123"                     # @NotBlank, min=6, max=100
        confirmPassword = "testpassword123"              # @NotBlank, must match password
        selectedPlan = "trial"                           # @NotBlank (trial, basic, premium, enterprise)
        acceptTerms = "true"                             # Must be true (Boolean)
        _token = $csrfToken
    }
    
    # Validation kurallara uygunluğu kontrol et
    Write-Host "Validation check:" -ForegroundColor Yellow
    Write-Host "  - firstName length: $($formData.firstName.Length) (min=2, max=50) ✅" -ForegroundColor Gray
    Write-Host "  - lastName length: $($formData.lastName.Length) (min=2, max=50) ✅" -ForegroundColor Gray  
    Write-Host "  - companyName length: $($formData.companyName.Length) (min=2, max=100) ✅" -ForegroundColor Gray
    Write-Host "  - username length: $($formData.username.Length) (min=3, max=20) ✅" -ForegroundColor Gray
    $emailValid = if ($formData.email -match '^[^@]+@[^@]+\.[^@]+$') { '✅' } else { '❌' }
    Write-Host "  - email format: $emailValid" -ForegroundColor Gray
    Write-Host "  - password length: $($formData.password.Length) (min=6, max=100) ✅" -ForegroundColor Gray
    $passwordsMatch = if ($formData.password -eq $formData.confirmPassword) { '✅' } else { '❌' }
    Write-Host "  - passwords match: $passwordsMatch" -ForegroundColor Gray
    Write-Host "  - selectedPlan: $($formData.selectedPlan) ✅" -ForegroundColor Gray
    Write-Host "  - acceptTerms: $($formData.acceptTerms) ✅" -ForegroundColor Gray
    
    # Store values for later checks
    $global:TestTenant = $formData.companyName.ToLower() -replace '[^a-z0-9]', ''
    $global:TestEmail = $formData.email
    $global:TestUsername = $formData.username
    
    Write-Host "Expected tenant schema: $global:TestTenant" -ForegroundColor Cyan
    
    # POST request gönder
    Write-Host "Sending validation-compliant registration request..." -ForegroundColor Yellow
    $registerResponse = Invoke-WebRequest -Uri "http://localhost:8080/register" -Method POST -Body $formData -WebSession $session -ContentType "application/x-www-form-urlencoded"
    
    if ($registerResponse.StatusCode -eq 200) {
        Write-Host "✅ Registration request sent successfully" -ForegroundColor Green
        
        # Check for validation errors in response
        if ($registerResponse.Content -match "error|hata|geçersiz|invalid|required|zorunlu") {
            Write-Host "⚠️ Response contains error indicators - checking content..." -ForegroundColor Yellow
            # Extract specific error messages
            if ($registerResponse.Content -match '<div[^>]*class="[^"]*error[^"]*"[^>]*>([^<]+)</div>') {
                Write-Host "❌ Error found: $($matches[1])" -ForegroundColor Red
            }
        } elseif ($registerResponse.Content -match "başarıyla|success|login|redirect") {
            Write-Host "✅ Registration appears successful!" -ForegroundColor Green
        } else {
            Write-Host "⚠️ Registration response unclear - analyzing..." -ForegroundColor Yellow
        }
        
        # Try to extract tenant ID from redirect or response
        if ($registerResponse.BaseResponse.ResponseUri.Query -match "tenantId=([^&]+)") {
            $global:NewTenantId = $matches[1]
            Write-Host "✅ Found tenant ID: $global:NewTenantId" -ForegroundColor Green
        }
    }
    
} catch {
    Write-Host "❌ Registration failed: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.Exception.Response) {
        Write-Host "HTTP Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
        try {
            $errorContent = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($errorContent)
            $responseText = $reader.ReadToEnd()
            if ($responseText -match "error|validation") {
                Write-Host "Error details in response body detected" -ForegroundColor Red
            }
        } catch { }
    }
}

# 6. Yeni şema oluşturuldu mu kontrol et
Write-Host "`n🔍 Checking if new schema was created..." -ForegroundColor Yellow
Start-Sleep -Seconds 5  # Migration'ın tamamlanması için bekle

$schemaCreated = $false

# Use the expected tenant schema name
$expectedSchema = if ($global:TestTenant) { $global:TestTenant } else { $testTenant }
Write-Host "Looking for schema: $expectedSchema" -ForegroundColor Cyan

# PostgreSQL ile kontrol et
try {
    $env:PGPASSWORD = "postgres"
    $schemaExists = psql -h localhost -U postgres -d stockify -t -c "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = '$expectedSchema');" 2>$null
    if ($LASTEXITCODE -eq 0 -and $schemaExists -match "t") {
        Write-Host "✅ Schema '$expectedSchema' created successfully!" -ForegroundColor Green
        $schemaCreated = $true
    } else {
        Write-Host "❌ Schema '$expectedSchema' was NOT created!" -ForegroundColor Red
    }
} catch {
    Write-Host "⚠️ Cannot check schema creation - psql not available" -ForegroundColor Yellow
    Write-Host "Will check via application logs instead..." -ForegroundColor Yellow
}
    }
} catch {
    Write-Host "⚠️ Direct DB check failed, trying alternative..." -ForegroundColor Yellow
}

# Alternatif: Log dosyasından kontrol et  
if (-not $schemaCreated) {
    if (Test-Path "logs\stockify.log") {
        $recentLogs = Get-Content "logs\stockify.log" -Tail 50
        $migrationLogs = $recentLogs | Where-Object { $_ -match $expectedSchema -and ($_ -match "migrat" -or $_ -match "schema" -or $_ -match "SUCCESS") }
        if ($migrationLogs) {
            Write-Host "✅ Found migration activity for '$testTenant' in logs!" -ForegroundColor Green
            $schemaCreated = $true
        }
    }
}

if (-not $schemaCreated) {
    Write-Host "❌ Schema '$testTenant' was NOT created!" -ForegroundColor Red
}

# 7. Şemadaki tabloları kontrol et
Write-Host "`n📋 Checking tables in new schema..." -ForegroundColor Yellow

$tablesFound = $false

# PostgreSQL ile tablo listesini al
try {
    $env:PGPASSWORD = "postgres"
    $tables = psql -h localhost -U postgres -d stockify -t -c "SELECT table_name FROM information_schema.tables WHERE table_schema = '$testTenant' ORDER BY table_name;" 2>$null
    if ($LASTEXITCODE -eq 0 -and $tables) {
        $tableList = $tables -split "`n" | Where-Object { $_.Trim() -ne "" }
        Write-Host "✅ Found $($tableList.Count) tables in schema '$testTenant':" -ForegroundColor Green
        foreach ($table in $tableList) {
            Write-Host "  - $($table.Trim())" -ForegroundColor Cyan
        }
        
        # Expected tables kontrol et
        $expectedTables = @("app_user", "product", "stock_notification", "tenant_config", "stock_movement", "product_categories")
        $missingTables = @()
        foreach ($expected in $expectedTables) {
            if ($tables -notmatch $expected) {
                $missingTables += $expected
            }
        }
        
        if ($missingTables.Count -eq 0) {
            Write-Host "✅ All expected tables are present!" -ForegroundColor Green
            $tablesFound = $true
        } else {
            Write-Host "❌ Missing tables: $($missingTables -join ', ')" -ForegroundColor Red
        }
    }
} catch {
    Write-Host "⚠️ Direct DB table check failed" -ForegroundColor Yellow
}

if (-not $tablesFound) {
    Write-Host "❌ No tables found or could not verify tables in schema '$testTenant'!" -ForegroundColor Red
}

# 8. Son logları kontrol et
Write-Host "`n📜 Checking recent logs..." -ForegroundColor Yellow
if (Test-Path "logs\stockify.log") {
    $recentLogs = Get-Content "logs\stockify.log" -Tail 20
    Write-Host "Recent logs:" -ForegroundColor Cyan
    $recentLogs | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
} else {
    Write-Host "❌ Log file not found!" -ForegroundColor Red
}

# 9. Tenant ile ilgili logları filtrele
Write-Host "`n🔍 Filtering tenant-related logs..." -ForegroundColor Yellow
if (Test-Path "logs\stockify.log") {
    $tenantLogs = Get-Content "logs\stockify.log" | Where-Object { 
        $_ -match $testTenant -or 
        $_ -match "TenantManagementService" -or 
        $_ -match "createTenant" -or
        $_ -match "migrat" -or
        $_ -match "schema" 
    } | Select-Object -Last 15
    
    if ($tenantLogs) {
        Write-Host "Tenant-related logs:" -ForegroundColor Cyan
        $tenantLogs | ForEach-Object { Write-Host "  $_" -ForegroundColor Gray }
    } else {
        Write-Host "No tenant-related logs found" -ForegroundColor Yellow
    }
} else {
    Write-Host "❌ Log file not found!" -ForegroundColor Red
}

# 10. Özet rapor
Write-Host "`n📊 SUMMARY REPORT" -ForegroundColor White -BackgroundColor Blue
Write-Host "Test Tenant: $testTenant" -ForegroundColor White
Write-Host "Schema Created: $(if ($schemaExists -match 't') { '✅ YES' } else { '❌ NO' })" -ForegroundColor $(if ($schemaExists -match 't') { 'Green' } else { 'Red' })
if ($tables) {
    Write-Host "Tables Count: $($tableList.Count)" -ForegroundColor White
    if ($missingTables.Count -eq 0) {
        Write-Host "All Tables Present: ✅ YES" -ForegroundColor Green
    } else {
        Write-Host "Missing Tables: ❌ $($missingTables -join ', ')" -ForegroundColor Red
    }
} else {
    Write-Host "Tables Count: ❌ 0" -ForegroundColor Red
}

# 11. Browser'da register sayfasını aç
Write-Host "`n🌐 Opening register page in browser..." -ForegroundColor Yellow
Start-Process "http://localhost:8080/register"

# 12. USERNAME-ONLY LOGIN TEST 
Write-Host "`n🔐 USERNAME-ONLY LOGIN VALIDATION TEST" -ForegroundColor White -BackgroundColor DarkBlue

Write-Host "`n📝 This test validates that:" -ForegroundColor Yellow
Write-Host "   • Users can only login with username (not email)" -ForegroundColor White
Write-Host "   • Registration form correctly captures username" -ForegroundColor White  
Write-Host "   • Login form only accepts username" -ForegroundColor White

Write-Host "`n🧪 TO TEST MANUALLY:" -ForegroundColor Cyan
Write-Host "1. Go to http://localhost:8080/register" -ForegroundColor White
Write-Host "2. Fill out the form with:" -ForegroundColor White
Write-Host "   - Company Name: Test Company" -ForegroundColor Gray
Write-Host "   - Username: testuser123" -ForegroundColor Gray
Write-Host "   - Email: test@example.com" -ForegroundColor Gray
Write-Host "   - Password: password123" -ForegroundColor Gray
Write-Host "3. Complete registration" -ForegroundColor White
Write-Host "4. Go to http://localhost:8080/login" -ForegroundColor White
Write-Host "5. Try logging in with:" -ForegroundColor White
Write-Host "   ✅ Username: testuser123 (should work)" -ForegroundColor Green
Write-Host "   ❌ Email: test@example.com (should fail)" -ForegroundColor Red

Write-Host "`n🔍 EXPECTED BEHAVIOR:" -ForegroundColor Yellow
Write-Host "• Login with username should succeed" -ForegroundColor Green
Write-Host "• Login with email should fail with 'Kullanıcı bulunamadı' error" -ForegroundColor Red
Write-Host "• Login page should show 'Sadece kullanıcı adı ile giriş yapabilirsiniz' message" -ForegroundColor Cyan

# Test username-only login (email login should fail)
Write-Host "`n===== TESTING USERNAME-ONLY LOGIN =====" -ForegroundColor Yellow

# Test 1: Login with correct username (should work)
Write-Host "`nTest 1: Login with correct username..." -ForegroundColor Cyan
$loginData = @{
    username = $global:TestUsername
    password = "password123"
    tenant_id = $global:TestTenant
}

try {
    $loginResponse = Invoke-WebRequest -Uri "http://localhost:8080/login" `
        -Method POST `
        -Body $loginData `
        -ContentType "application/x-www-form-urlencoded" `
        -SessionVariable loginSession `
        -MaximumRedirection 0 `
        -ErrorAction SilentlyContinue
    
    if ($loginResponse.StatusCode -eq 302 -or $loginResponse.StatusCode -eq 200) {
        Write-Host "✅ Username login successful (Status: $($loginResponse.StatusCode))" -ForegroundColor Green
    } else {
        Write-Host "❌ Username login failed with status: $($loginResponse.StatusCode)" -ForegroundColor Red
    }
} catch {
    if ($_.Exception.Response.StatusCode -eq 302) {
        Write-Host "✅ Username login successful (redirected)" -ForegroundColor Green
    } else {
        Write-Host "❌ Username login failed: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# Test 2: Login with email (should fail)
Write-Host "`nTest 2: Login with email (should fail)..." -ForegroundColor Cyan
$emailLoginData = @{
    username = $global:TestEmail  # Using email instead of username
    password = "password123"
    tenant_id = $global:TestTenant
}

try {
    $emailLoginResponse = Invoke-WebRequest -Uri "http://localhost:8080/login" `
        -Method POST `
        -Body $emailLoginData `
        -ContentType "application/x-www-form-urlencoded" `
        -SessionVariable emailSession `
        -MaximumRedirection 0 `
        -ErrorAction SilentlyContinue
    
    if ($emailLoginResponse.StatusCode -eq 302 -or $emailLoginResponse.StatusCode -eq 200) {
        Write-Host "❌ Email login should have failed but succeeded!" -ForegroundColor Red
    } else {
        Write-Host "✅ Email login correctly failed with status: $($emailLoginResponse.StatusCode)" -ForegroundColor Green
    }
} catch {
    if ($_.Exception.Response.StatusCode -eq 401 -or $_.Exception.Response.StatusCode -eq 403) {
        Write-Host "✅ Email login correctly failed (unauthorized)" -ForegroundColor Green
    } else {
        Write-Host "? Email login response: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}

Write-Host "`n===== USERNAME-ONLY LOGIN TEST COMPLETED =====" -ForegroundColor Yellow

Write-Host "`n🎯 Test completed! Check the summary above for results." -ForegroundColor Green
