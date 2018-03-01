/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.avdmanager;

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.intellij.rt.execution.testFrameworks.ProcessBuilder.isWindows;

public class MockAvdManagerConnection extends AvdManagerConnection {

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(MockAvdManagerConnection.class);
  }

  @NotNull private final AndroidSdkHandler mySdkHandler;

  public MockAvdManagerConnection(@NotNull AndroidSdkHandler handler) {
    super(handler);
    mySdkHandler = handler;
  }

  public static void inject() {
    setConnectionFactory(MockAvdManagerConnection::new);
  }

  @Override
  protected void addParameters(@NotNull AvdInfo info, @NotNull GeneralCommandLine commandLine) {
    super.addParameters(info, commandLine);
    commandLine.addParameters("-no-window");
  }

  @NotNull
  private File getAdbBinary() {
    return new File(mySdkHandler.getLocation(), FileUtil.join(SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER, SdkConstants.FN_ADB));
  }

  public boolean deleteAvdByDisplayName(@NotNull String avdName) {
    // We need to delete the AVD ID. We get it by converting spaces to underscores.
    return super.deleteAvd(avdName.replace(' ', '_'));
  }

  public void killEmulator() {
    AndroidDebugBridge adb = AndroidDebugBridge.createBridge(getAdbBinary().getAbsolutePath(), false);

    Collection<IDevice> emulatorDevices = new ArrayList<>();
    for (IDevice device : adb.getDevices()) {
      EmulatorConsole emulatorConsole = EmulatorConsole.getConsole(device);
      if (emulatorConsole != null) {
        emulatorConsole.kill();
        emulatorDevices.add(device);
      }
    }

    giveEmulatorsChanceToExit(emulatorDevices, adb);
    // Force kill remaining emulators that didn't exit
    killEmulatorProcesses();
    // Kill emulator crash report dialogs left behind
    killEmulatorCrashReportProcess();
  }

  public void killEmulatorProcesses() {
    // Note that pgrep matches up to 15 characters.
    exec(isWindows ? "taskkill /F /IM qemu*" : "pkill -9 qemu");
    killEmulatorCrashReportProcess();
  }

  private void giveEmulatorsChanceToExit(@NotNull Collection<IDevice> emulators, @NotNull AndroidDebugBridge adb) {
    try {
      Wait.seconds(30)
        .expecting("All emulators to have terminated gracefully")
        .until(() -> {
          List<IDevice> devices = Arrays.asList(adb.getDevices());
          for (IDevice device : emulators) {
            if (device == null || !device.isEmulator()) {
              continue;
            }

            if (devices.contains(device)) {
              return false;
            }
          }
          return true;
        });
    } catch (WaitTimedOutError timeout) {
      getLogger().warn("Emulators did not shut down gracefully");
    }
  }

  public void tapRunningAvd(int x, int y) {
    try {
      exec(getAdbBinary().getAbsolutePath() + " shell input tap " + x + " " + y);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void tapBackButtonOnRunningAvd() {
    try {
      exec(getAdbBinary().getAbsolutePath() + " shell input keyevent 4");
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void exec(@NotNull String cmd) {
    try {
      Runtime.getRuntime().exec(cmd).waitFor(10, TimeUnit.SECONDS);
    }  catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void killEmulatorCrashReportProcess() {
    exec(isWindows ? "taskkill /F /IM  emulator64-cra*" : "pkill emulator64-cra");
  }
}
