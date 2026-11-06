$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $inputDirectory = Join-Path $root "target/package-input"
    $outputDirectory = Join-Path $root "dist/windows"
    $appDirectory = Join-Path $outputDirectory "ERONIX"
    $appJar = "emergency-room-simulation-1.0.0.jar"
    $javaCommand = Get-Command java -ErrorAction Stop
    $javaHome = if ($env:JAVA_HOME) {
        $env:JAVA_HOME
    }
    else {
        Split-Path -Parent (Split-Path -Parent $javaCommand.Source)
    }

    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw "Maven is required."
    }
    if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
        throw "JDK 25 or newer with jpackage is required."
    }

    & mvn --no-transfer-progress clean package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies `
        -DincludeScope=runtime `
        "-DoutputDirectory=$inputDirectory"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }

    Copy-Item (Join-Path $root "target/$appJar") (Join-Path $inputDirectory $appJar)
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
    if (Test-Path $appDirectory) {
        Remove-Item -Recurse -Force $appDirectory
    }

    if (Test-Path (Join-Path $javaHome "jmods")) {
        $jpackageArguments = @(
            "--type", "app-image",
            "--name", "ERONIX",
            "--app-version", "1.0.0",
            "--vendor", "ERONIX",
            "--module-path", $inputDirectory,
            "--module", "hospital.emergency.room.simulation/hospital.ui.HospitalApp",
            "--add-modules", "javafx.controls",
            "--java-options", "--enable-native-access=javafx.graphics",
            "--icon", (Join-Path $root "ERONIX.ico"),
            "--dest", $outputDirectory
        )
    }
    else {
        $jpackageArguments = @(
            "--type", "app-image",
            "--name", "ERONIX",
            "--app-version", "1.0.0",
            "--vendor", "ERONIX",
            "--runtime-image", $javaHome,
            "--input", $inputDirectory,
            "--main-jar", $appJar,
            "--main-class", "hospital.ui.HospitalLauncher",
            "--java-options", "--enable-native-access=ALL-UNNAMED",
            "--icon", (Join-Path $root "ERONIX.ico"),
            "--dest", $outputDirectory
        )
    }
    & jpackage @jpackageArguments
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    Copy-Item (Join-Path $root "scripts/create-windows-desktop-shortcut.ps1") `
        (Join-Path $outputDirectory "create-desktop-shortcut.ps1")
    if ($env:ERONIX_SKIP_DESKTOP_SHORTCUT -ne "1") {
        & (Join-Path $outputDirectory "create-desktop-shortcut.ps1")
    }

    Write-Host "Windows application image created at $appDirectory"
}
finally {
    Pop-Location
}
