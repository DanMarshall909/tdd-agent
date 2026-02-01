# Bootstrap Gradle
$url = "https://services.gradle.org/distributions/gradle-8.5-bin.zip"
$zipFile = "gradle-8.5-bin.zip"
$extractTo = "."

Write-Host "Downloading Gradle 8.5 from $url"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

try {
    Invoke-WebRequest -Uri $url -OutFile $zipFile -UseBasicParsing
    Write-Host "Downloaded successfully"

    Write-Host "Extracting..."
    Expand-Archive -Path $zipFile -DestinationPath $extractTo -Force

    Write-Host "Setting up wrapper JAR..."
    Copy-Item "gradle-8.5\lib\gradle-wrapper.jar" "gradle\wrapper\gradle-wrapper.jar" -Force

    Remove-Item $zipFile
    Write-Host "Bootstrap complete! Run: .\gradlew.bat build"
} catch {
    Write-Host "Error: $_"
}
