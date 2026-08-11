$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
Set-Location 'F:\views\g\Naukri\backend'
Write-Host "Building backend with JAVA_HOME=$env:JAVA_HOME"
& mvn -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "BE build failed with exit code $LASTEXITCODE" }
$jar = 'F:\views\g\Naukri\backend\target\naukri-be.jar'
if (-not (Test-Path $jar)) { throw "Expected jar not found at $jar" }
$ts = (Get-Item $jar).LastWriteTime
$sz = [math]::Round((Get-Item $jar).Length / 1MB, 2)
Write-Host "Step 2: BE build SUCCESS -- jar=$jar  size=${sz}MB  ts=$ts"

