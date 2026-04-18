@echo off 
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.10
set PATH=%JAVA_HOME%\bin;%PATH%
echo Starting Agent...
call "C:\Program Files\apache-maven-3.9.14\bin\mvn.cmd" -pl agent spring-boot:run
pause 
