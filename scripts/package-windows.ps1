$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $inputDirectory = Join-Path $root "target/package-input"
    $outputDirectory = Join-Path $root "dist/windows/installers"
    $appVersion = if ($env:ERONIX_VERSION) { $env:ERONIX_VERSION } else { "1.0.0" }
    if ($appVersion -notmatch '^\d+(\.\d+)*$') {
        throw "ERONIX_VERSION must be a numeric dotted version."
    }
    $appJar = "emergency-room-simulation-$appVersion.jar"
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
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

    & mvn --no-transfer-progress clean package org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies `
        "-Drevision=$appVersion" `
        -DincludeScope=runtime `
        "-DoutputDirectory=$inputDirectory"
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }

    Copy-Item (Join-Path $root "target/$appJar") (Join-Path $inputDirectory $appJar)
    if (Test-Path (Join-Path $javaHome "jmods")) {
        $jpackageArguments = @(
            "--type", "msi",
            "--name", "ERONIX",
            "--app-version", $appVersion,
            "--vendor", "ERONIX",
            "--win-upgrade-uuid", "8A90D06E-6EA0-4A31-A6A6-3BC54FC45149",
            "--win-menu",
            "--win-menu-group", "ERONIX",
            "--module-path", $inputDirectory,
            "--module", "hospital.emergency.room.simulation/hospital.ui.HospitalApp",
            "--add-modules", "javafx.controls",
            "--java-options", "--enable-native-access=javafx.graphics",
            "--java-options", "-Deronix.version=$appVersion",
            "--icon", (Join-Path $root "ERONIX.ico"),
            "--dest", $outputDirectory
        )
    }
    else {
        $jpackageArguments = @(
            "--type", "msi",
            "--name", "ERONIX",
            "--app-version", $appVersion,
            "--vendor", "ERONIX",
            "--win-upgrade-uuid", "8A90D06E-6EA0-4A31-A6A6-3BC54FC45149",
            "--win-menu",
            "--win-menu-group", "ERONIX",
            "--runtime-image", $javaHome,
            "--input", $inputDirectory,
            "--main-jar", $appJar,
            "--main-class", "hospital.ui.HospitalLauncher",
            "--java-options", "--enable-native-access=ALL-UNNAMED",
            "--java-options", "-Deronix.version=$appVersion",
            "--icon", (Join-Path $root "ERONIX.ico"),
            "--dest", $outputDirectory
        )
    }
    & jpackage @jpackageArguments
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed with exit code $LASTEXITCODE."
    }

    Write-Host "Windows MSI installer created in $outputDirectory for version $appVersion"
}
finally {
    Pop-Location
}
