@echo off
setlocal

set JAVA_HOME=C:\Program Files\Java\jdk-21
set MAVEN_HOME=C:\Program Filespache-maven-3.9.11
set PATH=%JAVA_HOME%in;%MAVEN_HOME%in;%PATH%

if not exist pom.xml (
    echo HATA: pos\pos\ klasorunden calistirin!
    pause
    exit /b 1
)

echo.
echo [1/4] JAR derleniyor...
call mvn.cmd package -DskipTests
if errorlevel 1 ( echo HATA: Maven basarisiz! ^& pause ^& exit /b 1 )
echo JAR tamam.

echo.
echo [2/4] app-input hazirlaniyor...
if not exist targetpp-input mkdir targetpp-input
del /f /q targetpp-input\*.jar >/dev/null 2>&1
copy /y target\pos-0.0.1-SNAPSHOT.jar targetpp-input\r
if errorlevel 1 ( echo HATA: JAR kopyalanamadi! ^& pause ^& exit /b 1 )
echo JAR kopyalandi.

echo.
echo [3/4] Ikon olusturuluyor...
java.exe -cp target\classes com.market.pos.build.IconMaker
if errorlevel 1 ( echo HATA: Ikon olusturulamadi! ^& pause ^& exit /b 1 )

echo.
echo [4/4] jpackage calistiriliyor...
if exist C:\OZRPos-build\OZRPos rmdir /s /q C:\OZRPos-build\OZRPos
call mvn.cmd jpackage:jpackage
if errorlevel 1 ( echo HATA: jpackage basarisiz! ^& pause ^& exit /b 1 )

echo.
echo TAMAMLANDI! C:\OZRPos-build\OZRPos\OZRPos.exe hazir.
echo.
pause
