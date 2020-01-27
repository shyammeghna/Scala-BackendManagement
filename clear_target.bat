@echo off
:: delete target folder
for /f "delims=" %%i in ('dir %~dp0 /b') do (
 if exist "%~dp0%%i\" (
   if exist %~dp0%%i\target\ (
     rmdir /S /Q %~dp0%%i\target\
   )
 )
)