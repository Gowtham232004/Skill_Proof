# SkillProof Backend Testing Guide

## Quick Status Check

### 1. Check if Services Are Running

**Check Spring Boot (Port 8080):**
```powershell
netstat -ano | findstr :8080
```
You should see output with port 8080 LISTENING. If you see it, Spring Boot is running ✅

**Check Python AI Service (Port 8000):**
```powershell
netstat -ano | findstr :8000
```
You should see output with port 8000 LISTENING. If you see it, Python service is running ✅

---

## Method 1: Test with PowerShell (Easiest)

### Test 1: Check Python AI Service Health
```powershell
Invoke-WebRequest http://localhost:8000/health -UseBasicParsing | Select-Object -ExpandProperty Content
```

**Expected output:**
```json
{"status":"healthy","service":"skillproof-ai"}
```

If you see this → **Python service is working ✅**
If you see error → **Python service is down ❌**

---

### Test 2: Check Spring Boot Health
```powershell
Invoke-WebRequest http://localhost:8080/api/auth/github -UseBasicParsing | Select-Object -ExpandProperty Content
```

**Expected output:**
```json
{"url":"https://github.com/login/oauth/authorize?..."}
```

If you see this → **Spring Boot is working ✅**
If you see error → **Spring Boot is down ❌**

---

### Test 3: Full End-to-End Test (The Main Test)

This is the **REAL TEST** - it tests everything working together:

```powershell
# Step 1: Set the JWT token
$token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJHb3d0aGFtMjMyMDA0Iiwicm9sZSI6InVzZXIiLCJpYXQiOjE3NzM4OTA1NjksImV4cCI6MTc3Mzk3Njk2OX0.YDJTtX8T6JiWnI5GkgY5tJIwxHl7PWjmUG8bSXRLYkI"

# Step 2: Create request body
$body = @{
    repoOwner = "Gowtham232004"
    repoName = "Automl"
} | ConvertTo-Json

# Step 3: Make the request (this takes 10-30 seconds because it's calling Gemini AI)
$response = Invoke-WebRequest -Uri "http://localhost:8080/api/verify/start" `
  -Method Post `
  -Headers @{"Authorization" = "Bearer $token"} `
  -ContentType "application/json" `
  -Body $body `
  -UseBasicParsing

# Step 4: Parse and display the response
$data = $response.Content | ConvertFrom-Json

Write-Host "✅ SUCCESS - Full pipeline worked!" -ForegroundColor Green
Write-Host ""
Write-Host "Session ID: $($data.sessionId)" -ForegroundColor Cyan
Write-Host "Repository: $($data.repoOwner)/$($data.repoName)" -ForegroundColor Cyan
Write-Host "Language: $($data.primaryLanguage)" -ForegroundColor Cyan
Write-Host "Frameworks: $($data.frameworksDetected -join ', ')" -ForegroundColor Cyan
Write-Host "Questions Generated: $($data.questions.Count)" -ForegroundColor Cyan
Write-Host ""
Write-Host "=== Sample Question ===" -ForegroundColor Yellow
$q = $data.questions[0]
Write-Host "Q1 [$($q.difficulty)] - File: $($q.fileReference)" -ForegroundColor Yellow
Write-Host "$($q.questionText)" -ForegroundColor White
```

**What to expect:**
- Takes 10-30 seconds (it's calling Gemini AI)
- You should see Session ID, Repository info, and 5 questions
- Questions should reference actual files like `middleware.ts`, `utils.ts`, etc.

If you see this → **Everything is working perfectly ✅**

---

## Method 2: Test with Postman (Visual Way)

### Setup:
1. Open **Postman** (download from postman.com if you don't have it)
2. Create a new POST request

### Configure the Request:
- **URL:** `http://localhost:8080/api/verify/start`
- **Method:** POST
- **Headers:** Add a header
  - Key: `Authorization`
  - Value: `Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxIiwidXNlcm5hbWUiOiJHb3d0aGFtMjMyMDA0Iiwicm9sZSI6InVzZXIiLCJpYXQiOjE3NzM4OTA1NjksImV4cCI6MTc3Mzk3Njk2OX0.YDJTtX8T6JiWnI5GkgY5tJIwxHl7PWjmUG8bSXRLYkI`
- **Body:** Select "raw" → "JSON" → Paste:
```json
{
  "repoOwner": "Gowtham232004",
  "repoName": "Automl"
}
```

### Click SEND

You should get a response with 5 questions in the Response panel.

---

## Understanding the Response

### If You Get Questions (Status 200) ✅

```json
{
  "sessionId": 10,
  "status": "IN_PROGRESS",
  "questions": [
    {
      "questionNumber": 1,
      "difficulty": "EASY",
      "fileReference": "middleware.ts",
      "questionText": "Explain the overall architecture..."
    },
    ...4 more questions...
  ]
}
```

**This means:**
- ✅ Spring Boot is working
- ✅ Python AI Service is working
- ✅ Gemini API is working
- ✅ Database is working
- ✅ **Everything is PERFECT**

---

### If You Get an Error

#### Error: "Connection refused" on port 8080
```
Invoke-WebRequest : The underlying connection was closed
```
**Fix:** Start Spring Boot
```powershell
cd c:\Users\gowth\Desktop\skillproof\backend-core
mvn spring-boot:run
```

