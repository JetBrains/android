/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

# This script relies on NSIS 3.0 (currently 3.0b2), with the UAC, FindProc
# and Nsis7z plugins

# IMPORTANT: to run this script, the defines DIR_SRC and DIR_SDK (if BUNDLE_SDK
# is set) must exist and point to the absolute path where those components can
# be found.
#
# This should look like the following:
#   $DIR_SRC\android-studio\
#   $DIR_SDK\android-sdk.7z
#
# DIR_SRC and DIR_SDK can be set to the same location.

# If uncommented, DRY_RUN visits every page but doesn't actually install
# anything.
#!define DRY_RUN

# If uncommented, this script will build an intermediate uninstall_builder.exe
# which generates an uninstaller when run. This extra step allows us to sign the
# uninstaller. See also: http://nsis.sourceforge.net/Signing_an_Uninstaller
# NOTE: Be sure to run uninstall_builder.exe from the same working directory
# that contains this script so the final uninstaller is put in the right
# location.
#!define UNINSTALL_BUILDER

# If uncommented, bundle the SDK with the installer. This adds ~500MB to the
# installer's size.
#!define BUNDLE_SDK

# If uncommented, bundle the Microsoft redistributables with the installer.
# This is a dependency we hope to remove, eventually, after we rebuild all tool
# exes so they statically link MSVC++ libraries instead of loading them
# dynamically.
#!define DIR_MSREDIST ..\..\..\microsoft

# If uncommented, bundle Intel HAXM with the installer.
#!define DIR_HAXM ..\..\..\intel

Unicode true

!define APP_NAME "Android Studio"
Name "${APP_NAME}"

!ifndef DRY_RUN && UNINSTALL_BUILDER
    !ifndef DIR_SRC
        !warning "DIR_SRC should be defined! This script won't work without it."
        CompileErrorOnPurpose
    !endif

    !ifdef BUNDLE_SDK
        !ifndef DIR_SDK
            !warning "DIR_SDK should be defined! This script won't work without it."
            CompileErrorOnPurpose
        !endif
    !endif
!endif

!ifndef UNINSTALL_BUILDER
# Via the UAC plugin, we start our installer as a user process, collect
# information about the current user, and then elevate to an admin installer.
# See http://nsis.sourceforge.net/UAC_plug-in for details
RequestExecutionLevel user
!else
RequestExecutionLevel admin # The uninstaller always runs in admin mode
!endif

# General Symbol Definitions
!define REGKEY "SOFTWARE\${APP_NAME}"

!define EXE_NAME_32 "studio.exe"
!define EXE_NAME_64 "studio64.exe"

!define USER_ADMIN_HANDOFF_FILE "inst_user_settings.tmp"

!define DEFAULT_STUDIO_PATH "$PROGRAMFILES\Android\Android Studio"
!define FALLBACK_STUDIO_PATH "C:\Android\Android Studio"
!define DEFAULT_SDK_PATH "$PROFILE\AppData\Local\Android\sdk"
!define FALLBACK_SDK_PATH "C:\Android\sdk"
!define ANDROID_USER_SETTINGS "$PROFILE\.android"
!define BAD_CHARS '?%*:|"<>!;'

!define VERSION_MAJOR 2022
!define VERSION_MINOR 3
!define VERSION ${VERSION_MAJOR}.${VERSION_MINOR}
!define VENDOR Android
!define COMPANY "Google LLC"
!define URL http://developer.android.com

# MUI Symbol Definitions
!define MUI_ICON "assets\studio-inst.ico"
!ifndef DRY_RUN
# Abort normally, but dry runs are for testing, and we often want to test,
# quit, and iterate in that case, so don't slow us down then.
!define MUI_ABORTWARNING
!endif
!define MUI_FINISHPAGE_NOAUTOCLOSE
!define MUI_HEADERIMAGE
!define MUI_HEADERIMAGE_BITMAP "assets\header.bmp"
!define MUI_WELCOMEFINISHPAGE_BITMAP "assets\sidebar.bmp"
!define MUI_STARTMENUPAGE_REGISTRY_ROOT HKLM
!define MUI_STARTMENUPAGE_REGISTRY_KEY "${REGKEY}"
!define MUI_STARTMENUPAGE_REGISTRY_VALUENAME StartMenuGroup
!define MUI_STARTMENUPAGE_DEFAULTFOLDER "${APP_NAME}"
!define MUI_FINISHPAGE_RUN
!define MUI_FINISHPAGE_RUN_TEXT "Start ${APP_NAME}"
!define MUI_FINISHPAGE_RUN_FUNCTION s_StartApp
!define MUI_UNICON ${MUI_ICON} # Our studio install icon is generic, so just re-use it for uninstall
!define MUI_UNFINISHPAGE_NOAUTOCLOSE

# Included files
!include UAC.nsh
!include Sections.nsh
!include MUI2.nsh
!include x64.nsh
!include LogicLib.nsh
!include WinVer.nsh
!include nsDialogs.nsh
!include StrFunc.nsh
!include FileFunc.nsh
!include StrContains.nsh
!include TimeStamp.nsh

# StrFunc.nsh requires priming the commands which actually get used later
${StrRep}
${StrStr}

# Helper defines for Sections.nsh
!define SelectSection '!insertmacro SelectSection'
!define UnselectSection '!insertmacro UnselectSection'

# Installer attributes
!ifndef UNINSTALL_BUILDER
OutFile setup.exe
!else
OutFile "uninstall_builder.exe"
SetCompress off
!endif
CRCCheck on
XPStyle on
BrandingText " " # Branding footer disabled
ShowInstDetails hide
ShowUninstDetails hide

# Right-click installer .exe -> Properties -> Details
VIProductVersion ${VERSION}.0.0
VIAddVersionKey ProductName "${APP_NAME}"
VIAddVersionKey ProductVersion "${VERSION}"
VIAddVersionKey CompanyName "${COMPANY}"
VIAddVersionKey CompanyWebsite "${URL}"
VIAddVersionKey FileVersion "${VERSION}"
VIAddVersionKey FileDescription ""
VIAddVersionKey LegalCopyright ""
ShowUninstDetails hide

# Variables
Var s_DefaultSdkPath
Var s_UserSettingsPath
Var s_SystemMemoryMB
Var s_SystemMemoryGB
Var s_InstallHaxmBat
Var s_StudioPath
Var s_SdkPath
Var s_InstallSdk # 0 (skip install) or non-zero (include install)
Var s_InstallHaxm # 0 (skip install) or non-zero (include install)
Var s_SdkReferencePath # Path where we installed the latest SDK to. Will be
                       # different from s_SdkPath if user asks to use an
                       # existing SDK.

Var s_SkipAndroidLicense # If 1, skip the license (we saw it already)
Var s_SkipIntelLicense

Var s_StartMenuGroup

