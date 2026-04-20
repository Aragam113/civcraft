$Host.UI.RawUI.WindowTitle = 'CivCraft build log (live)'

# Pick whichever of the two possible locations has the most recently written
# file, so the window always shows the currently-running build.
$candidates = @()
$candidates += Get-ChildItem 'C:\Users\fajar\AppData\Local\Temp\gbuild*.log' -ErrorAction SilentlyContinue
$candidates += Get-ChildItem 'C:\Users\fajar\AppData\Local\Temp\claude\C--\be88344a-d8ae-4072-87a0-31d5bfb7bbab\tasks\*.output' -ErrorAction SilentlyContinue

$log = ($candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName

if (-not $log) {
    Write-Host "No build log found yet; waiting..." -ForegroundColor Yellow
    while (-not $log) {
        Start-Sleep 2
        $candidates = @()
        $candidates += Get-ChildItem 'C:\Users\fajar\AppData\Local\Temp\gbuild*.log' -ErrorAction SilentlyContinue
        $candidates += Get-ChildItem 'C:\Users\fajar\AppData\Local\Temp\claude\C--\be88344a-d8ae-4072-87a0-31d5bfb7bbab\tasks\*.output' -ErrorAction SilentlyContinue
        $log = ($candidates | Sort-Object LastWriteTime -Descending | Select-Object -First 1).FullName
    }
}

Write-Host "Following: $log" -ForegroundColor Cyan
Write-Host "Press Ctrl+C to stop. Re-open this window to re-snap to the newest log." -ForegroundColor Yellow
Write-Host ""
Get-Content -Wait -Tail 300 $log
