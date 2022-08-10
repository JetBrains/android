@ECHO OFF
SETLOCAL

::----------------------------------------------------------------------
:: Android Studio startup script.
::----------------------------------------------------------------------

:: ---------------------------------------------------------------------
:: Ensure IDE_HOME points to the directory where the IDE is installed.
:: ---------------------------------------------------------------------
SET IDE_BIN_DIR=%~dp0
FOR /F "delims=" %%i in ("%IDE_BIN_DIR%\..") DO SET IDE_HOME=%%~fi

:: ---------------------------------------------------------------------
:: Locate a JRE installation directory which will be used to run the IDE.
:: Try (in order): STUDIO_JDK, studio%BITS%.exe.jdk, ..\jbr[-x86], JDK_HOME, JAVA_HOME.
:: ---------------------------------------------------------------------
SET JRE=%IDE_HOME%\jre
SET JAVA_HOME=
SET STUDIO_JDK=
SET JDK_HOME=
SET JAVA_EXE=%JRE%\bin\java.exe
IF NOT EXIST "%JAVA_EXE%" (
  ECHO ERROR: cannot start Android Studio.
  ECHO No JRE found. Please make sure STUDIO_JDK, JDK_HOME, or JAVA_HOME point to a valid JRE installation.
  EXIT /B
)

SET BITS=64
REG Query "HKLM\Hardware\Description\System\CentralProcessor\0" | FIND /i "x86" > NUL && SET BITS=
SET STUDIO_EXE=%IDE_HOME%\bin\studio%BITS%.exe

SET VM_OPTIONS_FILE=%IDE_BIN_DIR%\studio%BITS%.exe.vmoptions
SET USER_VM_OPTIONS_FILE=
SET ACC=-Djb.vmOptionsFile="%VM_OPTIONS_FILE%"
FOR /F "eol=# usebackq delims=" %%i IN ("%VM_OPTIONS_FILE%") DO CALL "%IDE_BIN_DIR%\append.bat" "%%i"

SET CLASS_PATH=%IDE_HOME%\lib\bootstrap.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\util.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\jdom.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\log4j.jar
SET CLASS_PATH=%CLASS_PATH%;%IDE_HOME%\lib\jna.jar
SET CLASS_PATH=%CLASS_PATH%;%JDK%\lib\tools.jar
SET CLASS_PATH=%CLASS_PATH%;%STUDIO_CLASS_PATH%

:: ---------------------------------------------------------------------
:: Run the IDE.
:: ---------------------------------------------------------------------
"%STUDIO_EXE%" disableNonBundledPlugins dontReopenProjects