# Page definitions
# Generated by CoolSoft NSIS Dialog Designer
!include PageDefns\UninstallPreviousPage.nsdinc
!include PageDefns\AndroidSdkPage.nsdinc
!include PageDefns\InstallDirsPage.nsdinc
!include PageDefns\HaxmPage.nsdinc

# ---------- Pages -------------------------------------------

Page custom fnc_UninstallPreviousPage_ShowIfNecessary func_UninstallPreviousPage_Leave

# Installer pages
!insertmacro MUI_PAGE_WELCOME

!define MUI_PAGE_CUSTOMFUNCTION_PRE s_ComponentsPagePre
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE s_ComponentsPageLeave
!insertmacro MUI_PAGE_COMPONENTS

!define MUI_PAGE_CUSTOMFUNCTION_PRE s_AndroidLicensePagePre
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE s_AndroidLicensePageLeave
!insertmacro MUI_PAGE_LICENSE licenses/android.txt

!define MUI_PAGE_CUSTOMFUNCTION_PRE s_IntelLicensePagePre
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE s_IntelLicensePageLeave
!insertmacro MUI_PAGE_LICENSE licenses/intel.txt

Page custom fnc_AndroidSdkPage_ShowIfNecessary fnc_AndroidSdkPage_Leave

Page custom fnc_InstallDirsPage_Show fnc_InstallDirsPage_Leave

Page custom fnc_HaxmPage_ShowIfNecessary fnc_HaxmPage_Leave

!insertmacro MUI_PAGE_STARTMENU Application $s_StartMenuGroup

!insertmacro MUI_PAGE_INSTFILES

!insertmacro MUI_PAGE_FINISH

# Uninstaller pages
!insertmacro MUI_UNPAGE_COMPONENTS

!define MUI_UNCONFIRMPAGE_TEXT_TOP "Click uninstall to begin the process."
!define MUI_PAGE_CUSTOMFUNCTION_LEAVE un.s_ConfirmPageLeave
!insertmacro MUI_UNPAGE_CONFIRM

!insertmacro MUI_UNPAGE_INSTFILES

# Installer languages
!insertmacro MUI_LANGUAGE English

# ---------- Sections ----------------------------------------

# Hide a section and make sure it's unselected
!macro EXCLUDE_SECTION SECTION_ID
    # In NSIS, the way you hide a section is by clearing its text
    ${UnselectSection} ${SECTION_ID}
    SectionSetText ${SECTION_ID} ""
!macroend

# Simple macro to call before using the HAXM installer. After calling this,
# $s_InstallHaxmBat will be set.
!macro PREPARE_HAXM_INSTALLER
    !ifdef DIR_HAXM
        File ${DIR_HAXM}\intelhaxm-android.exe
        File ${DIR_HAXM}\silent_install.bat
        StrCpy $s_InstallHaxmBat "$OUTDIR\silent_install.bat"
    !endif
!macroend

# Run the HAXM installer with the following argument string.
!macro RUN_HAXM_INSTALLER ARG_STR ERROR_CODE_VAR STDOUT_VAR
    !ifdef DIR_HAXM
        # Save existing variables
        Push $0
        Push $OUTDIR
        ${GetParent} "$s_InstallHaxmBat" $0
        SetOutPath $0 # Sets $OUTDIR and the cwd

        ${If} ${RunningX64}
            ${DisableX64FSRedirection} # Ensure HAXM bat runs in 64-bit mode
        ${EndIf}

        nsExec::ExecToStack '"$s_InstallHaxmBat" ${ARG_STR}'

        # We restore $0 first, because if one of the macro args happens to also be
        # $0, restoring it second would overwrite the arg.
        # Stack (from bottom to top): $0, $OUTDIR, $STDOUT, $ERROR
        Exch 3 #                      $ERROR, $OUTDIR, $STDOUT, $0
        Pop $0
        Pop ${STDOUT_VAR}
        Pop $OUTDIR
        Pop ${ERROR_CODE_VAR}

        SetOutPath $OUTDIR
    !else
        StrCpy ${ERROR_CODE_VAR} 1
        StrCpy ${STDOUT_VAR} "Installer not bundled with HAXM"
    !endif # DIR_HAXM
!macroend

# Call this macro to see if HAXM is installable on the system. If so, OUT_VAR
# will be set to 1, 0 otherwise. This macro should only be called after
# PREPARE_HAXM_INSTALLER is called.
!macro CAN_INSTALL_HAXM OUT_VAR
    !insertmacro RUN_HAXM_INSTALLER "-c" $R0 $R1
    # Error code is 0 if you can install haxm, non-zero otherwise
    IntOp ${OUT_VAR} $R0 ! # $OUT_VAR = !$R0
!macroend

# Silently install HAXM. This macro should only be called after
# PREPARE_HAXM_INSTALLER is called.
!macro INSTALL_HAXM MEMORY_MB OUT_VAR MESSAGE
    !insertmacro RUN_HAXM_INSTALLER "-m ${MEMORY_MB}" ${OUT_VAR} ${MESSAGE}
    # RUN_HAXM_INSTALLER sets OUT_VAR to 0 on success, non-zero otherwise
    # Change OUT_VAR to 0 on failure, 1 otherwise
    IntOp ${OUT_VAR} ${OUT_VAR} ! # OUT_VAR = !OUT_VAR
!macroend

# Silently uninstall HAXM. This macro should only be called after
# PREPARE_HAXM_INSTALLER is called.
!macro UNINSTALL_HAXM
    !insertmacro RUN_HAXM_INSTALLER "-u" $R0 $R1
    # We currently don't care about the return values for this step - it either
    # worked or it didn't, and we succeed or fail silently.
!macroend

# Call this macro to see if HAXM is already installed on the system. If so,
# OUT_VAR will be set to 1, 0 otherwise. This macro should only be called after
# PREPARE_HAXM_INSTALLER is called.
!macro IS_HAXM_INSTALLED OUT_VAR
    !insertmacro RUN_HAXM_INSTALLER "-v" $R0 $R1
    # Error-code is 0 on installed, non-zero otherwise
    IntOp ${OUT_VAR} $R0 ! # $OUT_VAR = !$R0
!macroend

# Requires the user to stop Studio, if it's running, or else we should abort.
# It's expected that this macro should be used in onInit functions.
!macro STOP_STUDIO_PROCESS
    StrCpy $R2 1 # This var means continue looping. Set to 0 to escape.
    ${DoWhile} $R2 == 1
        FindProcDLL::FindProc ${EXE_NAME_32} # Sets $R0 to 1 if found
        StrCpy $R1 $R0
        FindProcDLL::FindProc ${EXE_NAME_64}

        ${If} $R0 != 1
        ${AndIf} $R1 != 1
            StrCpy $R2 0
        ${Else}
            MessageBox MB_OKCANCEL "Android Studio is currently running. \
            Please exit the application and press OK to continue with the installation."\
            IDOK tryagain IDCANCEL 0
                MessageBox MB_OK "Uninstalling Android Studio has been aborted. Please try again later."
                Abort
            tryagain:
        ${EndIf}
    ${Loop}
