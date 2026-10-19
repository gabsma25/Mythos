# Compila o app, instala no celular conectado por USB e copia livros para Download/Mythos.
# Uso: .\scripts\instalar-no-celular.ps1 [-Livros "C:\caminho\da\pasta"] [-Emulador]
param(
    [string]$Livros = "$env:USERPROFILE\Documentos\livros-mythos",
    [switch]$Emulador
)

$ErrorActionPreference = "Stop"
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$raiz = Split-Path $PSScriptRoot -Parent

$conectados = & $adb devices | Select-String "\t(device|unauthorized)$" | ForEach-Object { $_.Line -split "\t" }
if ($conectados -contains "unauthorized") {
    Write-Host "O celular está conectado, mas não autorizou este computador. Desbloqueie a tela e toque em Permitir no aviso de depuração USB." -ForegroundColor Yellow
    exit 1
}
$seriais = & $adb devices | Select-String "\tdevice$" | ForEach-Object { ($_.Line -split "\t")[0] }
if (-not $seriais) {
    Write-Host "Nenhum celular conectado. Ative a depuração USB e autorize este computador no aviso do celular." -ForegroundColor Yellow
    exit 1
}
# Instala no aparelho físico. O emulador só é usado com -Emulador, para que um
# celular desconectado não faça a instalação ir parar no emulador sem aviso.
$fisicos = @($seriais | Where-Object { $_ -notlike "emulator-*" })
$emuladores = @($seriais | Where-Object { $_ -like "emulator-*" })
if ($Emulador) {
    if (-not $emuladores) { Write-Host "Nenhum emulador aberto." -ForegroundColor Yellow; exit 1 }
    $serial = $emuladores[0]
} elseif ($fisicos) {
    $serial = $fisicos[0]
} else {
    Write-Host "Só o emulador está conectado; o celular não aparece para o adb." -ForegroundColor Yellow
    Write-Host "Verifique a Depuração USB e o Bloqueador automático, ou use -Emulador para instalar no emulador." -ForegroundColor Yellow
    exit 1
}
$env:ANDROID_SERIAL = $serial
Write-Host "Dispositivo: $serial ($(& $adb shell getprop ro.product.model))" -ForegroundColor Cyan

Write-Host "Compilando..." -ForegroundColor Cyan
Push-Location $raiz
try { & .\gradlew.bat :app:assembleDebug --console=plain -q; if ($LASTEXITCODE -ne 0) { exit 1 } }
finally { Pop-Location }

Write-Host "Instalando no celular..." -ForegroundColor Cyan
& $adb install -r "$raiz\app\build\outputs\apk\debug\app-debug.apk"

if (Test-Path $Livros) {
    Write-Host "Copiando livros de $Livros para Download/Mythos..." -ForegroundColor Cyan
    & $adb shell mkdir -p /sdcard/Download/Mythos
    Get-ChildItem $Livros -File -Include *.epub -Recurse | ForEach-Object {
        $destino = "/sdcard/Download/Mythos/$($_.Name)"
        & $adb push $_.FullName $destino
        # Arquivos enviados pelo adb ficam "pendentes" no MediaStore e não aparecem no seletor até serem indexados.
        & $adb shell content call --uri content://media --method scan_file --arg "'$destino'" | Out-Null
    }
    $ignorados = Get-ChildItem $Livros -File -Recurse | Where-Object { $_.Extension -ne ".epub" }
    if ($ignorados) {
        Write-Host "Ignorados (formato ainda não suportado): $($ignorados.Name -join ', ')" -ForegroundColor Yellow
    }
}

& $adb shell am start -n com.mythos.reader/.MainActivity | Out-Null
Write-Host "Pronto! No app, toque em Importar > Downloads > Mythos e selecione os livros." -ForegroundColor Green
