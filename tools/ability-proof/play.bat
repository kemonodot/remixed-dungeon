@echo off
setlocal
set "PROOF_ROOT=%~dp0"
if not exist "%PROOF_ROOT%rundir" mkdir "%PROOF_ROOT%rundir"
if not exist "%PROOF_ROOT%profile" mkdir "%PROOF_ROOT%profile"
if not exist "%PROOF_ROOT%cache" mkdir "%PROOF_ROOT%cache"
cd /d "%PROOF_ROOT%rundir"
set "XDG_CACHE_HOME=%PROOF_ROOT%cache"
set "PROOF_JAVA=java"
if defined JAVA_HOME set "PROOF_JAVA=%JAVA_HOME%\bin\java.exe"
"%PROOF_JAVA%" -Xms64m -Xmx512m --add-opens java.base/java.util=ALL-UNNAMED "-Duser.home=%PROOF_ROOT%profile" -cp "%PROOF_ROOT%RemixedDungeon.jar" com.nyrds.pixeldungeon.desktop.AbilityProofLauncher --windowed --nosound --debug %*
