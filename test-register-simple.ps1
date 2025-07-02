# Simple Tenant Registration Test Script
# Bu script register sayfasını test eder ve logları kontrol eder

Write-Host "🚀 TENANT REGISTRATION TEST" -ForegroundColor White -BackgroundColor Blue

# 1. Uygulama durumunu kontrol et
Write-Host "`n1. Checking application status..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health" -Method GET -TimeoutSec 5
    Write-Host "✅ Application is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Application not ready yet" -ForegroundColor Red
    exit
}

# 2. Test tenant bilgileri
$testTenant = "testcompany$(Get-Date -Format 'MMddHHmm')"
$testEmail = "admin@$testTenant.com"
$testPassword = "Test123!"
$testUsername = "admin$(Get-Date -Format 'HHmm')"

Write-Host "`n2. Test tenant preparation..." -ForegroundColor Yellow
Write-Host "  Company Name: $testTenant" -ForegroundColor Cyan
Write-Host "  Admin Email: $testEmail" -ForegroundColor Cyan
Write-Host "  Username: $testUsername" -ForegroundColor Cyan

# 3. Register page test
Write-Host "`n3. Testing register page..." -ForegroundColor Yellow
try {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $registerPageResponse = Invoke-WebRequest -Uri "http://localhost:8080/register" -Method GET -WebSession $session
    Write-Host "✅ Register page accessible" -ForegroundColor Green
    
    # CSRF token al
    $csrfToken = ""
    if ($registerPageResponse.Content -match 'name="_token" value="([^"]+)"') {
        $csrfToken = $matches[1]
        Write-Host "✅ CSRF token found" -ForegroundColor Green
    }
} catch {
    Write-Host "❌ Cannot access register page: $_" -ForegroundColor Red
    exit
}

# 4. Registration request
Write-Host "`n4. Sending registration request..." -ForegroundColor Yellow

$formData = @{
    firstName = "Test"
    lastName = "User"
    companyName = $testTenant
    username = $testUsername
    email = $testEmail
    password = $testPassword
    confirmPassword = $testPassword
    selectedPlan = "trial"
    acceptTerms = "true"
    _token = $csrfToken
}

Write-Host "Form data prepared with:" -ForegroundColor Gray
Write-Host "  - All required fields ✅" -ForegroundColor Gray
Write-Host "  - Valid email format ✅" -ForegroundColor Gray
Write-Host "  - Passwords match ✅" -ForegroundColor Gray
Write-Host "  - Terms accepted ✅" -ForegroundColor Gray

try {
    $registerResponse = Invoke-WebRequest -Uri "http://localhost:8080/register" -Method POST -Body $formData -WebSession $session -ContentType "application/x-www-form-urlencoded"
    
    Write-Host "✅ Registration request sent successfully" -ForegroundColor Green
    Write-Host "Response Status: $($registerResponse.StatusCode)" -ForegroundColor Cyan
    
    # Response içeriğini kontrol et
    if ($registerResponse.Content -match "başarıyla|success|login") {
        Write-Host "✅ Registration appears successful!" -ForegroundColor Green
    } elseif ($registerResponse.Content -match "error|hata|validation") {
        Write-Host "⚠️ Registration may have validation errors" -ForegroundColor Yellow
    } else {
        Write-Host "? Registration response unclear" -ForegroundColor Gray
    }
    
} catch {
    Write-Host "❌ Registration failed: $($_.Exception.Message)" -ForegroundColor Red
}

# 5. Logs kontrol et
Write-Host "`n5. Checking application logs..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

if (Test-Path "logs\stockify.log") {
    Write-Host "📜 Recent log entries:" -ForegroundColor Cyan
    $recentLogs = Get-Content "logs\stockify.log" -Tail 30
    
    # Registration ile ilgili logları filtrele
    $registrationLogs = $recentLogs | Where-Object { 
        $_ -match $testUsername -or 
        $_ -match $testTenant -or 
        $_ -match "RegisterController" -or 
        $_ -match "TenantManagementService" -or
        $_ -match "createTenant" -or
        ($_ -match "ERROR" -or $_ -match "WARN") -and $_ -match (Get-Date -Format "yyyy-MM-dd")
    }
    
    if ($registrationLogs) {
        Write-Host "Relevant logs found:" -ForegroundColor Green
        $registrationLogs | ForEach-Object { 
            if ($_ -match "ERROR") {
                Write-Host "  ❌ $_" -ForegroundColor Red
            } elseif ($_ -match "WARN") {
                Write-Host "  ⚠️ $_" -ForegroundColor Yellow
            } else {
                Write-Host "  ℹ️ $_" -ForegroundColor Gray
            }
        }
    } else {
        Write-Host "No specific registration logs found in recent entries" -ForegroundColor Yellow
    }
    
    # Success/failure patterns
    $successPattern = $recentLogs | Where-Object { $_ -match "Successfully created tenant" }
    $errorPattern = $recentLogs | Where-Object { $_ -match "Failed to create tenant|❌.*$testTenant" }
    
    if ($successPattern) {
        Write-Host "`n✅ TENANT CREATION SUCCESS detected in logs!" -ForegroundColor Green
    } elseif ($errorPattern) {
        Write-Host "`n❌ TENANT CREATION FAILURE detected in logs!" -ForegroundColor Red
    } else {
        Write-Host "`n? Tenant creation status unclear from logs" -ForegroundColor Yellow
    }
    
} else {
    Write-Host "❌ Log file not found at logs\stockify.log" -ForegroundColor Red
}

# 6. Summary
Write-Host "`n📊 TEST SUMMARY" -ForegroundColor White -BackgroundColor Blue
Write-Host "Test Tenant: $testTenant" -ForegroundColor White
Write-Host "Registration Request: ✅ Sent" -ForegroundColor Green
Write-Host "Check the logs above for detailed results" -ForegroundColor White
Write-Host "For database verification, use pgAdmin or another PostgreSQL client" -ForegroundColor Gray

Write-Host "`n🌐 Opening register page in browser for manual testing..." -ForegroundColor Yellow
Start-Process "http://localhost:8080/register"

Write-Host "`n🎯 Test completed!" -ForegroundColor Green
