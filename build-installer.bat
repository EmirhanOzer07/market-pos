@echo off
setlocal

set JAVA_HOME=C:\Program Files\Java\jdk-21
set MAVEN_HOME=C:\Program Files\apache-maven-3.9.11
set PATH=%JAVA_HOME%\bin;%MAVEN_HOME%\bin;%PATH%

if not exist pom.xml (
    echo HATA: pos\pos\ klasoründen calistirin!
    pause
    exit /b 1
)

echo.
echo [1/4] JAR derleniyor...
call mvn.cmd package -DskipTests
if errorlevel 1 ( echo HATA: Maven basarisiz! & pause & exit /b 1 )
echo JAR tamam.

echo.
echo [2/4] app-input hazirlaniyor...
if not exist target\app-input mkdir target\app-input
del /f /q target\app-input\*.jar 2>/dev/null
copy /y target\pos-0.0.1-SNAPSHOT.jar target\app-input\
if errorlevel 1 ( echo HATA: JAR kopyalanamadi! & pause & exit /b 1 )
echo JAR kopyalandi.

echo.
echo [3/4] Ikon olusturuluyor...
java.exe -cp target\classes com.market.pos.build.IconMaker
if errorlevel 1 ( echo HATA: Ikon olusturulamadi! & pause & exit /b 1 )

echo.
echo [4/4] jpackage calistiriliyor...
if exist C:\OZRPos-build\OZRPos rmdir /s /q C:\OZRPos-build\OZRPos
call mvn.cmd jpackage:jpackage
if errorlevel 1 ( echo HATA: jpackage basarisiz! & pause & exit /b 1 )

echo.
echo TAMAMLANDI! C:\OZRPos-build\OZRPos\OZRPos.exe hazir.
echo Musteri dagitimi icin bu klasoru kopyalayin.
echo.
pause
