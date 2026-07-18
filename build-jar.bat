@echo off
REM Sestavi hru do samostatneho spustitelneho JAR souboru bez nutnosti otevirat IntelliJ IDEA.
REM Pouziva Maven zabudovany primo v IntelliJ IDEA (neni potreba mit Maven nainstalovany zvlast).

setlocal
set "MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2024.2.3\plugins\maven\lib\maven3\bin\mvn.cmd"

if not exist "%MVN%" (
    echo Nepodarilo se najit Maven v IntelliJ IDEA na ocekavane ceste:
    echo   %MVN%
    echo Uprav cestu v tomto souboru, pokud mas IntelliJ IDEA nainstalovanou jinde nebo v jine verzi.
    pause
    exit /b 1
)

echo Sestavuji MJUN_GAME.jar...
call "%MVN%" -o clean package

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Sestaveni selhalo. Zkontroluj vypis vyse.
    pause
    exit /b 1
)

echo.
echo Hotovo! Spustitelny JAR je v: target\MJUN_GAME-1.0-SNAPSHOT.jar
echo Spustit muzes dvojklikem na target\MJUN_GAME-1.0-SNAPSHOT.jar (pokud mas asociovane .jar s Javou)
echo nebo prikazem: java -jar target\MJUN_GAME-1.0-SNAPSHOT.jar
pause
