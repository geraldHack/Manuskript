Write-Host "Setze JAVA_HOME auf D:\intellij\jdk-25+36"
$env:JAVA_HOME = "D:\intellij\jdk-25+36"
Write-Host "Stoppe alle Java-Prozesse..."
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Write-Host "Führe Maven Clean aus und starte Anwendung..."
# javafx:run kompiliert automatisch wenn nötig, daher nur clean + javafx:run
mvn clean javafx:run