!macroend

# Installer sections
Section "Android Studio" SectionStudio
    SectionIn RO # Can't deselect this section

    !ifndef DRY_RUN && UNINSTALL_BUILDER

    SetOutPath $s_StudioPath
    SetOverwrite on

    File /r "${DIR_SRC}\android-studio\*"

    !endif # DRY_RUN && UNINSTALL_BUILDER
SectionEnd

!ifdef BUNDLE_SDK
Section "Android SDK" SectionSdk
    # Final size on disk: 2.2+GB. Add size until components page shows 2.3GB
    AddSize 1930000

    !ifndef DRY_RUN && UNINSTALL_BUILDER

    SetOutPath $TEMP
    SetOverwrite on

    SetCompress off
    File "${DIR_SDK}\android-sdk.7z"
    SetCompress auto

    ${If} $s_InstallSdk != 0
        StrCpy $s_SdkReferencePath "$s_SdkPath"
        StrCpy $R0 "Extracting Android SDK... %s"
    ${Else}
        StrCpy $s_SdkReferencePath "$TEMP\Android Sdk"
        # Should be rare, but perhaps a reference SDK was left over from a previous install
        RMDir /r $s_SdkReferencePath
        StrCpy $R0 "Preparing reference SDK for Android Studio first launch... %s"
    ${EndIf}

    SetOutPath "$s_SdkReferencePath"
    Nsis7z::ExtractWithDetails "$TEMP\android-sdk.7z" "$R0"
    Delete "$TEMP\android-sdk.7z"

    !endif # DRY_RUN && UNINSTALL_BUILDER
SectionEnd
!endif # BUNDLE_SDK

Section "Android Virtual Device" SectionAvd
    # Dummy section for AVD creation. We actually don't do anything here but
    # hand off the work to the Studio first run wizard. See SectionFirstRun.
    AddSize 1048576 # == 1GB. AVD creation creates a 1GB virtual device.
SectionEnd



Section "Performance (Intel® HAXM)" SectionHaxm
    !ifndef DRY_RUN && UNINSTALL_BUILDER

    !insertmacro INSTALL_HAXM "$var_HaxmPage_SelectedMB" $R0 $R1
    ${If} $R0 == 0
        ${StrContains} $0 $R1 "For details, please check the installation log"
        ${If} $0 == 1
            # Error message has log file name inside quotes
            # Find string from first quote to end
            ${StrStr} $0 $R1 '"'
            StrLen $1 $0
            # String contains two quotes plus \r\n
            IntOp $1 $1 - 4
            # Get just the filename
            StrCpy $2 $0 $1 1
            FileOpen $0 $2 r
            # If there's an error opening the file, just print out the original message
            IfErrors plainbox
            # Actual content is on second line. Skip the first line
            # TODO: support case where log is more than one line long
            FileReadUTF16LE $0 $1
            FileReadUTF16LE $0 $1
            FileClose $0
            IfErrors plainbox
            StrCpy $1 "$R1$\r$\n$\r$\n$1"
            MessageBox MB_OK $1 /SD IDOK
        ${Else}
            # If we didn't find the normal error, just print out the normal error
            Goto plainbox
        ${EndIf}
        Goto done
        plainbox:
            MessageBox MB_OK $R1 /SD IDOK
        done:
    ${Endif}

    !endif # DRY_RUN && UNINSTALL_BUILDER
SectionEnd


# MS VC Redistributable
Section -Redist SectionRedist
    !ifdef DIR_MSREDIST
    !ifndef DRY_RUN && UNINSTALL_BUILDER

    SetOutPath $TEMP

    # Always add the VS.Net 2008 and 2010 redistributable CRT installers, relative to this script
    # Note: /q:a is "almost silent with progress bar", /q is totally silent but might
    # still display an EULA & install page on some systems.
    File ${DIR_MSREDIST}\vcredist_x86_2008.exe
    ExecWait '"$TEMP\vcredist_x86_2008.exe" /q:a'
    Delete /REBOOTOK "$TEMP\vcredist_x86_2008.exe"

    ${If} ${RunningX64}
        File ${DIR_MSREDIST}\vcredist_x64_2008.exe
        ExecWait '"$TEMP\vcredist_x64_2008.exe" /q:a'
        Delete /REBOOTOK "$TEMP\vcredist_x64_2008.exe"
    ${EndIf}

    File ${DIR_MSREDIST}\vcredist_x86_2010.exe
    ExecWait '"$TEMP\vcredist_x86_2010.exe" /passive /norestart /showfinalerror'
    Delete /REBOOTOK "$TEMP\vcredist_x86_2010.exe"

    ${If} ${RunningX64}
        File ${DIR_MSREDIST}\vcredist_x64_2010.exe
        ExecWait '"$TEMP\vcredist_x64_2010.exe" /passive /norestart /showfinalerror'
        Delete /REBOOTOK "$TEMP\vcredist_x64_2010.exe"
    ${EndIf}

    !endif # DRY_RUN && UNINSTALL_BUILDER
    !endif # DIR_MSREDIST
SectionEnd

Section -Post SectionSystem
    !ifndef DRY_RUN && UNINSTALL_BUILDER

    WriteRegStr HKLM "${REGKEY}" Path $s_StudioPath
    SetOutPath $s_StudioPath

    !insertmacro MUI_STARTMENU_WRITE_BEGIN Application
    SetOutPath $SMPROGRAMS\$s_StartMenuGroup

    ${If} ${RunningX64}
        CreateShortcut "$SMPROGRAMS\$s_StartMenuGroup\${APP_NAME}.lnk" "$s_StudioPath\bin\${EXE_NAME_64}"
    ${Else}
        CreateShortcut "$SMPROGRAMS\$s_StartMenuGroup\${APP_NAME}.lnk" "$s_StudioPath\bin\${EXE_NAME_32}"
    ${EndIf}

    !insertmacro MUI_STARTMENU_WRITE_END

    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" DisplayName "${APP_NAME}"
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" DisplayVersion "${VERSION}"
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" Publisher "${COMPANY}"
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" URLInfoAbout "${URL}"
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" DisplayIcon "$s_StudioPath\bin\${EXE_NAME_32}"
    WriteRegStr HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" UninstallString $s_StudioPath\uninstall.exe
    WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" NoModify 1
    WriteRegDWORD HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}" NoRepair 1

    WriteRegStr HKLM "${REGKEY}" SdkPath "$s_SdkPath"
    WriteRegStr HKLM "${REGKEY}" UserSettingsPath "$s_UserSettingsPath"
    WriteRegStr HKLM "${REGKEY}" InstallSdk "$s_InstallSdk"
    WriteRegStr HKLM "${REGKEY}" InstallHaxm "$s_InstallHaxm"

    !endif # DRY_RUN && UNINSTALL_BUILDER
