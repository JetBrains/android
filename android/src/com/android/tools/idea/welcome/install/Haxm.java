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
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.devices.Storage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.avdmanager.AccelerationErrorCode;
import com.android.tools.idea.avdmanager.AccelerationErrorSolution;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.ElevatedCommandLine;
import com.android.tools.idea.welcome.wizard.deprecated.HaxmInstallSettingsStep;
import com.android.tools.idea.welcome.wizard.deprecated.HaxmUninstallInfoStep;
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStep;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Platform;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ModalityState;
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
  public enum HaxmInstallationIntention {
    INSTALL_WITH_UPDATES,
    INSTALL_WITHOUT_UPDATES,
    UNINSTALL
  }

  // In UI we cannot use longs, so we need to pick a unit other then byte
  public static final Storage.Unit UI_UNITS = Storage.Unit.MiB;
  public static final Logger LOG = Logger.getInstance(Haxm.class);
  public static final IdDisplay ID_INTEL = IdDisplay.create("intel", "");
  public static final String COMPONENT_PATH = "Hardware_Accelerated_Execution_Manager";
  public static final String REPO_PACKAGE_PATH = "extras;intel;" + COMPONENT_PATH;
  public static final String RUNNING_INTEL_HAXM_INSTALLER_MESSAGE = "Running Intel® HAXM installer";

  private static final int INTEL_HAXM_INSTALLER_EXIT_CODE_SUCCESS = 0;
  private static final int INTEL_HAXM_INSTALLER_EXIT_CODE_REBOOT_REQUIRED = 2;
  private static final ScopedStateStore.Key<Integer> KEY_EMULATOR_MEMORY_MB =
    ScopedStateStore.createKey("emulator.memory", ScopedStateStore.Scope.PATH, Integer.class);
  private final ScopedStateStore.Key<Boolean> myIsCustomInstall;
  private ProgressStep myProgressStep;
  private final HaxmInstallationIntention myInstallationIntention;
  private boolean myHaxmInstallerSuccessfullyCompleted = false;
  private static AccelerationErrorCode ourInitialCheck;

  public Haxm(@NotNull HaxmInstallationIntention installationIntention,
              @NotNull ScopedStateStore store,
              @NotNull ScopedStateStore.Key<Boolean> isCustomInstall) {
    super(store, "Performance (Intel ® HAXM)",
          "Enables a hardware-assisted virtualization engine (hypervisor) to speed up " +
          "Android app emulation on your development computer. (Recommended)",
          (installationIntention == HaxmInstallationIntention.INSTALL_WITH_UPDATES),
          FileOpUtils.create());
    myIsCustomInstall = isCustomInstall;
    myInstallationIntention = installationIntention;
  }

  public HaxmInstallationIntention getInstallationIntention() {
    return myInstallationIntention;
  }

  public boolean isConfiguredSuccessfully() {
    return myHaxmInstallerSuccessfullyCompleted;
  }

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
    return manager.checkAcceleration();
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

  /**
   * Modifies cl with parameters used during uninstallation and returns it.
   * @param cl The command line for the base command. Modified in-place by this method.
   * @return cl
   */
  @NotNull
  private static GeneralCommandLine addUninstallParameters(@NotNull GeneralCommandLine cl) {
    cl.addParameters("-u");
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
  private static GeneralCommandLine getWindowsHaxmCommandLine(File source) throws IOException {
    File batFile = new File(source, "silent_install.bat");
    File logFile = FileUtil.createTempFile("haxm_log", ".txt");
    return new ElevatedCommandLine(batFile.getAbsolutePath())
      .withTempFilePrefix("haxm")
      .withWorkDirectory(source)
      .withParameters("-log", logFile.getPath());
  }

  private static int getRecommendedMemoryAllocation() {
    return FirstRunWizardDefaults.getRecommendedHaxmMemory(AvdManagerConnection.getMemorySize());
  }

  @Override
  public void init(@NotNull ProgressStep progressStep) {
    myProgressStep = progressStep;
    if (myInstallationIntention == HaxmInstallationIntention.INSTALL_WITH_UPDATES
        || myInstallationIntention == HaxmInstallationIntention.INSTALL_WITHOUT_UPDATES) {
      myStateStore.put(KEY_EMULATOR_MEMORY_MB, getRecommendedMemoryAllocation());
    }
  }

  @NotNull
  @Override
  public Collection<DynamicWizardStep> createSteps() {
    if (myInstallationIntention == HaxmInstallationIntention.UNINSTALL) {
      return Collections.singleton(new HaxmUninstallInfoStep());
    }
    return Collections.singleton(new HaxmInstallSettingsStep(myIsCustomInstall, myKey, KEY_EMULATOR_MEMORY_MB));
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull AndroidSdkHandler sdkHandler) {
    File sdkRoot = sdkHandler.getLocation();
    if (sdkRoot == null) {
      installContext.print("HAXM installer could not be run because SDK root isn't specified", ConsoleViewContentType.ERROR_OUTPUT);
      return;
    }

    if (myInstallationIntention == HaxmInstallationIntention.UNINSTALL) {
      configureForUninstall(installContext, sdkRoot);
      return;
    }

    AccelerationErrorCode accelerationErrorCode = checkHaxmInstallation();

    AccelerationErrorSolution.SolutionCode solution = accelerationErrorCode.getSolution();
    if (accelerationErrorCode == ALREADY_INSTALLED) {
      if (myInstallationIntention == HaxmInstallationIntention.INSTALL_WITHOUT_UPDATES) {
        myHaxmInstallerSuccessfullyCompleted = true;
        return;
      }
      // Force reinstall
      solution = AccelerationErrorSolution.SolutionCode.REINSTALL_HAXM;
    }
    switch (solution) {
      case INSTALL_HAXM:
      case REINSTALL_HAXM:
        try {
          GeneralCommandLine commandLine = getInstallCommandLine(sdkRoot);
          runInstaller(installContext, commandLine);
        }
        catch (WizardException|IOException e) {
          LOG.warn(String.format("Tried to install HAXM on %s OS with %s memory size",
                                  Platform.current().name(), String.valueOf(AvdManagerConnection.getMemorySize())), e);
          installContext.print("Unable to install Intel HAXM\n", ConsoleViewContentType.ERROR_OUTPUT);
          String message = e.getMessage();
          if (!StringUtil.endsWithLineBreak(message)) {
            message += "\n";
          }
          installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT);
        }
        break;

      case NONE:
        String message = String.format("Unable to install Intel HAXM\n%1$s\n%2$s\n", accelerationErrorCode.getProblem(), accelerationErrorCode.getSolutionMessage());
        installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT);
        break;

      default:
        // Different error that is unrelated to the installation of Haxm
        break;
    }
  }

  private void configureForUninstall(@NotNull InstallContext installContext, @NotNull File sdkRoot) {
    if (isInstalled(installContext, sdkRoot)) {
      try {
        GeneralCommandLine commandLine = getUninstallCommandLine(sdkRoot);
        runInstaller(installContext, commandLine);
      }
      catch (WizardException |IOException e) {
        LOG.warn(String.format("Tried to uninstall HAXM on %s OS", Platform.current().name()), e);
        installContext.print("Unable to uninstall Intel HAXM\n", ConsoleViewContentType.ERROR_OUTPUT);
        String message = e.getMessage();
        if (!StringUtil.endsWithLineBreak(message)) {
          message += "\n";
        }
        installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT);
      }
    }
    else {
      // HAXM is not installed and the intention is to uninstall, so nothing to do here
      // This should not normally be the case unless some of the previous HAXM installation/uninstallation
      // operations failed or were executed outside of Studio
      installContext.print("HAXM is not installed, so not proceeding with its uninstallation.", ConsoleViewContentType.NORMAL_OUTPUT);
      myHaxmInstallerSuccessfullyCompleted = true;
    }
  }

  private static boolean isInstalled(@NotNull InstallContext context, @NotNull File sdkRoot) {
    try {
      GeneralCommandLine command = getInstallerCommandLine(sdkRoot);
      command.addParameter("-v");
      OSProcessHandler processHandler = new OSProcessHandler(command);
      processHandler.startNotify();
      processHandler.waitFor();
      Integer exitCode = processHandler.getExitCode();
      return exitCode != null && exitCode == 0;
    }
    catch (ExecutionException | WizardException | IOException exception) {
      context.print("Failed to determine whether HAXM is installed: " + exception.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
      return false;
    }
  }

  private void runInstaller(@NotNull InstallContext installContext, @NotNull GeneralCommandLine commandLine) {
    try {
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      if (progressIndicator != null) {
        progressIndicator.setIndeterminate(true);
        progressIndicator.setText(RUNNING_INTEL_HAXM_INSTALLER_MESSAGE);
      }
      installContext.print(RUNNING_INTEL_HAXM_INSTALLER_MESSAGE + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
      CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
      final StringBuffer output = new StringBuffer();
      process.addProcessListener(new ProcessAdapter() {
        @Override
        public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
          output.append(event.getText());
          super.onTextAvailable(event, outputType);
        }
      });
      myProgressStep.attachToProcess(process);
      int exitCode = process.runProcess().getExitCode();
      // TODO: For some reason, this can be a deceptive zero exit code on Mac when emulator instances are running
      // More testing of bash scripts invocation with intellij process wrappers might be useful.
      if (exitCode != INTEL_HAXM_INSTALLER_EXIT_CODE_SUCCESS) {
        // According to the installer docs for Windows, installer may signify that a reboot is required
        if (SystemInfo.isWindows && exitCode == INTEL_HAXM_INSTALLER_EXIT_CODE_REBOOT_REQUIRED) {
          String rebootMessage = "Reboot required: HAXM installation succeeded, however the installer reported that a reboot is " +
                                 "required in order for the changes to take effect";
          installContext.print(rebootMessage, ConsoleViewContentType.NORMAL_OUTPUT);
          AccelerationErrorSolution.promptAndRebootAsync(rebootMessage, ModalityState.NON_MODAL);
          myHaxmInstallerSuccessfullyCompleted = true;

          return;
        }

        // HAXM is not required so we do not stop setup process if this install failed.
        if (myInstallationIntention == HaxmInstallationIntention.UNINSTALL) {
          installContext.print("HAXM uninstallation failed", ConsoleViewContentType.ERROR_OUTPUT);
        }
        else {
          installContext.print(
            String.format("HAXM installation failed. To install HAXM follow the instructions found at: %s",
                            SystemInfo.isWindows ? FirstRunWizardDefaults.HAXM_WINDOWS_INSTALL_URL
                                                 : FirstRunWizardDefaults.HAXM_MAC_INSTALL_URL),
            ConsoleViewContentType.ERROR_OUTPUT);
        }
        Matcher m = Pattern.compile("installation log:\\s*\"(.*)\"").matcher(output.toString());
        if (m.find()) {
          String file = m.group(1);
          installContext.print(String.format("Installer log is located at %s", file), ConsoleViewContentType.ERROR_OUTPUT);
          try {
            installContext.print("Installer log contents:\n", ConsoleViewContentType.ERROR_OUTPUT);
            installContext.print(FileUtil.loadFile(new File(file), "UTF-16"), ConsoleViewContentType.NORMAL_OUTPUT);
          }
          catch (IOException e) {
            installContext.print("Failed to read installer output log.\n", ConsoleViewContentType.ERROR_OUTPUT);
          }
        }
        if (progressIndicator != null) {
          progressIndicator.setFraction(1);
        }
        myHaxmInstallerSuccessfullyCompleted = false;
        return;
      }
      if (progressIndicator != null) {
        progressIndicator.setFraction(1);
      }
      myHaxmInstallerSuccessfullyCompleted = true;
    }
    catch (ExecutionException e) {
      installContext.print("Unable to run Intel HAXM installer: " + e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
      LOG.warn(e);
    }
  }

  /**
   * Create a platform-dependant command line for running the silent HAXM installer.
   *
   * @return command line object
   * @throws IllegalStateException if called on an unsupported OS
   */
  @NotNull
  private GeneralCommandLine getInstallCommandLine(@NotNull File sdk) throws WizardException, IOException {
    int memorySize = myStateStore.getNotNull(KEY_EMULATOR_MEMORY_MB, getRecommendedMemoryAllocation());
    return addInstallParameters(getInstallerCommandLine(sdk), memorySize);
  }

  @NotNull
  private static GeneralCommandLine getInstallerCommandLine(@NotNull File sdk) throws WizardException, IOException {
    if (SystemInfo.isMac) {
      return getMacHaxmCommandLine(getSourceLocation(sdk));
    }
    else if (SystemInfo.isWindows) {
      return getWindowsHaxmCommandLine(getSourceLocation(sdk));
    }
    else {
      assert !canRun();
      throw new IllegalStateException("Unsupported OS");
    }
  }

  @NotNull
  private static GeneralCommandLine getUninstallCommandLine(File sdk) throws WizardException, IOException {
    if (SystemInfo.isMac) {
      return addUninstallParameters(getMacHaxmCommandLine(getSourceLocation(sdk)));
    }
    else if (SystemInfo.isWindows) {
      return addUninstallParameters(getWindowsHaxmCommandLine(getSourceLocation(sdk)));
    }
    else {
      assert !canRun();
      throw new IllegalStateException("Unsupported OS");
    }
  }

  @NotNull
  private static File getSourceLocation(File sdk) {
    String path = FileUtil.join(SdkConstants.FD_EXTRAS, ID_INTEL.getId(), COMPONENT_PATH);
    return new File(sdk, path);
  }

  @NotNull
  @Override
  public Collection<String> getRequiredSdkPackages() {
    return ImmutableList.of(REPO_PACKAGE_PATH);
  }
}
