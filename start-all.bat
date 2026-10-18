@echo off
setlocal

set "ROOT=%~dp0"

start "Mock External API" /D "%ROOT%" cmd /k "call gradlew.bat :mock-external-api:bootRun"
start "Backend" /D "%ROOT%" cmd /k "call gradlew.bat :backend:bootRun"
start "Frontend" /D "%ROOT%frontend" cmd /k "npm run dev"
