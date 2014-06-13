@ECHO off

REM Manual updater for Android Studio.
REM
REM Android Studio has an integrated update mechanism which is the recommended
REM way to update Studio. Some users have experienced issues with applying updates
REM and in this case the Android Studio team might ask you to try to manually
REM update using this script. If you use this please make sure to give us some
REM feedback by reporting a bug on b.android.com.
REM
REM This bat figures out the current Studio build and the latest canary
REM available on the server. It downloads the windows patch and tries to
REM apply it.
REM
REM Variables you might want to modify:
REM - FROM: the build-number to update from (in the form 123.456789, no quotes)
REM - TO  : the build-number to update to   (in the form 123.456789, no quotes)
REM - java_exe: The path to your java executable.
REM
REM IMPORTANT: to execute this, copy the BAT into the android-studio\bin directory
REM (the bin directory where studio.exe is located) and execute it from a cmd.exe
REM from the android-studio directory, like this:
REM   Start Menu > cmd.exe
REM   > cd path\to\android-studio
REM   > bin\update_studio.bat


SETLOCAL enableextensions enabledelayedexpansion

REM Change current directory and drive to parent of where the script is.
SET STUDIO_DIR=%~dps0..
CD /d %STUDIO_DIR%
IF not exist bin\studio.exe (
    ECHO This does not look like an Android Studio directory.
    ECHO Please place this bat file in your android-studio\bin
    ECHO directory and try again.
    GOTO :EOF
)

REM in case the TEMP variable contain spaces, get a short version of it
CALL :get_short_temp "%TEMP%"

REM If you know which exact build number you have, you can set it here.
REM Otherwise leave it blank and the script will figure it out.
SET FROM=

IF "x%FROM%" == "x" CALL :compute_from

REM If you know which exact build number you want to update to, you can set it here.
REM Otherwise leave it blank and the script will figure it out.
SET TO=

IF "x%TO%" == "x" CALL :compute_to

IF "x%TO%" == "x%FROM%" (
    ECHO You already have the latest Android Studio version %FROM%.
    ECHO There is nothing to update.
    ECHO .
    PAUSE
    GOTO :EOF
)

ECHO This script will download the Studio updater from %FROM% to %TO%.
PAUSE
ECHO .

SET JAR_URL=https://dl.google.com/android/studio/patches/AI-%FROM%-%TO%-patch-win.jar
SET JAR=%SHORT_TEMP%\AI-%FROM%-%TO%-patch-win.jar
CALL :download_jar

ECHO Current Studio: version %FROM%
ECHO Availabe update: version %TO%
ECHO Updating from %FROM% to %TO%:
ECHO .

SET java_exe=
IF exist sdk\tools CALL sdk\tools\lib\find_java.bat
IF not defined java_exe SET java_exe=java

ECHO Starting update: %java_exe% -classpath %JAR% com.intellij.updater.Runner install %STUDIO_DIR%

%java_exe% -classpath %JAR% com.intellij.updater.Runner install %STUDIO_DIR%

PAUSE
GOTO :EOF

REM ---------

:compute_from
REM read current version, will be in the form AI-123.123456, remove the AI- prefix
SET /P FROM=< build.txt
SET FROM=%FROM:~3%
ECHO Current Studio: version %FROM%
ECHO .
GOTO :EOF

:compute_to
SET XML_URL=https://dl.google.com/android/studio/patches/updates.xml
SET XML=%SHORT_TEMP%\studio_updates.xml
ECHO Download %XML_URL%...
bitsadmin /transfer download_studio_updates_xml %XML_URL% %XML%

REM find first build number in updates.xml
FOR /F "eol=> tokens=1,2" %%i in (%XML%) do (
    IF "x%%i" == "x<build" (
        CALL :set_to %%j
    )
)
ECHO Availabe update: version %TO%
ECHO .
GOTO :EOF

:set_to
IF "x%TO%" == "x" SET TO=%~2
GOTO :EOF

:download_jar
ECHO Download %JAR_URL%...
bitsadmin /transfer download_studio_updater %JAR_URL% %JAR%
ECHO .
GOTO :EOF

:get_short_temp
SET SHORT_TEMP=%~s1
GOTO :EOF

