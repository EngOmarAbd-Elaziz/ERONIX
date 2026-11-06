$ErrorActionPreference = "Stop"

$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$applicationLauncher = Join-Path $scriptDirectory "ERONIX\ERONIX.exe"
$applicationIcon = Join-Path $scriptDirectory "ERONIX\ERONIX.exe"
$desktopDirectory = [Environment]::GetFolderPath("Desktop")
$shortcutPath = Join-Path $desktopDirectory "ERONIX.lnk"

if (-not (Test-Path $applicationLauncher -PathType Leaf)) {
    throw "ERONIX launcher was not found at: $applicationLauncher"
}
if (-not (Test-Path $desktopDirectory -PathType Container)) {
    throw "Windows Desktop folder was not found: $desktopDirectory"
}

$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $applicationLauncher
$shortcut.WorkingDirectory = Split-Path -Parent $applicationLauncher
$shortcut.IconLocation = "$applicationIcon,0"
$shortcut.Description = "ERONIX - Emergency Room Simulation"
$shortcut.Save()

Write-Host "ERONIX desktop shortcut created at: $shortcutPath"
