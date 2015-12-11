/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.ShellAPI;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinReg.HKEY;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Arrays;
import java.util.List;

/**
 * Special version of {@link GeneralCommandLine} that will execute the command with
 * elevated privileges on Windows.<br/>
 * This is useful for installation commands which require administrator privileges
 * on Windows version 7 and above.
 */
public class ElevatedCommandLine extends GeneralCommandLine {
  private static final int INFINITE = -1;

  public ElevatedCommandLine(@NotNull String... command) {
    super(command);
  }

  @Override
  @NotNull
  protected Process startProcess(@NotNull List<String> commands) throws IOException {
    if (SystemInfo.isWin7OrNewer) {
      return executeAsShellCommand();
    }
    else {
      return super.startProcess(commands);
    }
  }

  /**
   * On Windows we will execute this command as a shell command.
   * This allows us to specify elevated privileges with the "runas" parameter.
   */
  private Process executeAsShellCommand() throws IOException {
    // First create a wrapper that sets the current work directory, such that batch files
    // may call other batch/executable files in the same directory without specifying the
    // directory.
    // Note: This was needed for the Haxm silent_install.bat.
    String exeName = new File(getExePath()).getName();
    File wrapper = FileUtil.createTempFile(getWorkDirectory(), FileUtil.getNameWithoutExtension(exeName) + "_wrapper", ".bat", true);
    FileUtil.writeToFile(wrapper, String.format(
      "@echo off\n" +
      "setlocal enableextensions\n\n" +
      "cd /d \"%%~dp0\"\n\n" +
      "%1$s %%*", exeName));
    setExePath(wrapper.getPath());

    // Setup capturing of stdout and stderr in files.
    // ShellExecuteEx does not allow for the capture from code.
    final File outFile = FileUtil.createTempFile("haxm_out", ".txt", true);
    final File errFile = FileUtil.createTempFile("haxm_err", ".txt", true);
    addParameters(">", outFile.getPath(), "2>", errFile.getPath());

    final ShellExecuteInfo info = new ShellExecuteInfo(this);
    BOOL returnValue = Shell32Ex.INSTANCE.ShellExecuteEx(info);
    final int errorCode = returnValue.booleanValue() ? 0 : Kernel32.INSTANCE.GetLastError();

    // Return a fake Process which will wait for the created process to finish
    // and wrap stdout and stderr into their respective {@link InputStream}.
    return new Process() {
      private HANDLE myProcess = info.hProcess;
      private IntByReference myExitCode = new IntByReference(errorCode);

      @Override
      public OutputStream getOutputStream() {
        throw new RuntimeException("Unexpected behaviour");
      }

      @Override
      public InputStream getInputStream() {
        return toInputStream(outFile);
      }

      @Override
      public InputStream getErrorStream() {
        return toInputStream(errFile);
      }

      @Override
      public int waitFor() {
        if (myProcess != null) {
          Kernel32.INSTANCE.WaitForSingleObject(myProcess, INFINITE);
          Kernel32.INSTANCE.GetExitCodeProcess(myProcess, myExitCode);
          Kernel32.INSTANCE.CloseHandle(myProcess);
          myProcess = null;
        }
        return myExitCode.getValue();
      }

      @Override
      public int exitValue() {
        return waitFor();
      }

      @Override
      public void destroy() {
        waitFor();
      }

      private InputStream toInputStream(@NotNull File file) {
        try {
          waitFor();
          return new FileInputStream(file);
        }
        catch (FileNotFoundException e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  // Gory details of the structure passed to ShellExecuteEx.
  // Note: we cannot use ShellExecute since it does not give access to the process handle.
  @SuppressWarnings("unused")
  public static class ShellExecuteInfo extends Structure {
    public DWORD     cbSize;
    public ULONG     fMask;
    public HWND      hwnd;
    public WString   lpVerb;
    public WString   lpFile;
    public WString   lpParameters;
    public WString   lpDirectory;
    public int       nShow;
    public HINSTANCE hInstApp;
    public Pointer   lpIDList;
    public WString   lpClass;
    public HKEY      hKeyClass;
    public DWORD     dwHotKey;
    public HANDLE    hMonitor;
    public HANDLE    hProcess;

    private static final int SW_HIDE = 0;
    private static final int SEE_MASK_NO_CLOSE_PROCESS = 0x00000040;

    public ShellExecuteInfo(@NotNull GeneralCommandLine commandLine) {
      cbSize = new DWORD(size());
      lpFile = new WString(commandLine.getExePath());
      lpVerb = new WString("runas");
      lpParameters = new WString(commandLine.getParametersList().getParametersString());
      lpDirectory = new WString(commandLine.getWorkDirectory().getPath());
      nShow = SW_HIDE;
      fMask = new ULONG(SEE_MASK_NO_CLOSE_PROCESS);
    }

    @Override
    protected List getFieldOrder() {
      return Arrays.asList("cbSize", "fMask", "hwnd", "lpVerb", "lpFile", "lpParameters", "lpDirectory", "nShow", "hInstApp", "lpIDList",
                           "lpClass", "hKeyClass", "dwHotKey", "hMonitor", "hProcess");
    }
  }

  private interface Shell32Ex extends ShellAPI, StdCallLibrary {
    Shell32Ex INSTANCE = (Shell32Ex) Native.loadLibrary("shell32", Shell32Ex.class, W32APIOptions.UNICODE_OPTIONS);

    BOOL ShellExecuteEx(ShellExecuteInfo info);
  }
}
