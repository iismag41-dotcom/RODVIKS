@echo off
rem Stop local PostgreSQL 16 for rodviks
"C:\rodviks\tools\postgres\bin\pg_ctl.exe" -D "C:\rodviks\tools\pgdata" -m fast -w stop
if errorlevel 1 (
  echo PostgreSQL failed to stop. It may not be running.
  exit /b 1
)
echo PostgreSQL stopped.
pause