#### Error: "Connection refused" on port 8000
```
Invoke-WebRequest : The underlying connection was closed
```
**Fix:** Start Python service
```powershell
cd c:\Users\gowth\Desktop\skillproof\backend-ai
venv\Scripts\uvicorn app.main:app --reload --port 8000
```

#### Error: "401 Unauthorized"
```
Invoke-WebRequest : The remote server returned an error: (401) Unauthorized
```
**Fix:** Make sure you're using the correct JWT token and don't remove "Bearer " at the start

#### Error: "Timeout" (takes too long)
If it takes more than 60 seconds, the Gemini API might be slow or down. Wait a moment and try again.

---

## Method 3: Check Logs to See What's Happening

### Python AI Service Logs

Look at the **Python terminal** where you ran `uvicorn`. You should see:
```
2026-03-19 12:35:54,891 - app.main - INFO - Gemini API configured successfully
2026-03-19 12:35:54,891 - app.main - INFO - SkillProof AI Service started on port 8000
INFO:     Application startup complete.
```

When you make a request, you should see:
```
2026-03-19 12:40:00,123 - app.routers.questions - INFO - Generating questions for session 10, repo: Automl
2026-03-19 12:40:15,456 - app.services.question_generator - INFO - Successfully generated 5 questions for Automl
```

**If you see these logs → Python service is processing your request ✅**

### Spring Boot Logs

Look at the **Java terminal** where you ran `mvn spring-boot:run`. You should see:
```
2026-03-19 12:46:38.345  INFO 15328 --- [skillproof-backend] Started Application in 20.647 seconds
```

When you make a request, you should see:
```
2026-03-19 12:46:54.170  INFO 15328 --- [skillproof-backend] Verification start request from user 1 for repo Gowtham232004/Automl
2026-03-19 12:46:55.234  INFO 15328 --- [skillproof-backend] Calling AI service to generate questions for session 10
2026-03-19 12:46:55.567  INFO 15328 --- [skillproof-backend] AI service returned 5 questions for session 10
```

**If you see these logs → Spring Boot is calling Python service ✅**

---

## Quick Command to Test Everything at Once

Copy-paste this into PowerShell and it will tell you the status of everything:

```powershell
Write-Host "SkillProof Backend Status Check" -ForegroundColor Cyan
Write-Host "==============================" -ForegroundColor Cyan
Write-Host ""

# Check Spring Boot
Write-Host "Checking Spring Boot (port 8080)..." -ForegroundColor Yellow
if (netstat -ano | findstr ":8080") {
    Write-Host "✅ Spring Boot running" -ForegroundColor Green
} else {
    Write-Host "❌ Spring Boot NOT running" -ForegroundColor Red
}

# Check Python Service
Write-Host "Checking Python AI Service (port 8000)..." -ForegroundColor Yellow
if (netstat -ano | findstr ":8000") {
    Write-Host "✅ Python service running" -ForegroundColor Green
} else {
    Write-Host "❌ Python service NOT running" -ForegroundColor Red
}

# Test Python Health
Write-Host "Testing Python Health Endpoint..." -ForegroundColor Yellow
try {
    $health = Invoke-WebRequest http://localhost:8000/health -UseBasicParsing -ErrorAction Stop
    Write-Host "✅ Python service is healthy" -ForegroundColor Green
} catch {
    Write-Host "❌ Python service is down" -ForegroundColor Red
}

# Test Spring Boot Health
Write-Host "Testing Spring Boot Health Endpoint..." -ForegroundColor Yellow
try {
    $health = Invoke-WebRequest http://localhost:8080/api/auth/github -UseBasicParsing -ErrorAction Stop
    Write-Host "✅ Spring Boot is healthy" -ForegroundColor Green
} catch {
    Write-Host "❌ Spring Boot is down" -ForegroundColor Red
}

Write-Host ""
Write-Host "Status check complete!" -ForegroundColor Cyan
```

---

## Summary of Tests

| Test | Command | What It Checks | Expected |
|------|---------|---|---|
| **Port 8080 Running** | `netstat -ano \| findstr :8080` | Spring Boot process | LISTENING |
| **Port 8000 Running** | `netstat -ano \| findstr :8000` | Python service process | LISTENING |
| **Python Health** | `Invoke-WebRequest http://localhost:8000/health` | Python API responds | JSON response |
| **Spring Boot Health** | `Invoke-WebRequest http://localhost:8080/api/auth/github` | Spring Boot API responds | JSON with GitHub URL |
| **Full Pipeline** | `Invoke-WebRequest /api/verify/start` with body | Everything works together | 5 questions with file references |

---

## What Each Test Tells You

```
✅ Ports running + Health endpoints working = Services are up
✅ Full pipeline test returns questions = Everything works perfectly
❌ Timeouts = Services might be slow or crashed
❌ 401 Unauthorized = JWT token issue
❌ Connection refused = Services not running
```

---

## If Something Is Wrong

1. **First:** Check if services are running (ports 8080 and 8000)
2. **Second:** Check logs in the terminal windows
3. **Third:** Look for error messages in the response
4. **Fourth:** Restart the service that's failing

---

## Next Steps After Confirming Everything Works

Once you've verified the full pipeline works:
1. ✅ Both services are running
2. ✅ Health endpoints return success
3. ✅ Full pipeline test returns 5 questions

**You're ready for production testing!**
