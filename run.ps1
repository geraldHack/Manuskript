Write-Host "Stoppe alle Java-Prozesse..."
Get-Process -Name "java" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2
Write-Host "Führe Maven Clean compile aus..."
mvn clean compile
Write-Host "Starte Anwendung..."
mvn javafx:run
