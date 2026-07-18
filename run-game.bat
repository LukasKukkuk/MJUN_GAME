@echo off
REM Spusti jiz sestaveny JAR (nejdriv spust build-jar.bat, pokud jeste neexistuje).

set "JAR=%~dp0target\MJUN_GAME-1.0-SNAPSHOT.jar"

if not exist "%JAR%" (
    echo JAR soubor nenalezen: %JAR%
    echo Nejdriv spust build-jar.bat pro sestaveni hry.
    pause
    exit /b 1
)

start "" javaw -jar "%JAR%"