SectionEnd

Section -FirstRun SectionFirstRun
    !ifndef DRY_RUN && UNINSTALL_BUILDER

    StrCpy $R9 "false" # Set $R9 to [true|false] if Studio should create an AVD
    SectionGetFlags ${SectionAvd} $R8
    IntOp $R8 $R8 & ${SF_SELECTED}
    ${If} $R8 != 0
        StrCpy $R9 "true"
    ${EndIf}

    ${TimeStamp} $R7

    # Write out the installer->studio handoff file into the user's .android directory
    SetOutPath $PROFILE
    SetOverwrite on

    CreateDirectory "$s_UserSettingsPath\studio"
    CreateDirectory "$s_UserSettingsPath\studio\installer"
    FileOpen $R0 "$s_UserSettingsPath\studio\installer\firstrun.data" w
    # Even though NSIS is writing out UTF16LE, it doesn't provide an option to write out a BOM,
    # which means our file would open incorrectly in a lot of editors. So, we do it ourselves.
    # The BOM for UTF16LE is FFFE. http://en.wikipedia.org/wiki/Byte_order_mark
    FileWriteByte $R0 255
    FileWriteByte $R0 254
    FileWriteUTF16LE $R0 "# This file's encoding is UTF-16LE. If modified, preserve the encoding!$\r$\n"
    FileWriteUTF16LE $R0 "$\r$\n"
    FileWriteUTF16LE $R0 "# Location of the user's Android SDK$\r$\n"
    FileWriteUTF16LE $R0 "androidsdk.dir=$s_SdkPath$\r$\n"
    FileWriteUTF16LE $R0 "$\r$\n"
    FileWriteUTF16LE $R0 "# Location of a reference Android SDK that just has the latest files$\r$\n"
    FileWriteUTF16LE $R0 "androidsdk.repo=$s_SdkReferencePath$\r$\n"
    FileWriteUTF16LE $R0 "$\r$\n"
    FileWriteUTF16LE $R0 "# If true, create a default AVD with optimized settings$\r$\n"
    FileWriteUTF16LE $R0 "create.avd=$R9$\r$\n"
    FileWriteUTF16LE $R0 "$\r$\n"
    FileWriteUTF16LE $R0 "# The UTC timestamp when this installer ran$\r$\n"
    FileWriteUTF16LE $R0 "install.timestamp=$R7$\r$\n"

    FileWriteUTF16LE $R0 "$\r$\n"
    FileWriteUTF16LE $R0 "# The installed version of studio$\r$\n"
    FileOpen $R1 "$EXEDIR\version" R
    FileRead $R1 $R2
    FileClose $R1
    FileWriteUTF16LE $R0 "studio.version=$R2$\r$\n"

    FileClose $R0

    !endif # DRY_RUN && UNINSTALL_BUILDER
SectionEnd


# Uninstaller sections
Section "un.Android Studio" UNSectionStudio
    SectionIn RO # Can't deselect this section

    !ifndef DRY_RUN

    # Don't set REBOOTOK here, or else we may end up installing a new version
    # of Android Studio in this path which gets removed on reboot!
    RmDir /r $s_StudioPath

    !endif # DRY_RUN
SectionEnd

Section "un.Android SDK" UNSectionSdk
    !ifndef DRY_RUN
    RmDir /r /REBOOTOK $s_SdkPath
    !endif # DRY_RUN
SectionEnd

Section "un.Performance (Intel® HAXM)" UNSectionHaxm
    !ifndef DRY_RUN
    !insertmacro UNINSTALL_HAXM
    !endif
SectionEnd

Section -un.FirstRun UNSectionFirstRun
    !ifndef DRY_RUN
        RmDir /r /REBOOTOK "$s_UserSettingsPath\studio\installer"
        RmDir "$s_UserSettingsPath\studio" # Remove if empty
    !endif
SectionEnd

Section "un.Android User Settings" UNSectionUserSettings
    !ifndef DRY_RUN
        RmDir /r /REBOOTOK "${ANDROID_USER_SETTINGS}\"
    !endif
SectionEnd

Section -un.Post UNSectionSystem
    !ifndef DRY_RUN

    DeleteRegKey HKLM "SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\${APP_NAME}"
    DeleteRegValue HKLM "${REGKEY}" StartMenuGroup
    DeleteRegValue HKLM "${REGKEY}" Path
    # We don't add JdkPath anymore, but left in case we uninstall an old version
    DeleteRegValue HKLM "${REGKEY}" JdkPath
    DeleteRegValue HKLM "${REGKEY}" SdkPath
    DeleteRegValue HKLM "${REGKEY}" InstallSdk
    DeleteRegValue HKLM "${REGKEY}" InstallHaxm
    DeleteRegValue HKLM "${REGKEY}" UserSettingsPath

    DeleteRegKey /IfEmpty HKLM "${REGKEY}"

    Delete /REBOOTOK "$SMPROGRAMS\$s_StartMenuGroup\${APP_NAME}.lnk"
    RmDir  /REBOOTOK "$SMPROGRAMS\$s_StartMenuGroup"

    !endif # DRY_RUN
SectionEnd

# If you add a new uninstall section, and it shouldn't be run if all we're doing
# is overwriting an existing version of Studio, then update the logic in
# un.onInit to exclude the section from running in silent mode.

!insertmacro MUI_FUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${SectionStudio} \
    "The Android developer environment to design and develop your app for \
    Android."
!ifdef BUNDLE_SDK
  !insertmacro MUI_DESCRIPTION_TEXT ${SectionSdk} \
    "The collection of Android platform APIs, tools and utilities that enables \
    you to debug, profile, and compile your app."
!endif
  !insertmacro MUI_DESCRIPTION_TEXT ${SectionAvd} \
    "A preconfigured and optimized Android Virtual Device for app testing on \
    the emulator.  (Recommended)"
  !insertmacro MUI_DESCRIPTION_TEXT ${SectionHaxm} \
    "A hardware-assisted virtualization engine (hypervisor) which speeds up \
    Android emulation on your development computer. (Recommended)"
!insertmacro MUI_FUNCTION_DESCRIPTION_END

!insertmacro MUI_UNFUNCTION_DESCRIPTION_BEGIN
  !insertmacro MUI_DESCRIPTION_TEXT ${UNSectionStudio} \
    "The Android developer environment to design and develop your app for \
    Android."
  !insertmacro MUI_DESCRIPTION_TEXT ${UNSectionSdk} \
    "The collection of Android platform APIs, tools and utilities that enables \
    you to debug, profile, and compile your app."
  !insertmacro MUI_DESCRIPTION_TEXT ${UNSectionHaxm} \
    "A hardware-assisted virtualization engine (hypervisor) which speeds up \
    Android emulation on your development computer. (Recommended)"
  !insertmacro MUI_DESCRIPTION_TEXT ${UNSectionUserSettings} \
    "A folder that contains your saved Android Virtual Devices (AVDs), Android \
    SDK caches, and, potentially, app signing keystores."
