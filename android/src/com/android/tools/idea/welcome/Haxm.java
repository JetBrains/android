/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.welcome;

import com.android.SdkConstants;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.wizard.DynamicWizardStep;
import com.android.tools.idea.wizard.ScopedStateStore;
import com.google.common.base.Joiner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Intel® HAXM installable component
 */
public final class Haxm extends InstallableComponent {
  // In UI we cannot use longs, so we need to pick a unit other then byte
  public static final Storage.Unit UI_UNITS = Storage.Unit.MiB;
  public static final Logger LOG = Logger.getInstance(Haxm.class);
  public static final String COMPONENT_VENDOR = "intel";
  public static final String COMPONENT_PATH = "Hardware_Accelerated_Execution_Manager";
  private static final ScopedStateStore.Key<Boolean> KEY_INSTALL_HAXM =
    ScopedStateStore.createKey("install.haxm", ScopedStateStore.Scope.PATH, Boolean.class);
  private static final ScopedStateStore.Key<Integer> KEY_EMULATOR_MEMORY_MB =
    ScopedStateStore.createKey("emulator.memory", ScopedStateStore.Scope.PATH, Integer.class);
  private static long memorySize = -1;
  private final ScopedStateStore.Key<Boolean> myIsCustomInstall;
  private ScopedStateStore myState;
  private ProgressStep myProgressStep;


  public Haxm(ScopedStateStore.Key<Boolean> isCustomInstall) {
    super("SDK Emulator Extra - Intel® HAXM", 2306867, KEY_INSTALL_HAXM);
    myIsCustomInstall = isCustomInstall;
  }

  public static boolean canRun() {
    // TODO HAXM is disabled as headless install is not in the repositories yet
    if (Boolean.getBoolean("install.haxm") && (SystemInfo.isWindows || SystemInfo.isMac)) {
      return getMemorySize() >= Storage.Unit.GiB.getNumberOfBytes();
    }
    else {
      return false;
    }
  }

  @NotNull
  private static GeneralCommandLine getMacHaxmInstallCommandLine(File source, int memorySize) {
    String shellScript = getAbsolutePathString(source, "silent_install.sh");
    String diskImage = getAbsolutePathString(source, "IntelHAXM_1.1.0_below_10.10.dmg");
    // Explicitly calling bash so we don't have to deal with permissions on the shell script file.
    // Note that bash is preinstalled on Mac OS X so we can rely on it.
    String[] installerInvocation = {"/bin/bash", shellScript, "-f", diskImage, "-m", String.valueOf(memorySize)};

    // We can't use 'sudo' here as this requires a terminal. So Apple Script is used to run shell script as admin
    String appleScript = String.format("do shell script \"%s\" with administrator privileges", Joiner.on(" ").join(installerInvocation));
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath("/usr/bin/osascript");
    commandLine.setWorkDirectory(source);
    commandLine.addParameters("-e", appleScript);
    return commandLine;
  }

  private static String getAbsolutePathString(File source, String filename) {
    return "'" + new File(source, filename).getAbsolutePath() + "'";
  }

  @NotNull
  private static GeneralCommandLine getWindowsHaxmInstallCommandLine(File source, int memorySize) {
    // TODO
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(new File(source, "silent_install.bat").getAbsolutePath());
    commandLine.setWorkDirectory(source);
    commandLine.addParameters("-m", String.valueOf(memorySize));
    return commandLine;
  }

  private static int getRecommendedMemoryAllocation() {
    return FirstRunWizardDefaults.getRecommendedHaxmMemory(getMemorySize());
  }

  public static long getMemorySize() {
    if (memorySize < 0) {
      memorySize = checkMemorySize();
    }
    return memorySize;
  }

