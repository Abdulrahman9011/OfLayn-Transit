@echo off
set APP_HOME=%~dp0
set JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar
if not exist "%JAR%" (
  echo Downloading gradle-wrapper.jar ...
  powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.9.0/gradle/wrapper/gradle-wrapper.jar' -OutFile '%JAR%'" || (echo Download failed. Run: gradle wrapper --gradle-version 8.9 & exit /b 1)
)
java -Dorg.gradle.appname=gradlew -classpath "%JAR%" org.gradle.wrapper.GradleWrapperMain %*