!insertmacro MUI_UNFUNCTION_DESCRIPTION_END

# ---------- Functions ---------------------------------------

# Returns 1 if PATH contains only valid chars, 0 otherwise. If INCLUDE_SPACE is 1, consider space an invalid char
Var validPath_path
Var validPath_includeSpace
Var validPath_badChars

Function fnc_ValidPath
    Pop $validPath_includeSpace
    Pop $validPath_path
    Push $R0
    StrLen $R0 $validPath_path
    StrCpy $validPath_path $validPath_path $R0 2 ; Skip c:
    ${If} $validPath_includeSpace == 0
        StrCpy $validPath_badChars '${BAD_CHARS}'
    ${Else}
        StrCpy $validPath_badChars ' ${BAD_CHARS}'
    ${EndIf}
    ${StrContainsAnyOf} $R0 $validPath_path $validPath_badChars
    Exch $R0
FunctionEnd
!macro fnc_ValidPath VAR PATH INCLUDE_SPACE
    Push `${PATH}`
    Push `${INCLUDE_SPACE}`
    call fnc_ValidPath
    Pop `${VAR}`
!macroend
!define fnc_ValidPath "!insertmacro fnc_ValidPath"


# Returns DEFAULT if it contains none of $BAD_CHARS (or space if INCLUDE_SPACE = 1) otherwise returns FALLBACK
Var defaultPath_default
Var defaultPath_fallback
Var defaultPath_includeSpace

Function fnc_DefaultPath
    Pop $defaultPath_includeSpace
    Pop $defaultPath_fallback
    Pop $defaultPath_default
    Push $R0
    ${fnc_ValidPath} $R0 $defaultPath_default $defaultPath_includeSpace
    ${If} $R0 == 0
        Push $defaultPath_default
    ${Else}
        Push $defaultPath_fallback
    ${EndIf}
    Exch
    Pop $R0
FunctionEnd
!macro fnc_DefaultPath VAR DEFAULT FALLBACK INCLUDE_SPACE
    Push `${DEFAULT}`
    Push `${FALLBACK}`
    Push `${INCLUDE_SPACE}`
    call fnc_DefaultPath
    Pop `${VAR}`
!macroend
!define fnc_DefaultPath "!insertmacro fnc_DefaultPath"


# Elevate the installer from user to admin priveleges, passing along some values
# from one process to the other via a temporarily created file.
!macro ELEVATE_PROCESS

# Hand off data from the user account to the admin account using the HKCU
# (Current User) registry
${IfNot} ${UAC_IsAdmin}
    FileOpen $R0 ${USER_ADMIN_HANDOFF_FILE} w
    ${fnc_DefaultPath} $s_DefaultSdkPath "${DEFAULT_SDK_PATH}" "${FALLBACK_SDK_PATH}" 1
    FileWriteUTF16LE $R0 $s_DefaultSdkPath
    FileWriteUTF16LE $R0 "$\r$\n"
    FileWriteUTF16LE $R0 ${ANDROID_USER_SETTINGS}
    FileClose $R0
    SetFileAttributes ${USER_ADMIN_HANDOFF_FILE} HIDDEN
${Else}
    FileOpen $R0 ${USER_ADMIN_HANDOFF_FILE} r
    FileReadUTF16LE $R0 $s_DefaultSdkPath
    FileReadUTF16LE $R0 $s_UserSettingsPath
    FileClose $R0
    Delete ${USER_ADMIN_HANDOFF_FILE}

    ${If} $s_DefaultSdkPath != ""
        # FileReadUTF16LE includes the newline (\r\n) in the read, so remove it.
        StrCpy $s_DefaultSdkPath $s_DefaultSdkPath -2
    ${EndIf}

    # Fail-safe in case values couldn't get read in for some reason
    ${If} $s_DefaultSdkPath == ""
        ${fnc_DefaultPath} $s_DefaultSdkPath "${DEFAULT_SDK_PATH}" "${FALLBACK_SDK_PATH}" 1
    ${EndIf}
    ${If} $s_UserSettingsPath == ""
        StrCpy $s_UserSettingsPath ${ANDROID_USER_SETTINGS}
    ${Endif}
${EndIf}

uac_tryagain:
!insertmacro UAC_RunElevated # See UAC.nsh for documentation on return values
${Switch} $0
${Case} 0
    ${If} $1 = 1
        # We are the user script, and we successfully elevated ourselves to the
        # admin installer, which did all the real work. (This case is hit after
        # the admin installer has finished)
        Quit
    ${ElseIf} $3 <> 0
        # We are already admin - we must be the spawned, elevated process. Continue!
        Goto uac_success
    ${ElseIf} $1 = 3
        # Somehow the user simply chose another non-admin account. Try again?
        MessageBox MB_YESNO|MB_ICONEXCLAMATION "You must choose an account with administrative privileges. Try again?" \
            /SD IDNO IDYES uac_tryagain IDNO 0
    ${EndIf}
    ${Break}
    # If here, something went wrong. Fall to the next case to show the abort message.
${Case} 1223
    MessageBox MB_ICONSTOP "The installer could not request administrative privileges and must abort."
    ${Break}
${Case} 1062
    MessageBox MB_ICONSTOP "Logon service not running, which is needed to request administrative privileges, and the installer must abort."
    ${Break}
${Default}
    MessageBox MB_ICONSTOP "Unable to elevate [error: $0]"
    ${Break}
${EndSwitch}

# Normally, we delete the handoff file right after being elevated to
# admin mode. However, if the user can't elevate to admin mode for some
# reason, we still need to clean this up.
Delete ${USER_ADMIN_HANDOFF_FILE}
Quit

uac_success:
Delete ${USER_ADMIN_HANDOFF_FILE}
SetShellVarContext all

!macroend

# Installer functions
Function .onInit

!ifdef UNINSTALL_BUILDER
    System::Call "kernel32::GetCurrentDirectory(i ${NSIS_MAX_STRLEN}, t .r0)"
    CreateDirectory "$0\uninstaller"
    WriteUninstaller "$0\uninstaller\uninstall.exe"
    MessageBox MB_OK "Uninstaller was built in $0\uninstaller" /SD IDOK
    ExecShell "open" "$0\uninstaller"
    Quit
