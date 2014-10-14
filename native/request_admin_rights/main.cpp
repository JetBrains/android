/*
* Copyright 2000-2009 JetBrains s.r.o.
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*
* NOTE: This source is derivate work based on the JetBrains VistaUpdatedLauncher code, which can
* be found here:
* https://github.com/JetBrains/intellij-community/tree/master/native/VistaUpdaterLauncher
*/

/**
 * The purpose of this program is to test the current working directory to see if the active Windows
 * account has privileges to write to it and, if not, show a UAC (User Account Control) prompt. This
 * allows us to call a program with admin privileges only if necessary.
 *
 * See showHelp() for usage.
 */

#include "targetver.h"

#include <windows.h>
#include <process.h>
#include <stdio.h>
#include <tchar.h>
#include <conio.h>
#include <string.h>
#include <stdlib.h>

static const int MAX_PATH_LEN = 32768;

// The following parameter is intended for internal use only - a user shouldn't pass it in!
// If elevation is required, request_admin_rights.exe works by calling itself recursively. The child
// process should then have admin rights, but in case something goes wrong, we want to make sure it
// doesn't recurse infinitely, so we add an explicit parameter to stop it from happening.
static const wchar_t* PARAM_SKIP_ELEVATION = L"--skip-elevation";

void showHelp() {
    wprintf(L"request_admin_rights usage:\n");
    wprintf(L"> cd some/target/path\n");
    wprintf(L"> request_admin_rights target-exe [arg1 arg2 arg3...]\n\n");
    wprintf(L"This will either run \"target-exe\" directly or ask the user for permissions to ");
    wprintf(L"run as admin if additional privileges are required.");
}

bool validateArgs(int argc, const _TCHAR* argv[]) {
    int finalArgCount = 0;
    for (int i = 1; i < argc; i++)
    {
        if (wcscmp(argv[i], PARAM_SKIP_ELEVATION) == 0) {
            continue;
        }

        finalArgCount++;
    }

    return (finalArgCount > 0);
}

bool shouldSkipElevation(int argc, const _TCHAR* argv[]) {
    for (int i = 0; i < argc; i++)
    {
        if (wcscmp(argv[i], PARAM_SKIP_ELEVATION) == 0) {
            return true;
        }
    }

    return false;
}

bool requiresElevation(const wchar_t* path)
{
    wchar_t fileName[MAX_PATH_LEN] = L"";
    wcscat_s(fileName, path);
    wcscat_s(fileName, L"\\.dir-is-writable-check");
    HANDLE file = CreateFile(fileName, GENERIC_READ|GENERIC_WRITE, 0, NULL, CREATE_NEW, 0, NULL);
    if (file == INVALID_HANDLE_VALUE)
    {
        DWORD error = GetLastError();
        if (error == ERROR_ACCESS_DENIED)
        {
            // looks like we need to request elevation
            return true;
        }
        else
        {
            // there's no need to request elevaion since patcher will most likely fail anyway
            fflush(stdout);
            return false;
        }
    }

    CloseHandle(file);
    DeleteFile(fileName);

    return false;
}

bool appendArgument(wchar_t result[], const wchar_t argument[])
{
    // Don't append the --skip_elevation param - we only needed it as a handoff when recursively
    // calling request_admin_rights.exe from request_admin_rights.exe, but we don't want to pass it on to the actual command
    if (wcscmp(argument, PARAM_SKIP_ELEVATION) == 0) {
        return false;
    }

    bool needsQuoting = wcschr(argument, L' ') != NULL;
    if (needsQuoting)
    {
        wcscat_s(result, MAX_PATH_LEN, L"\"");
    }
    wcscat_s(result, MAX_PATH_LEN, argument);
    if (needsQuoting)
    {
        wcscat_s(result, MAX_PATH_LEN, L"\"");
    }

    return true;
}

