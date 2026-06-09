@echo off
REM Start MySQL Server 8.4 (if Windows service is not installed)
set MYSQL_BIN=C:\Program Files\MySQL\MySQL Server 8.4\bin
set MYSQL_DATA=C:\ProgramData\MySQL\MySQL Server 8.4\Data

echo Starting MySQL on port 3306...
start "MySQL" "%MYSQL_BIN%\mysqld.exe" --datadir="%MYSQL_DATA%" --port=3306
echo MySQL started. Root password: password