!endif

    !insertmacro STOP_STUDIO_PROCESS

    !insertmacro ELEVATE_PROCESS

    InitPluginsDir
    # A useful property of PLUGINSDIR is that it's a temp directory that is automatically deleted
    # after the installer runs. We'll load additional dependencies into there.
    SetOutPath $PLUGINSDIR

    ${If} ${RunningX64}
        SetRegView 64
    ${EndIf}

    Call s_QuerySystemMemory
    ${If} $s_SystemMemoryGB < 1
        !insertmacro EXCLUDE_SECTION ${SectionHaxm}
    ${EndIf}

    !ifndef DRY_RUN
        !insertmacro PREPARE_HAXM_INSTALLER

        !insertmacro CAN_INSTALL_HAXM $R0
        ${If} $R0 == 0
            !insertmacro EXCLUDE_SECTION ${SectionHaxm}
        ${Endif}

    !endif # DRY_RUN

    ${fnc_DefaultPath} $s_StudioPath "${DEFAULT_STUDIO_PATH}" "${FALLBACK_STUDIO_PATH}" 0
    !ifdef BUNDLE_SDK
        StrCpy $s_SdkPath $s_DefaultSdkPath
    !endif # BUNDLE_SDK

    # HACK: NSIS always defaults to "Program Files (x86)". However, we provide
    # both 32-bit and 64-bit .exes, so we always want to install into
    # "Program Files" regardless of OS type.
    # See also: http://sourceforge.net/p/nsis/bugs/843/
    ${If} ${RunningX64}
      # "C:\Program Files (x86)\blah" -> "C:\Program Files\blah"
      ${StrRep} $s_StudioPath $s_StudioPath $PROGRAMFILES $PROGRAMFILES64
    ${EndIf}

FunctionEnd

# Uninstaller functions
Function un.onInit

    !insertmacro STOP_STUDIO_PROCESS

    InitPluginsDir # See onInit for why we use PLUGINSDIR as a temp directory
    SetOutPath $PLUGINSDIR

    ${If} ${RunningX64}
        SetRegView 64
    ${EndIf}

    StrCpy $s_StudioPath $INSTDIR # $INSTDIR is set to cwd
    ReadRegStr $s_SdkPath HKLM "${REGKEY}" SdkPath
    ReadRegStr $s_InstallSdk HKLM "${REGKEY}" InstallSdk
    ReadRegStr $s_InstallHaxm HKLM "${REGKEY}" InstallHaxm
    ReadRegStr $s_UserSettingsPath HKLM "${REGKEY}" UserSettingsPath

    # If we weren't the ones who installed the SDK in the first place, don't
    # recommend uninstalling it by default
    ${If} $s_InstallSdk != 1
        ${UnselectSection} ${UNSectionSdk}
    ${Endif}

    # If we weren't the ones who installed HAXM in the first place, don't
    # recommend uninstalling it by default.
    ${If} $s_InstallHaxm != 1
        ${UnselectSection} ${UNSectionHaxm}
    ${EndIf}

    # Don't uninstall user settings by default
    ${UnselectSection} ${UNSectionUserSettings}

    !ifndef DRY_RUN
        # Exclude sections we can't or don't need to uninstall

        IfFileExists $s_SdkPath skip_exclude_sdk_section 0
            !insertmacro EXCLUDE_SECTION ${UNSectionSdk}
        skip_exclude_sdk_section:

        IfFileExists $s_UserSettingsPath skip_exclude_settings_section 0
            !insertmacro EXCLUDE_SECTION ${UNSectionUserSettings}
        skip_exclude_settings_section:

        !insertmacro PREPARE_HAXM_INSTALLER
        !insertmacro IS_HAXM_INSTALLED $R0
        ${If} $R0 == 0
            !insertmacro EXCLUDE_SECTION ${UNSectionHaxm}
        ${Endif}
    !endif # DRY_RUN

    IfSilent 0 skip_disable_sections
        # The silent uninstaller is called by the installer when we want to
        # replace an existing version of Android Studio by removing it. So, only
        # remove Android Studio and nothing else!
        ${UnselectSection} ${UNSectionSdk}
        ${UnselectSection} ${UNSectionHaxm}
        ${UnselectSection} ${UNSectionUserSettings}
    skip_disable_sections:

    !insertmacro MUI_STARTMENU_GETFOLDER Application $s_StartMenuGroup
FunctionEnd

# Ask the system for how much memory it has available. When finished,
# the variables $s_SystemMemoryMB and $s_SystemMemoryGB will be initialized
Function s_QuerySystemMemory
    # We will need to use variables $0 through $9, so save away any existing values.
    Push $0
    Push $1
    Push $2
    Push $3
    Push $4
    Push $5
    Push $6
    Push $7
    Push $8
    Push $9

    # Call GlobalMemoryStatusEx using the System plugin.
    # For more info: http://nsis.sourceforge.net/Docs/System/System.html
    System::Alloc 64
    Pop $0
    # GlobalMemoryStatusEx requires the first parameter to be the size
    # of the whole structure. Everything else is zeroed out.
    System::Call "*$0(i 64, i 0, l 0, l 0, l 0, l 0, l 0, l 0, l 0)"
    System::Call "Kernel32::GlobalMemoryStatusEx(i r0)" # Read status into $0
    System::Call "*$0(i.r1, i.r2, l.r3, l.r4, l.r5, l.r6, l.r7, l.r8, l.r9)"
    # Machine's physical memory value (in bytes) is the third parameter. See MSDN:
    # http://msdn.microsoft.com/en-us/library/windows/desktop/aa366770(v=vs.85).aspx
    System::Int64Op $3 / 1048576 # 1024 * 1024
    Pop $s_SystemMemoryMB
    System::Int64Op $s_SystemMemoryMB / 1024
    Pop $s_SystemMemoryGB
    System::Free $0

    # Restore old values
    Pop $9
    Pop $8
    Pop $7
    Pop $6
    Pop $5
    Pop $4
    Pop $3
    Pop $2
    Pop $1
    Pop $0

FunctionEnd

# Function invoked by MUI_FINISHPAGE_RUN
Function s_StartApp
    !ifndef DRY_RUN

    # ExecShell sets the CWD using $OUTDIR, which in this context is the
    # StartMenu>Shortcuts folder.
    # We change it to where studio.exe is actually located
    # for this invocation and then restore it.

    Push $OUTDIR
    SetOutPath $s_StudioPath
    ${If} ${RunningX64}
        StrCpy $R0 "$s_StudioPath\bin\${EXE_NAME_64}"
    ${Else}
        StrCpy $R0 "$s_StudioPath\bin\${EXE_NAME_32}"
    ${EndIf}
    !insertmacro UAC_AsUser_ExecShell "" "$R0" "" "" ""
    Pop $OUTDIR

    !endif # DRY_RUN
FunctionEnd