void appendArguments(wchar_t result[], int argc, const _TCHAR* argv[]) {
    for (int i = 0; i < argc; i++)
    {
        if (appendArgument(result, argv[i])) {
            wcscat_s(result, MAX_PATH_LEN, L" ");
        }
    }
}

int _tmain(int argc, const _TCHAR* argv[])
{
    if (!validateArgs(argc, argv)) {
        showHelp();
        return 0;
    }

    wchar_t elevationPath[MAX_PATH_LEN] = L"";
    _wgetcwd(elevationPath, MAX_PATH_LEN);

    HANDLE processHandle = NULL;
    if (!shouldSkipElevation(argc, argv) && requiresElevation(elevationPath))
    {
        // Prepare to call this program again in elevated mode.
        wchar_t originalParams[MAX_PATH_LEN] = L"";
        appendArguments(originalParams, argc - 1, &argv[1]); // Skip argv[0] which is this exe

        wchar_t modifiedParams[MAX_PATH_LEN] = L"";
        wcscat_s(modifiedParams, PARAM_SKIP_ELEVATION);
        wcscat_s(modifiedParams, L" ");
        wcscat_s(modifiedParams, originalParams);

        wprintf(L"Creating elevated process: %s %s\n", argv[0], originalParams);
        fflush(stdout);

        SHELLEXECUTEINFO shExecInfo;
        shExecInfo.cbSize = sizeof(SHELLEXECUTEINFO);
        shExecInfo.fMask = SEE_MASK_NOCLOSEPROCESS;
        shExecInfo.hwnd = NULL;
        shExecInfo.lpVerb = L"runas";
        shExecInfo.lpFile = argv[0];
        shExecInfo.lpParameters = modifiedParams;
        shExecInfo.lpDirectory = NULL;
        shExecInfo.nShow = SW_HIDE;
        shExecInfo.hInstApp = NULL;

        if (ShellExecuteEx(&shExecInfo) == FALSE)
        {
            wprintf(L"ShellExecuteEx() failed with error code %d\n", GetLastError());
            fflush(stdout);
            return -1;
        }
        processHandle = shExecInfo.hProcess;
    }
    else // Elevation not required
    {
        STARTUPINFO startupInfo = {0};
        startupInfo.cb = sizeof(startupInfo);
        PROCESS_INFORMATION processInformation = {0};

        startupInfo.hStdInput  = GetStdHandle(STD_INPUT_HANDLE);
        startupInfo.hStdOutput = GetStdHandle(STD_OUTPUT_HANDLE);
        startupInfo.hStdError  = GetStdHandle(STD_ERROR_HANDLE);

        wchar_t commandLine[MAX_PATH_LEN] = L"";
        appendArguments(commandLine, argc - 1, &argv[1]);

        wprintf(L"Creating new process: %s\n", commandLine);
        fflush(stdout);

        if (!CreateProcess(
            NULL, /*LPCTSTR lpApplicationName*/
            commandLine,/* LPTSTR lpCommandLine*/
            NULL, /*LPSECURITY_ATTRIBUTES lpProcessAttributes*/
            NULL, /*LPSECURITY_ATTRIBUTES lpThreadAttributes*/
            TRUE, /*BOOL bInheritHandles,*/
            0,    /*DWORD dwCreationFlags*/
            NULL, /*LPVOID lpEnvironment*/
            NULL, /*LPCTSTR lpCurrentDirectory*/
            &startupInfo, /*LPSTARTUPINFO lpStartupInfo*/
            &processInformation /*LPPROCESS_INFORMATION lpProcessInformation*/))
        {
            wprintf(L"Cannot create process: %d\n", GetLastError());
            return -1;
        }
        processHandle = processInformation.hProcess;
    }

    WaitForSingleObject(processHandle, INFINITE);

    DWORD exitCode = 0;
    if (!GetExitCodeProcess(processHandle, &exitCode))
    {
        wprintf(L"Cannot retrieve process exit code: %d\n", GetLastError());
        exitCode = -1;
    }
    CloseHandle(processHandle);

    return exitCode;
}

