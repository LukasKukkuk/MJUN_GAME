@echo off
REM Spusti hru se ZOBRAZENOU konzoli (na rozdil od run-game.bat) - vidis logy/chyby
REM primo v tomto okne, uzitecne kdyz neco nefunguje (napr. Discord napojeni).

set "JAR=%~dp0target\MJUN_GAME-1.0-SNAPSHOT.jar"

if not exist "%JAR%" (
    echo JAR soubor nenalezen: %JAR%
    echo Nejdriv spust build-jar.bat pro sestaveni hry.
    pause
    exit /b 1
)

java -jar "%JAR%"
echo.
echo Hra skoncila. Okno zustava otevrene, at si muzes precist logy vyse.
pause