# Check whether directory is empty: use FindFirst/FindNext,
# skipping the "." and ".." directories.
# Return 1 (true) if empty and 0 (false) if not empty.
Function s___isEmptyDir
    Exch $0                         # $0 = Get dir
    Push $1
    Push $2
    StrCpy $2 1                     # result is 1/true by default (empty)

    FindFirst  $0 $1 "$0\*.*"       # $0 <= find handle, $1 <= first match,
    ${DoWhile} $1 != ""             # Find returns "" when done
        ${If}    $1 != "."
        ${AndIf} $1 != ".."
            StrCpy $2 0             # result is false (not empty)
            ${Break}
        ${Endif}
        FindNext $0 $1
    ${Loop}

    ClearErrors
    FindClose $0
    StrCpy $0 $2                    # $0 is result (0 or 1)
    Pop  $2
    Pop  $1
    Exch $0
FunctionEnd

!macro s__IsEmptyDir VAR DIR
    Push ${DIR}
    call s___isEmptyDir
    Pop ${VAR}
!macroend

!define s_IsEmptyDir "!insertmacro s__IsEmptyDir"

!macro ConfirmUninstallPath PATH OUT_VAR
    MessageBox MB_YESNO|MB_ICONEXCLAMATION \
    "This will remove ALL of the program files under$\r$\n\
    $\r$\n\
    ${PATH}$\r$\n\
    $\r$\n\
    and cannot be undone. Are you sure you wish to continue?" \
    /SD IDYES IDYES yes IDNO no

    yes:
        StrCpy ${OUT_VAR} 1
        Goto done
    no:
        StrCpy ${OUT_VAR} 0
        Goto done

    done:
!macroend

!define ConfirmUninstallPath '!insertmacro ConfirmUninstallPath'

Function fnc_UninstallPreviousPage_ShowIfNecessary
    !ifndef DRY_RUN

    # Offer to remove a currently installed version of Studio if found
    ReadRegStr $R0 HKLM "${REGKEY}" Path
    ${If} $R0 != ""
        StrCpy $R1 "$R0\uninstall.exe"
        IfFileExists $R1 show_uninstall_page 0
    ${Endif}

    Abort # Skip uninstall page

    !else
        StrCpy $R0 "C:\Dummy Path\Android Studio"
    !endif # DRY_RUN

    show_uninstall_page:
    Push $R0
    Call fnc_UninstallPreviousPage_SetTargetPath
    Call fnc_UninstallPreviousPage_Show

FunctionEnd

Function func_UninstallPreviousPage_Leave
    !ifndef DRY_RUN

    ${NSD_GetState} $hCtl_UninstallPreviousPage_CheckUninstall $R0
    ${If} $R0 == ${BST_CHECKED}
        ${NSD_GetText} $hCtl_UninstallPreviousPage_TextPath $R1

        ${ConfirmUninstallPath} $R1 $R2

        ${If} $R2 == 0
            Abort
        ${Endif}

        StrCpy $R3 "$R1\uninstall.exe"
        # _?=... causes the uninstaller to run directly instead of in the
        # background. If we don't do this, ExecWait won't work.
        ExecWait '"$R3" /S _?=$R1'
        Delete $R3
        RmDir $R1 # Should be empty after the uninstaller is removed
    ${EndIf}

    !endif
FunctionEnd

Function fnc_AndroidSdkPage_ShowIfNecessary
    !ifndef BUNDLE_SDK
        Abort # Skip this page if this installer doesn't bundle an SDK
    !endif

    # Skip this page if the user is installing the latest SDK
    ${If} $s_InstallSdk != 0
        Abort
    ${EndIf}

    Call fnc_AndroidSdkPage_Show
FunctionEnd

Function fnc_AndroidSdkPage_InitializeControls # Called in AndroidSdkPage.nsdinc
    IfFileExists $s_SdkPath 0 skip_set_sdk_text
    ${NSD_SetText} $hCtl_AndroidSdkPage_DirRequestUseExisting_Txt $s_SdkPath

    skip_set_sdk_text:
FunctionEnd

# Test if a target directory is an Android SDK. If it is, OUT_VAR will be set to
# 1, else 0.
!macro IS_SDK SDK_DIR OUT_VAR
    StrCpy ${OUT_VAR} 0
    IfFileExists "${SDK_DIR}\tools\android.bat" valid_sdk invalid_sdk
    valid_sdk:
        StrCpy ${OUT_VAR} 1
    invalid_sdk:
!macroend

!define IS_SDK "!insertmacro IS_SDK"

Function fnc_AndroidSdkPage_Leave
    ${NSD_GetState} $hCtl_AndroidSdkPage_RadioInstallLatest $R0
    ${If} $R0 == ${BST_CHECKED}
        StrCpy $s_InstallSdk 1
    ${Else}
        StrCpy $s_InstallSdk 0
        ${NSD_GetText} $hCtl_AndroidSdkPage_DirRequestUseExisting_Txt $R0

        ${IS_SDK} $R0 $R1 # $R0 is the directory we're testing. $R1 will be set to 1 if it's vaild.
        ${If} $R1 == 0
            MessageBox MB_OK|MB_ICONEXCLAMATION "Sorry, the selected SDK path is invalid." /SD IDOK
            Abort
        ${Endif}
        StrCpy $s_SdkPath $R0
    ${EndIf}
FunctionEnd

# Given an input path, return a path that's non-empty, by appending numbers to the end.
# The path will be returned as-is if it is already empty.
#
# To use:
#   Push $pathIn
#   Call s_computeEmptyDir
#   Pop $pathOut # You can use $pathIn here again to overwrite it
Function s___computeEmptyDir
    Exch $0 # Get directory to check
    Push $1 # s_IsEmptyDir return value

    # Check whether that install directory is empty. If not, compute a new value.
    ${s_IsEmptyDir} $1 $0
    ${If} $1 == 0
        Push $R0 # Integer value which we'll append to DIR, like DIR1, DIR2, etc...
        Push $R1 # Copy of $0 (original path) plus a numeric value
        StrCpy $R0 0
        ${Do}
            IntOp $R0 $R0 + 1
            StrCpy $R1 "$0$R0"
            ${s_IsEmptyDir} $1 $R1
        ${LoopUntil} $1 != 0

        StrCpy $0 $R1
        Pop $R1
        Pop $R0
    ${Endif}

    Pop $1
    Exch $0 # Put output directory on top of the stack
FunctionEnd

!macro s__computeEmptyDir PATH
    Push ${PATH}
    Call s___computeEmptyDir
    Pop ${PATH}
!macroend

!define s_computeEmptyDir "!insertmacro s__computeEmptyDir"


# Called when entering the Components page.
Function s_ComponentsPagePre
!ifdef BUNDLE_SDK
    # Restore the SDK section selection if the user previously expressed
    # interest in installing it (and are now coming back to this page).
    # See also s_ComponentsPageLeave, which sets the flag.
    ${If} $s_InstallSdk != 0
        ${SelectSection} ${SectionSdk}
    ${Else}
        ${UnselectSection} ${SectionSdk}
    ${EndIf}
