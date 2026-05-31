$MavenVersion = "3.9.6"
$MavenDir = "$PSScriptRoot\.mvn-portable\apache-maven-$MavenVersion"
$MavenZip = "$PSScriptRoot\.mvn-portable\apache-maven-$MavenVersion-bin.zip"
$MavenUrl = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"

# Ensure .mvn-portable directory exists
if (-not (Test-Path "$PSScriptRoot\.mvn-portable")) {
    New-Item -ItemType Directory -Path "$PSScriptRoot\.mvn-portable" | Out-Null
}

if (-not (Test-Path "$MavenDir\bin\mvn.cmd")) {
    Write-Host "Maven not found. Downloading Apache Maven $MavenVersion..." -ForegroundColor Cyan
    
    # Download Maven
    Invoke-WebRequest -Uri $MavenUrl -OutFile $MavenZip
    
    Write-Host "Extracting Maven..." -ForegroundColor Cyan
    # Extract to .mvn-portable folder
    Expand-Archive -Path $MavenZip -DestinationPath "$PSScriptRoot\.mvn-portable"
    
    # Clean up zip
    Remove-Item $MavenZip
}

# Run maven package
Write-Host "Running Maven build..." -ForegroundColor Green
& "$MavenDir\bin\mvn.cmd" clean package -DskipTests

if ($LASTEXITCODE -eq 0) {
    Write-Host "Build successful! Starting LibraryGUI..." -ForegroundColor Green
    java -cp target/digital-library-search-1.0-SNAPSHOT.jar com.bdata.LibraryGUI
}
else {
    Write-Host "Build failed with exit code $LASTEXITCODE." -ForegroundColor Red
}
