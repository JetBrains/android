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

import static com.intellij.rt.execution.testFrameworks.ProcessBuilder.isWindows;

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.EmulatorConsole;
import com.android.ddmlib.IDevice;
import com.android.sdklib.internal.avd.AvdInfo;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.emulatorcommand.EmulatorCommandBuilderFactory;
import com.google.common.util.concurrent.MoreExecutors;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MockAvdManagerConnection extends AvdManagerConnection {

  @NotNull
  private static Logger getLogger() {
    return Logger.getInstance(MockAvdManagerConnection.class);
  }

  @NotNull private final AndroidSdkHandler mySdkHandler;

  public MockAvdManagerConnection(@NotNull AndroidSdkHandler handler, @NotNull Path avdHomeFolder) {
    super(handler, avdHomeFolder, MoreExecutors.newDirectExecutorService());
    mySdkHandler = handler;
  }

  public static void inject() {
    setConnectionFactory(MockAvdManagerConnection::new);
  }

  @NotNull
  private String getAdbBinary() {
    return mySdkHandler.getLocation().resolve(SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER).resolve(SdkConstants.FN_ADB).toString();
  }

  public void killEmulator() {
    try {
      AndroidDebugBridge.initIfNeeded(false);
      AndroidDebugBridge adb = AndroidDebugBridge.createBridge(getAdbBinary(), false);

      Collection<IDevice> emulatorDevices = new ArrayList<>();
      assert adb != null;
      for (IDevice device : adb.getDevices()) {
        EmulatorConsole emulatorConsole = EmulatorConsole.getConsole(device);
        if (emulatorConsole != null) {
          emulatorConsole.kill();
          emulatorConsole.close();
          emulatorDevices.add(device);
        }
      }

      giveEmulatorsChanceToExit(emulatorDevices, adb);
      // Force kill remaining emulators that didn't exit
      killEmulatorProcesses();
    }
    finally {
      AndroidDebugBridge.terminate();
    }
  }

  public void killEmulatorProcesses() {
    // Note that pgrep matches up to 15 characters.
    exec(isWindows ? "taskkill /F /IM qemu*" : "pkill -9 qemu");
    // Kill emulator crash report dialogs left behind
    killEmulatorCrashReportProcess();
  }

  private void giveEmulatorsChanceToExit(@NotNull Collection<IDevice> emulators, @NotNull AndroidDebugBridge adb) {
    try {
      Wait.seconds(30)
        .expecting("All emulators to have terminated gracefully")
        .until(() -> {
          if (Thread.currentThread().isInterrupted()) {
            // Exit early. Continue with rest of cleanup
            return true;
          }

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
    }
    catch (WaitTimedOutError timeout) {
      getLogger().warn("Emulators did not shut down gracefully");
    }
  }

  public void tapRunningAvd(int x, int y) {
    try {
      exec(getAdbBinary() + " shell input tap " + x + " " + y);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void tapBackButtonOnRunningAvd() {
    try {
      exec(getAdbBinary() + " shell input keyevent 4");
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void exec(@NotNull String cmd) {
    try {
      Runtime.getRuntime().exec(cmd).waitFor(10, TimeUnit.SECONDS);
    }
    catch (InterruptedException interrupted) {
      // Continue keeping the thread interrupted. Don't block on anything to cleanup
      Thread.currentThread().interrupt();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void killEmulatorCrashReportProcess() {
    exec(isWindows ? "taskkill /F /IM  emulator64-cra*" : "pkill emulator64-cra");
  }

  @Override
  protected @NotNull GeneralCommandLine newEmulatorCommand(@Nullable Project project,
                                                           @NotNull Path emulator,
                                                           @NotNull AvdInfo avd,
                                                           @NotNull EmulatorCommandBuilderFactory factory) {
    GeneralCommandLine command = super.newEmulatorCommand(project, emulator, avd, factory);
    command.addParameter("-no-window");

    return command;
  }
}
