#!/usr/bin/env pwsh
# SkillProof Quick Test Script
# Run this to test if everything is working

Write-Host "`n╔════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   SkillProof Backend - Quick Test Suite    ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════╝`n" -ForegroundColor Cyan

# Colors
$greenCheck = "✅"
$redX = "❌"
$yellow = "⚠️"

# Test 1: Services running
Write-Host "TEST 1: Services Running" -ForegroundColor Yellow
Write-Host "------------------------"

$port8080 = netstat -ano 2>$null | Select-String ":8080" | Select-Object -First 1
$port8000 = netstat -ano 2>$null | Select-String ":8000" | Select-Object -First 1

if ($port8080) { 
    Write-Host "$greenCheck Spring Boot (8080)" -ForegroundColor Green 
} else { 
    Write-Host "$redX Spring Boot (8080) - NOT RUNNING" -ForegroundColor Red 
    Write-Host "   Run: cd c:\Users\gowth\Desktop\skillproof\backend-core && mvn spring-boot:run" -ForegroundColor Gray
}

if ($port8000) { 
    Write-Host "$greenCheck Python AI Service (8000)" -ForegroundColor Green 
} else { 
    Write-Host "$redX Python AI Service (8000) - NOT RUNNING" -ForegroundColor Red 
    Write-Host "   Run: cd c:\Users\gowth\Desktop\skillproof\backend-ai && venv\Scripts\uvicorn app.main:app --reload --port 8000" -ForegroundColor Gray
}

# Test 2: Health endpoints
Write-Host "`nTEST 2: Health Endpoints" -ForegroundColor Yellow
Write-Host "------------------------"

try {
    $py = Invoke-WebRequest http://localhost:8000/health -UseBasicParsing -ErrorAction Stop -TimeoutSec 5
    Write-Host "$greenCheck Python Health Check" -ForegroundColor Green
} catch {
    Write-Host "$redX Python Health Check Failed" -ForegroundColor Red
}

try {
    $java = Invoke-WebRequest http://localhost:8080/api/auth/github -UseBasicParsing -ErrorAction Stop -TimeoutSec 5
    Write-Host "$greenCheck Spring Boot Health Check" -ForegroundColor Green
} catch {
    Write-Host "$redX Spring Boot Health Check Failed" -ForegroundColor Red
}

# Test 3: Full end-to-end test
Write-Host "`nTEST 3: Full End-to-End Pipeline" -ForegroundColor Yellow
Write-Host "----------------------------------"

$token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJHb3d0aGFtMjMyMDA0Iiwicm9sZSI6InVzZXIiLCJpYXQiOjE3NzM4OTA1NjksImV4cCI6MTc3Mzk3Njk2OX0.YDJTtX8T6JiWnI5GkgY5tJIwxHl7PWjmUG8bSXRLYkI"
$body = @{
    repoOwner = "Gowtham232004"
    repoName = "Automl"
} | ConvertTo-Json

Write-Host "Calling /api/verify/start (may take 10-30 seconds)..." -ForegroundColor Gray

try {
    $start = Get-Date
    $response = Invoke-WebRequest -Uri "http://localhost:8080/api/verify/start" `
        -Method Post `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ContentType "application/json" `
        -Body $body `
        -UseBasicParsing `
        -TimeoutSec 60 `
        -ErrorAction Stop
    
    $elapsed = (Get-Date) - $start
    $data = $response.Content | ConvertFrom-Json
    
    Write-Host "$greenCheck Full Pipeline Test" -ForegroundColor Green
    Write-Host "   Response time: $($elapsed.TotalSeconds)s" -ForegroundColor Gray
    Write-Host "   Session ID: $($data.sessionId)" -ForegroundColor Gray
    Write-Host "   Questions generated: $($data.questions.Count)" -ForegroundColor Gray
    
    if ($data.questions.Count -eq 5) {
        Write-Host "   $greenCheck All 5 questions generated" -ForegroundColor Green
        
        Write-Host "`n   Sample question:" -ForegroundColor Gray
        $q = $data.questions[0]
        Write-Host "   Q1 [$($q.difficulty)] - $($q.fileReference)" -ForegroundColor Cyan
        Write-Host "   $($q.questionText.Substring(0, [Math]::Min(70, $q.questionText.Length)))..." -ForegroundColor White
    }
} catch {
    Write-Host "$redX Full Pipeline Test Failed" -ForegroundColor Red
    Write-Host "   Error: $($_.Exception.Message)" -ForegroundColor Gray
}

# Summary
Write-Host "`n╔════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║           Test Summary                     ║" -ForegroundColor Cyan
Write-Host "╚════════════════════════════════════════════╝" -ForegroundColor Cyan

Write-Host "`nIf all tests above show $greenCheck then everything is working!" -ForegroundColor Green
Write-Host "If any test shows $redX then check the error message above." -ForegroundColor Yellow

Write-Host ""
