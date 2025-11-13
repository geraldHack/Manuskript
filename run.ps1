Write-Host "Setze JAVA_HOME auf D:\intellij\jdk-25+36"
$env:JAVA_HOME = "D:\intellij\jdk-25+36"
# Unterdrücke Warnungen über veraltete sun.misc.Unsafe Methoden (von Maven/Guava)
$env:MAVEN_OPTS = "--add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED"
Write-Host "Stoppe alle Java-Prozesse..."
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Write-Host "Führe Maven Clean aus und starte Anwendung..."
# javafx:run kompiliert automatisch wenn nötig, daher nur clean + javafx:run
mvn clean javafx:run