  private static long checkMemorySize() {
    OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
    // This is specific to JDKs derived from Oracle JDK (including OpenJDK and Apple JDK among others).
    // Other then this, there's no standard way of getting memory size
    // without adding 3rd party libraries or using native code.
    try {
      Class<?> oracleSpecificMXBean = Class.forName("com.sun.management.OperatingSystemMXBean");
      Method getPhysicalMemorySizeMethod = oracleSpecificMXBean.getMethod("getTotalPhysicalMemorySize");
      Object result = getPhysicalMemorySizeMethod.invoke(osMXBean);
      if (result instanceof Number) {
        return ((Number)result).longValue();
      }
    }
    catch (ClassNotFoundException e) {
      // Unsupported JDK
    }
    catch (NoSuchMethodException e) {
      // Unsupported JDK
    }
    catch (InvocationTargetException e) {
      LOG.error(e); // Shouldn't happen (unsupported JDK?)
    }
    catch (IllegalAccessException e) {
      LOG.error(e); // Shouldn't happen (unsupported JDK?)
    }
    // Maximum memory allocatable to emulator - 32G. Only used if non-Oracle JRE.
    return 32L * Storage.Unit.GiB.getNumberOfBytes();
  }

  @Override
  public void init(@NotNull ScopedStateStore state, @NotNull ProgressStep progressStep) {
    myState = state;
    myProgressStep = progressStep;
    state.put(KEY_EMULATOR_MEMORY_MB, getRecommendedMemoryAllocation());
    state.put(KEY_INSTALL_HAXM, true);
  }

  @Override
  public DynamicWizardStep[] createSteps() {
    return new DynamicWizardStep[]{new HaxmInstallSettingsStep(myIsCustomInstall, KEY_INSTALL_HAXM, KEY_EMULATOR_MEMORY_MB)};
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull File sdk) {
    if (!canRun()) {
      Logger.getInstance(getClass()).error("Tried to install HAXM on %s OS with %s memory size", Platform.current().name(),
                                           String.valueOf(getMemorySize()));
      installContext.print("Unable to install Intel HAXM", ConsoleViewContentType.ERROR_OUTPUT);
      return;
    }
    GeneralCommandLine commandLine = getCommandLine(sdk);
    try {
      CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
      myProgressStep.attachToProcess(process);
      int exitCode = process.runProcess().getExitCode();
      if (exitCode != 0) {
        // HAXM is not required so we do not stop setup process if this install failed.
        myProgressStep.print("HAXM installation failed. To install HAXM follow the instructions found at " +
                             FirstRunWizardDefaults.HAXM_DOCUMENTATION_URL + ".", ConsoleViewContentType.ERROR_OUTPUT);
      }
      else {
        myProgressStep.print("Completed HAXM installation\n", ConsoleViewContentType.SYSTEM_OUTPUT);
      }
    }
    catch (ExecutionException e) {
      installContext.print("Unable to run Intel HAXM installer: " + e.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
      LOG.error(e);
    }
  }

  /**
   * @return command line object or <code>null</code> if OS is not supported
   */
  @NotNull
  private GeneralCommandLine getCommandLine(File sdk) {
    ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    progressIndicator.setIndeterminate(true);
    progressIndicator.setText("Running Intel® HAXM installer");
    int memorySize = myState.getNotNull(KEY_EMULATOR_MEMORY_MB, getRecommendedMemoryAllocation());
    String path = FileUtil.join(SdkConstants.FD_EXTRAS, COMPONENT_VENDOR, COMPONENT_PATH);
    File sourceLocation = new File(sdk, path);
    if (SystemInfo.isMac) {
      return getMacHaxmInstallCommandLine(sourceLocation, memorySize);
    }
    else if (SystemInfo.isWindows) {
      return getWindowsHaxmInstallCommandLine(sourceLocation, memorySize);
    }
    else {
      assert !canRun();
      throw new IllegalStateException("Usupported OS");
    }
  }

  @NotNull
  @Override
  public PkgDesc.Builder[] getRequiredSdkPackages() {
    return new PkgDesc.Builder[]{InstallComponentsPath.createExtra(true, COMPONENT_VENDOR, COMPONENT_PATH)};
  }
}
