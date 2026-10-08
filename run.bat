@echo off
echo Compiling Java DSA project...
if not exist out mkdir out
javac -d out src\*.java
if errorlevel 1 (
  echo Compilation failed.
  pause
  exit /b 1
)
echo Starting Product Search and Recommendation System...
java -cp out Main
pause
