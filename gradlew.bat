@echo off
setlocal

set WRAPPER_JAR=gradle\wrapper\gradle-wrapper.jar

if not exist "%WRAPPER_JAR%" (
    if exist "%SystemRoot%\System32\curl.exe" (
        mkdir gradle\wrapper 2>nul
        curl -fsSL -o "%WRAPPER_JAR%" "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
    ) else (
        echo ERROR: curl.exe is required to fetch the Gradle wrapper JAR.
        exit /b 1
    )
)

java -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
