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
import com.android.repository.Revision;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.legacy.descriptors.IPkgDesc;
import com.android.sdklib.repository.legacy.descriptors.PkgDesc;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.avdmanager.AccelerationErrorCode;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.ElevatedCommandLine;
import com.android.tools.idea.welcome.wizard.HaxmInstallSettingsStep;
import com.android.tools.idea.welcome.wizard.ProgressStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableList;
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

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.avdmanager.AccelerationErrorCode.ALREADY_INSTALLED;
import static com.android.tools.idea.avdmanager.AccelerationErrorCode.CANNOT_INSTALL_ON_THIS_OS;

/**
 * Intel® HAXM installable component
 */
public final class Haxm extends InstallableComponent {
  // In UI we cannot use longs, so we need to pick a unit other then byte
  public static final Storage.Unit UI_UNITS = Storage.Unit.MiB;
  public static final Logger LOG = Logger.getInstance(Haxm.class);
  public static final IdDisplay ID_INTEL = IdDisplay.create("intel", "");
  public static final String COMPONENT_PATH = "Hardware_Accelerated_Execution_Manager";
  public static final String REPO_PACKAGE_PATH = "extras;intel;" + COMPONENT_PATH;
  public static final String RUNNING_INTEL_HAXM_INSTALLER_MESSAGE = "Running Intel® HAXM installer";
  private static final ScopedStateStore.Key<Integer> KEY_EMULATOR_MEMORY_MB =
    ScopedStateStore.createKey("emulator.memory", ScopedStateStore.Scope.PATH, Integer.class);
  private final ScopedStateStore.Key<Boolean> myIsCustomInstall;
  private ProgressStep myProgressStep;
  private static AccelerationErrorCode ourInitialCheck;

  /**
   * Return true if it is possible to install Haxm on the current machine without any other configuration changes.
   */
  public static boolean canRun() {
    if (ourInitialCheck == null) {
      ourInitialCheck = checkHaxmInstallation();
    }
    switch (ourInitialCheck) {
      case NO_EMULATOR_INSTALLED:
      case UNKNOWN_ERROR:
        // We don't know if we can install Haxm. Assume we can if this is Windows or Mac:
        return SystemInfo.isMac || SystemInfo.isWindows;
      case NOT_ENOUGH_MEMORY:
      case ALREADY_INSTALLED:
        return false;
      default:
        switch (ourInitialCheck.getSolution()) {
          case INSTALL_HAXM:
          case REINSTALL_HAXM:
            return true;
          default:
            return false;
        }
    }
  }

  /**
   * Check the status of the Haxm installation.<br/>
   * If Haxm is installed we return the error code:
   * <ul><li>{@link AccelerationErrorCode#ALREADY_INSTALLED}</li></ul>
   * Other possible error conditions:
   * <ul>
   *   <li>On an OS other than Windows and Mac</li>
   *   <li>On Windows (until we fix the headless installer to use an admin account)</li>
   *   <li>If the CPU is not an Intel processor</li>
   *   <li>If there is not enough memory available</li>
   *   <li>If the CPU is not an Intel processor</li>
   *   <li>BIOS is not setup correctly</li>
   * </ul>
   * For some of these error conditions the user may rectify the problem and install Haxm later.
   */
  public static AccelerationErrorCode checkHaxmInstallation() {
    if (!SystemInfo.isWindows && !SystemInfo.isMac) {
      return CANNOT_INSTALL_ON_THIS_OS;
    }
    AvdManagerConnection manager = AvdManagerConnection.getDefaultAvdManagerConnection();
    return manager.checkAcceration();
  }

  public Haxm(@NotNull ScopedStateStore store, ScopedStateStore.Key<Boolean> isCustomInstall, boolean installUpdates) {
    super(store, "Performance (Intel ® HAXM)", "Enables a hardware-assisted virtualization engine (hypervisor) to speed up " +
                                                        "Android app emulation on your development computer. (Recommended)",
          installUpdates, FileOpUtils.create());
    myIsCustomInstall = isCustomInstall;
  }

  /**
   * Modifies cl with parameters used during installation and returns it.
   * @param cl The command line for the base command. Modified in-place by this method.
   * @param memorySize The memory that haxm should use
   * @return cl
   */
  @NotNull
  private static GeneralCommandLine addInstallParameters(@NotNull GeneralCommandLine cl, int memorySize) {
    cl.addParameters("-m", String.valueOf(memorySize));
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
    return new ElevatedCommandLine(batFile.getAbsolutePath()).withWorkDirectory(source);
  }

  private static int getRecommendedMemoryAllocation() {
    return FirstRunWizardDefaults.getRecommendedHaxmMemory(AvdManagerConnection.getMemorySize());
  }

  @NotNull
  private static IPkgDesc createExtra(@NotNull IdDisplay vendor, @NotNull String path) {
    return PkgDesc.Builder.newExtra(vendor, path, "", null, new Revision(Revision.MISSING_MAJOR_REV)).create();
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
  public void configure(@NotNull InstallContext installContext, @NotNull AndroidSdkHandler sdkHandler) {
    AccelerationErrorCode error = checkHaxmInstallation();
    if (error == ALREADY_INSTALLED) {
      return;
    }
    switch (error.getSolution()) {
      case INSTALL_HAXM:
      case REINSTALL_HAXM:
        try {
          GeneralCommandLine commandLine = getInstallCommandLine(sdkHandler.getLocation());
          runInstaller(installContext, commandLine);
        }
        catch (WizardException e) {
          LOG.error(String.format("Tried to install HAXM on %s OS with %s memory size",
                                  Platform.current().name(), String.valueOf(AvdManagerConnection.getMemorySize())));
          installContext.print("Unable to install Intel HAXM\n", ConsoleViewContentType.ERROR_OUTPUT);
          String message = e.getMessage();
          if (!StringUtil.endsWithLineBreak(message)) {
            message += "\n";
          }
          installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT);
          LOG.error(e);
        }
        break;

      case NONE:
        String message = String.format("Unable to install Intel HAXM\n%1$s\n%2$s\n", error.getProblem(), error.getSolutionMessage());
        installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT);
        break;

      default:
        // Different error that is unrelated to the installation of Haxm
        break;
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
  protected Collection<String> getRequiredSdkPackages() {
    return ImmutableList.of(REPO_PACKAGE_PATH);
  }
}
