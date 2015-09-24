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
package com.android.tools.idea.welcome.install;

import com.android.SdkConstants;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.NoPreviewRevision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.IdDisplay;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.sdklib.repository.descriptors.PkgType;
import com.android.tools.idea.sdk.remote.RemotePkgInfo;
import com.android.tools.idea.welcome.wizard.HaxmInstallSettingsStep;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Intel® HAXM installable component
 */
public final class Haxm extends InstallableComponent {
  // In UI we cannot use longs, so we need to pick a unit other then byte
  public static final Storage.Unit UI_UNITS = Storage.Unit.MiB;
  public static final Logger LOG = Logger.getInstance(Haxm.class);
  public static final IdDisplay ID_INTEL = new IdDisplay("intel", "");
  public static final String COMPONENT_PATH = "Hardware_Accelerated_Execution_Manager";
  public static final String RUNNING_INTEL_HAXM_INSTALLER_MESSAGE = "Running Intel® HAXM installer";
  private static final ScopedStateStore.Key<Integer> KEY_EMULATOR_MEMORY_MB =
    ScopedStateStore.createKey("emulator.memory", ScopedStateStore.Scope.PATH, Integer.class);
  private static long myMemorySize = -1;
  private final ScopedStateStore.Key<Boolean> myIsCustomInstall;
  private ProgressStep myProgressStep;

  /**
   * Returns the version of haxm installed, as reported by the silent installer.
   * Note this is different from the haxm installer version as reported by the sdk manager
   * (e.g. sdk manager currently shows version 5.2, but haxm installer reports 1.1.1).
   * @param sdk Path to the android sdk
   * @return the version of haxm that is currently installed
   * @throws WizardException If haxm is not currently installed, or there is a problem running the installer.
   */
  public static FullRevision getInstalledVersion(@NotNull File sdk) throws WizardException {
    GeneralCommandLine command;
    String path = FileUtil.join(SdkConstants.FD_EXTRAS, ID_INTEL.getId(), COMPONENT_PATH);
    File sourceLocation = new File(sdk, path);

    if (SystemInfo.isMac) {
      command = addVersionParameters(getMacHaxmCommandLine(sourceLocation));
    }
    else if (SystemInfo.isWindows) {
      command = addVersionParameters(getWindowsHaxmCommandLine(sourceLocation));
    }
    else {
      assert !canRun();
      throw new IllegalStateException("Unsupported OS");
    }
    try {
      CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(command);
      return FullRevision.parseRevision(process.runProcess().getStdout());
    }
    catch (NumberFormatException e) {
      LOG.warn("Invalid HAXM version found.", e);
      return new FullRevision(0);
    }
    catch (ExecutionException e) {
      throw new WizardException("Failed to get HAXM version", e);
    }
  }

  public static boolean canRun() {
    // TODO HAXM is disabled on Windows as headless installer currently fails to request admin access as needed.
    if (((Boolean.getBoolean("install.haxm") && SystemInfo.isWindows) || SystemInfo.isMac) &&
        isSupportedProcessor()) {
      return getMemorySize() >= Storage.Unit.GiB.getNumberOfBytes();
    }
    else {
      return false;
    }
  }

  private static boolean isSupportedProcessor() {
    if (SystemInfo.isMac) {
      return true;
    } else if (SystemInfo.isWindows) {
      String id = System.getenv().get("PROCESSOR_IDENTIFIER");
      if (id != null && id.contains("GenuineIntel")) {
        return true;
      }
    }
    return false;
  }



  public Haxm(@NotNull ScopedStateStore store, ScopedStateStore.Key<Boolean> isCustomInstall) {
    super(store, "Performance (Intel ® HAXM)", 2306867, "Enables a hardware-assisted virtualization engine (hypervisor) to speed up " +
                                                        "Android app emulation on your development computer. (Recommended)");
    myIsCustomInstall = isCustomInstall;
  }

  /**
   * Modifies cl with parameters used during installation and returns it.
   * @param cl The command line for the base command. Modified in-place by this method.
   * @param memorySize The memory that haxm should use
   * @return cl
   */
  @NotNull
  private GeneralCommandLine addInstallParameters(@NotNull GeneralCommandLine cl, int memorySize) {
    cl.addParameters("-m", String.valueOf(memorySize));
    return cl;
  }

  /**
   * Modifies cl with parameters used to check the haxm version and returns it.
   * @param cl The command line for the base command. Modified in-place by this method.
   * @return cl
   */
  @NotNull
  private static GeneralCommandLine addVersionParameters(@NotNull GeneralCommandLine cl) {
    cl.addParameters("-v");
    return cl;
  }

  private static GeneralCommandLine getMacHaxmCommandLine(File path) throws WizardException {
    // The new executable now requests admin access and executes the shell script. We need to make sure both exist and
    // are executable.
    ensureExistsAndIsExecutable(path, "silent_install.sh");
    File executable = ensureExistsAndIsExecutable(path, "HAXM installation");
    return new GeneralCommandLine(executable.getAbsolutePath()).withWorkDirectory(path);
  }

  @NotNull
  private static File ensureExistsAndIsExecutable(File path, String exeName) throws WizardException {
    File executable = new File(path, exeName);
    if (!executable.isFile()) {
      throw new WizardException("HAXM installer executable is missing: " + executable.getAbsolutePath());
    }
    else if (executable.canExecute() || executable.setExecutable(true)) {
      return executable;
    }
    else {
      throw new WizardException("Unable to set execute permission bit on HAXM installer executable: " + executable.getAbsolutePath());
    }
  }