!endif # BUNDLE_SDK
FunctionEnd


# Called when leaving the Components page.
Function s_ComponentsPageLeave

!ifdef BUNDLE_SDK
    # Minor hack alert: We ALWAYS want to install this installer's SDK, either
    # to the directory the user specified, or to a temporary location that
    # Android Studio's first run experience can make use of. Therefore, even
    # if a user says they don't want to install the SDK, we have to switch the
    # flag on behind their back anyway. The $s_InstallSdk flag will modify the
    # behaviour of the SDK section.
    SectionGetFlags ${SectionSdk} $R0
    IntOp $s_InstallSdk $R0 & ${SF_SELECTED}

    ${SelectSection} ${SectionSdk}
!else
    StrCpy $s_InstallSdk 0
!endif # BUNDLE_SDK

    SectionGetFlags ${SectionHaxm} $R0
    IntOp $s_InstallHaxm  $R0 & ${SF_SELECTED}

FunctionEnd

# Called when entering the Android License page.
Function s_AndroidLicensePagePre
    !ifndef BUNDLE_SDK
        Abort
    !endif # BUNDLE_SDK

    ${If} $s_SkipAndroidLicense == 1
        Abort
    ${Endif}
FunctionEnd

# Called when leaving the Android License page.
Function s_AndroidLicensePageLeave
    StrCpy $s_SkipAndroidLicense 1
FunctionEnd

# Called when entering the Intel License page.
Function s_IntelLicensePagePre
    ${If} $s_SkipIntelLicense == 1
        Abort
    ${Endif}

    # No need to show the Intel license if we're not installing HAXM
    SectionGetFlags ${SectionHaxm} $R0
    IntOp $R0  $R0 & ${SF_SELECTED}
    ${If} $R0 == 0
        Abort
    ${EndIf}
FunctionEnd

# Called when leaving the Intel License page.
Function s_IntelLicensePageLeave
    StrCpy $s_SkipIntelLicense 1
FunctionEnd

Function fnc_InstallDirsPage_InitializeControls # Called in InstallDirsPage.nsdinc
    ${s_computeEmptyDir} $s_StudioPath
    ${NSD_SetText} $hCtl_InstallDirsPage_DirRequestStudio_Txt $s_StudioPath

    ${If} $s_InstallSdk != 0
        ${s_computeEmptyDir} $s_SdkPath
        ${NSD_SetText} $hCtl_InstallDirsPage_DirRequestSdk_Txt $s_SdkPath
    ${Else}
        ShowWindow $hCtl_InstallDirsPage_GroupSdk ${SW_HIDE}
        ShowWindow $hCtl_InstallDirsPage_LabelSdk ${SW_HIDE}
        ShowWindow $hCtl_InstallDirsPage_DirRequestSdk_Txt ${SW_HIDE}
        ShowWindow $hCtl_InstallDirsPage_DirRequestSdk_Btn ${SW_HIDE}
    ${EndIf}
FunctionEnd

Function fnc_InstallDirsPage_Leave
    ${NSD_GetText} $hCtl_InstallDirsPage_DirRequestStudio_Txt $s_StudioPath
    ${s_IsEmptyDir} $0 $s_StudioPath
    ${If} $0 == 0
        MessageBox MB_OK|MB_ICONEXCLAMATION "Please select an empty folder to install Android Studio." /SD IDOK
        Abort
    ${Endif}

    ${fnc_ValidPath} $0 $s_StudioPath 0
    ${If} $0 != 0
        MessageBox MB_OK|MB_ICONEXCLAMATION 'The install path must not contain any of ${BAD_CHARS}.' /SD IDOK
        Abort
    ${Endif}

    ${If} $s_InstallSdk != 0
        ${NSD_GetText} $hCtl_InstallDirsPage_DirRequestSdk_Txt $s_SdkPath

        ${fnc_ValidPath} $0 $s_SdkPath 1
        ${If} $0 != 0
            MessageBox MB_OK|MB_ICONEXCLAMATION 'In order for the SDK tools to work properly, the path must not \
            contain any of ${BAD_CHARS} or space.' /SD IDOK
            Abort
        ${Endif}

        ${IS_SDK} $s_SdkPath $R1 # $R1 will be set to 1 if an SDK is already there
        ${If} $R1 == 1
            StrCpy $s_InstallSdk 0
            Goto endOfBlock
        ${Endif}

        ${s_IsEmptyDir} $0 $s_SdkPath
        ${If} $0 == 0
            MessageBox MB_OK|MB_ICONEXCLAMATION "Please select an empty folder to install the Android SDK." /SD IDOK
            Abort
        ${Endif}

        ${StrContains} $R1 $s_SdkPath $s_StudioPath
        ${If} $R1 != 0
            MessageBox MB_OK|MB_ICONEXCLAMATION \
            "You are attempting to install the Android SDK inside your Android Studio installation.$\r$\n \
            $\r$\n \
            Updates to Android Studio will not work in this configuration. Please select a valid installation path."

            Abort
        ${Endif}

        # Make sure the user didn't put their SDK under Program Files
        StrCpy $R0 $PROGRAMFILES
        ${If} ${RunningX64}
            StrCpy $R0 $PROGRAMFILES64
        ${EndIf}

        ${StrContains} $R1 $s_SdkPath $R0
        ${If} $R1 != 0
            MessageBox MB_YESNO|MB_ICONQUESTION \
            "Android Studio periodically updates your SDK to make sure your files are up to date. \
            To avoid getting errors when updating, we recommend installing your SDK into a user directory with read/write access.$\r$\n \
            $\r$\n \
            Do you want to update the SDK installation path?" IDYES updateSdkPath IDNO ignoreWarning
            updateSdkPath:
                StrCpy $s_SdkPath $s_DefaultSdkPath
                ${NSD_SetText} $hCtl_InstallDirsPage_DirRequestSdk_Txt $s_SdkPath
                Abort
            ignoreWarning:
        ${Endif}

        endOfBlock:
    ${EndIf}

FunctionEnd

Function fnc_HaxmPage_ShowIfNecessary

    # Skip this page if the user deselected the HAXM component
    SectionGetFlags ${SectionHaxm} $R0
    IntOp $R0  $R0 & ${SF_SELECTED}
    ${If} $R0 == 0
        Abort
    ${EndIf}

    Call fnc_HaxmPage_Show
FunctionEnd

Function fnc_HaxmPage_InitializeControls # Called in HaxmPage.nsdinc
    Push $s_SystemMemoryMB
    Call fnc_HaxmPage_SetSystemMemory
FunctionEnd

# Called when leaving the "Confirm Uninstall" page
Function un.s_ConfirmPageLeave
    ${ConfirmUninstallPath} $s_StudioPath $R0

    ${If} $R0 == 0
        Abort
    ${Endif}
FunctionEnd
