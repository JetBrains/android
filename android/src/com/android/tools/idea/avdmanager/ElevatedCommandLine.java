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
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShellAPI;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.ptr.IntByReference;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.List;

/**
 * Special version of {@link GeneralCommandLine} that will execute the command with
 * elevated privileges on Windows.<br>
 * This is useful for installation commands which require administrator privileges
 * on Windows version 7 and above.
 */
public class ElevatedCommandLine extends GeneralCommandLine {
  private static final int SEE_MASK_NO_CLOSE_PROCESS = 0x00000040;
  private static final int INFINITE = -1;
  private String myTempFilePrefix;

  public ElevatedCommandLine(@NotNull String... command) {
    super(command);
    myTempFilePrefix = "temp";
  }

  public ElevatedCommandLine withTempFilePrefix(@NotNull String tempFilePrefix) {
    myTempFilePrefix = tempFilePrefix;
    return this;
  }

  @Override
  @NotNull
  protected Process startProcess(@NotNull List<String> commands) throws IOException {
    if (SystemInfo.isWindows) {
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
    File wrapper = FileUtil.createTempFile(FileUtil.getNameWithoutExtension(exeName) + "_wrapper", ".bat", true);
    String exePath = new File(getExePath()).getParent();
    FileUtil.writeToFile(wrapper, String.format(
      "@echo off\n" +
      "setlocal enableextensions\n\n" +
      "cd /d \"%1$s\"\n\n" +
      "%2$s %%*", exePath, exeName));
    setExePath(wrapper.getPath());

    // Setup capturing of stdout and stderr in files.
    // ShellExecuteEx does not allow for the capture from code.
    File outFile = FileUtil.createTempFile(myTempFilePrefix + "_out", ".txt", true);
    File errFile = FileUtil.createTempFile(myTempFilePrefix + "_err", ".txt", true);
    addParameters(">", outFile.getPath(), "2>", errFile.getPath());

    ShellAPI.SHELLEXECUTEINFO info = new ShellAPI.SHELLEXECUTEINFO();
    info.cbSize = info.size();
    info.lpFile = getExePath();
    info.lpVerb = "runas";
    info.lpParameters = getParametersList().getParametersString();
    info.lpDirectory = getWorkDirectory().getPath();
    info.nShow = WinUser.SW_HIDE;
    info.fMask = SEE_MASK_NO_CLOSE_PROCESS;
    boolean returnValue = Shell32.INSTANCE.ShellExecuteEx(info);
    int errorCode = returnValue ? 0 : Kernel32.INSTANCE.GetLastError();

    // Return a fake Process which will wait for the created process to finish
    // and wrap stdout and stderr into their respective {@link InputStream}.
    return new ProcessWrapper(info.hProcess, errorCode, outFile, errFile);
  }

  /**
   * A fake Process which will wait for the created process to finish
   * and wrap stdout and stderr into their respective {@link InputStream}.
   */
  private static class ProcessWrapper extends Process {
    private HANDLE myProcess;
    private final IntByReference myExitCode;
    private final File myOutFile;
    private final File myErrFile;

    private ProcessWrapper(@NotNull HANDLE hProcess, int errorCode, @NotNull File outFile, @NotNull File errFile) {
      myProcess = hProcess;
      myExitCode = new IntByReference(errorCode);
      myOutFile = outFile;
      myErrFile = errFile;
    }

    @Override
    public OutputStream getOutputStream() {
      throw new RuntimeException("Unexpected behaviour");
    }

    @Override
    public InputStream getInputStream() {
      return toInputStream(myOutFile);
    }

    @Override
    public InputStream getErrorStream() {
      return toInputStream(myErrFile);
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
  }
}