  @NotNull
  private static GeneralCommandLine getWindowsHaxmCommandLine(File source) {
    File batFile = new File(source, "silent_install.bat");
    return new GeneralCommandLine(batFile.getAbsolutePath()).withWorkDirectory(source);
  }



  private static int getRecommendedMemoryAllocation() {
    return FirstRunWizardDefaults.getRecommendedHaxmMemory(getMemorySize());
  }

  public static long getMemorySize() {
    if (myMemorySize < 0) {
      myMemorySize = checkMemorySize();
    }
    return myMemorySize;
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

  @NotNull
  private static IPkgDesc createExtra(@NotNull IdDisplay vendor, @NotNull String path) {
    return PkgDesc.Builder.newExtra(vendor, path, "", null, new NoPreviewRevision(FullRevision.MISSING_MAJOR_REV)).create();
  }

  @Override
  public void init(@NotNull ProgressStep progressStep) {
    myProgressStep = progressStep;
    myStateStore.put(KEY_EMULATOR_MEMORY_MB, getRecommendedMemoryAllocation());
  }

  @NotNull
  @Override
  public Collection<DynamicWizardStep> createSteps() {
    return Collections.<DynamicWizardStep>singleton(new HaxmInstallSettingsStep(myIsCustomInstall, myKey, KEY_EMULATOR_MEMORY_MB));
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull File sdk) {
    if (!canRun()) {
      Logger.getInstance(getClass()).error(
        String.format("Tried to install HAXM on %s OS with %s memory size", Platform.current().name(), String.valueOf(getMemorySize())));
      installContext.print("Unable to install Intel HAXM\n", ConsoleViewContentType.ERROR_OUTPUT);
      return;
    }
    try {
      GeneralCommandLine commandLine = getInstallCommandLine(sdk);
      runInstaller(installContext, commandLine);
    }
    catch (WizardException e) {
      String message = e.getMessage();
      if (!StringUtil.endsWithLineBreak(message)) {
        message += "\n";
      }
      installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT);
      LOG.error(e);
    }
  }

  private void runInstaller(InstallContext installContext, GeneralCommandLine commandLine) {
    try {
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      progressIndicator.setIndeterminate(true);
      progressIndicator.setText(RUNNING_INTEL_HAXM_INSTALLER_MESSAGE);
      installContext.print(RUNNING_INTEL_HAXM_INSTALLER_MESSAGE + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
      CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
      final StringBuffer output = new StringBuffer();
      process.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(ProcessEvent event, Key outputType) {
          output.append(event.getText());
          super.onTextAvailable(event, outputType);
        }
      });
      myProgressStep.attachToProcess(process);
      int exitCode = process.runProcess().getExitCode();
      if (exitCode != 0) {
        // HAXM is not required so we do not stop setup process if this install failed.
        installContext.print(
          String.format("HAXM installation failed. To install HAXM follow the instructions found at: %s",
                        SystemInfo.isWindows ? FirstRunWizardDefaults.HAXM_WINDOWS_INSTALL_URL
                                             : FirstRunWizardDefaults.HAXM_MAC_INSTALL_URL),
          ConsoleViewContentType.ERROR_OUTPUT);
        Matcher m = Pattern.compile("installation log:\\s*\"(.*)\"").matcher(output.toString());
        if (m.find()) {
          String file = m.group(1);
          try {
            installContext.print("Installer log:\n", ConsoleViewContentType.ERROR_OUTPUT);
            installContext.print(FileUtil.loadFile(new File(file), "UTF-16"), ConsoleViewContentType.NORMAL_OUTPUT);
          }
          catch (IOException e) {
            installContext.print("Failed to read installer output log.\n", ConsoleViewContentType.ERROR_OUTPUT);
          }
        }
      }
      progressIndicator.setFraction(1);
    }
    catch (ExecutionException e) {
      installContext.print("Unable to run Intel HAXM installer: " + e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
      LOG.error(e);
    }
  }

  /**
   * Create a platform-dependant command line for running the silent HAXM installer.
   *
   * @return command line object
   * @throws IllegalStateException if called on an unsupported OS
   */
  @NotNull
  private GeneralCommandLine getInstallCommandLine(File sdk) throws WizardException {
    int memorySize = myStateStore.getNotNull(KEY_EMULATOR_MEMORY_MB, getRecommendedMemoryAllocation());
    String path = FileUtil.join(SdkConstants.FD_EXTRAS, ID_INTEL.getId(), COMPONENT_PATH);
    File sourceLocation = new File(sdk, path);
    if (SystemInfo.isMac) {
      return addInstallParameters(getMacHaxmCommandLine(sourceLocation), memorySize);
    }
    else if (SystemInfo.isWindows) {
      return addInstallParameters(getWindowsHaxmCommandLine(sourceLocation), memorySize);
    }
    else {
      assert !canRun();
      throw new IllegalStateException("Unsupported OS");
    }
  }

  @NotNull
  @Override
  public Collection<IPkgDesc> getRequiredSdkPackages(@Nullable Multimap<PkgType, RemotePkgInfo> remotePackages) {
    return ImmutableList.of(createExtra(ID_INTEL, COMPONENT_PATH));
  }
}
