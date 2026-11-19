# Builds a native Windows installer for DevForge with jpackage (bundles a Java runtime).
# Usage: powershell -File scripts\package.ps1 [-Type exe|msi|app-image]
# Requires JDK 21+ and (for exe/msi) the WiX Toolset v3 on PATH. Maven is bundled through .\mvnw.cmd.
param([string]$Type = "app-image")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

$pom = Get-Content pom.xml -Raw
$fullVersion = [regex]::Match($pom, "<version>([^<]+)</version>").Groups[1].Value
$appVersion = $fullVersion -replace "-.*", ""
$jar = "devforge-$fullVersion.jar"

.\mvnw.cmd -B clean verify
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
if (Test-Path dist) { Remove-Item dist -Recurse -Force }
New-Item -ItemType Directory dist | Out-Null
$packageInput = "target\jpackage-input"
New-Item -ItemType Directory $packageInput | Out-Null
Copy-Item "target\$jar" (Join-Path $packageInput $jar)

$modules = "java.base,java.sql,java.xml,java.net.http,java.logging,java.naming,java.management,jdk.crypto.ec,jdk.unsupported"

# --win-console is required: DevForge is a command-line program.
jpackage --type $Type --name devforge --app-version $appVersion --vendor "DevForge" `
  --description "Developer productivity and project management CLI" `
  --input $packageInput --main-jar $jar --main-class com.devforge.Main `
  --add-modules $modules --java-options "-Dfile.encoding=UTF-8" `
  --win-console --win-dir-chooser --win-menu --dest dist
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }
Get-ChildItem dist
