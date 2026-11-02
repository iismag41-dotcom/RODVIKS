@echo off
rem Start local PostgreSQL 16 for rodviks
"C:\rodviks\tools\postgres\bin\pg_ctl.exe" -D "C:\rodviks\tools\pgdata" -l "C:\rodviks\tools\pg.log" -w start
if errorlevel 1 (
  echo PostgreSQL failed to start. See C:\rodviks\tools\pg.log
  exit /b 1
)
echo PostgreSQL is running on localhost:5432
pause
