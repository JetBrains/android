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

import static com.android.tools.idea.avdmanager.AccelerationErrorCode.ALREADY_INSTALLED;
import static com.android.tools.idea.avdmanager.AccelerationErrorCode.CANNOT_INSTALL_ON_THIS_OS;

import com.android.SdkConstants;
import com.android.repository.io.FileOpUtils;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.sdklib.repository.IdDisplay;
import com.android.tools.idea.avdmanager.AccelerationErrorCode;
import com.android.tools.idea.avdmanager.AccelerationErrorSolution;
import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.avdmanager.ElevatedCommandLine;
import com.android.tools.idea.welcome.wizard.deprecated.GvmInstallInfoStep;
import com.android.tools.idea.welcome.wizard.deprecated.ProgressStep;
import com.android.tools.idea.welcome.wizard.deprecated.VmUninstallInfoStep;
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
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Google® GVM installable component
 */
public final class Gvm extends Vm {
  public static final Logger LOG = Logger.getInstance(Gvm.class);
  public static final IdDisplay ID_GOOGLE = IdDisplay.create("google", "");
  public static final String COMPONENT_PATH = "Android_Emulator_Hypervisor_Driver";
  public static final String GVM_REPO_PACKAGE_PATH = "extras;google;" + COMPONENT_PATH;
  public static final String RUNNING_GOOGLE_GVM_INSTALLER_MESSAGE = "Running Android Emulator Hypervisor Driver for AMD Processors installer";

  private static final int GOOGLE_GVM_INSTALLER_EXIT_CODE_SUCCESS = 0;
  private static final int GOOGLE_GVM_INSTALLER_EXIT_CODE_REBOOT_REQUIRED = 2;
  private ProgressStep myProgressStep;
  private static AccelerationErrorCode ourInitialCheck;

  public Gvm(
    @NotNull InstallationIntention installationIntention,
    @NotNull ScopedStateStore store,
    @NotNull ScopedStateStore.Key<Boolean> isCustomInstall
  ) {
    super(store, "Performance (Android Emulator Hypervisor Driver for AMD Processors)",
          "Enables a hardware-assisted virtualization engine (hypervisor) to speed up " +
          "Android app emulation on your development computer. (Recommended)",
          (installationIntention == InstallationIntention.INSTALL_WITH_UPDATES),
          FileOpUtils.create(),
          installationIntention,
          isCustomInstall);
  }

  /**
   * Return true if it is possible to install Gvm on the current machine without any other configuration changes.
   */
  public static boolean canRun() {
    if (ourInitialCheck == null) {
      ourInitialCheck = checkGvmInstallation();
    }
    switch (ourInitialCheck) {
      case NO_EMULATOR_INSTALLED:
      case UNKNOWN_ERROR:
        if (SystemInfo.isWindows && CpuVendor.isAMD()) {
          return true;
        }
        return false;
      case NOT_ENOUGH_MEMORY:
      case ALREADY_INSTALLED:
        return false;
      default:
        switch (ourInitialCheck.getSolution()) {
          case INSTALL_GVM:
          case REINSTALL_GVM:
            return true;
          default:
            return false;
        }
    }
  }

  /**
   * Check the status of the Gvm installation.<br/>
   * If Gvm is installed we return the error code:
   * <ul><li>{@link AccelerationErrorCode#ALREADY_INSTALLED}</li></ul>
   * Other possible error conditions:
   * <ul>
   *   <li>On an OS other than Windows and Mac</li>
   *   <li>On Windows (until we fix the headless installer to use an admin account)</li>
   *   <li>If the CPU is not an Google processor</li>
   *   <li>If there is not enough memory available</li>
   *   <li>If the CPU is not an Google processor</li>
   *   <li>BIOS is not setup correctly</li>
   * </ul>
   * For some of these error conditions the user may rectify the problem and install Gvm later.
   */
  private static AccelerationErrorCode checkGvmInstallation() {
    if (!SystemInfo.isWindows || !CpuVendor.isAMD()) {
      return CANNOT_INSTALL_ON_THIS_OS;
    }
    AvdManagerConnection manager = AvdManagerConnection.getDefaultAvdManagerConnection();
    return manager.checkAcceleration();
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

  @NotNull
  private static GeneralCommandLine getWindowsGvmCommandLine(File source) throws IOException {
    File batFile = new File(source, "silent_install.bat");
    File logFile = FileUtil.createTempFile("gvm_log", ".txt");
    return new ElevatedCommandLine(batFile.getAbsolutePath())
      .withTempFilePrefix("gvm")
      .withWorkDirectory(source)
      .withParameters("-log", logFile.getPath());
  }

  @Override
  public void init(@NotNull ProgressStep progressStep) {
    myProgressStep = progressStep;
  }

  @NotNull
  @Override
  public Collection<DynamicWizardStep> createSteps() {
    if (installationIntention == InstallationIntention.UNINSTALL) {
      return Collections.singleton(new VmUninstallInfoStep(VmType.GVM));
    }
    return Collections.singleton(new GvmInstallInfoStep(isCustomInstall));
  }

  @Override
  public void configure(@NotNull InstallContext installContext, @NotNull AndroidSdkHandler sdkHandler) {
    File sdkRoot = sdkHandler.getLocation();
    if (sdkRoot == null) {
      installContext.print("Android Emulator Hypervisor Driver for AMD Processors installer " +
              "could not be run because SDK root isn't specified", ConsoleViewContentType.ERROR_OUTPUT);
      return;
    }

    if (installationIntention == InstallationIntention.UNINSTALL) {
      configureForUninstall(installContext, sdkRoot);
      return;
    }

    AccelerationErrorCode accelerationErrorCode = checkGvmInstallation();

    AccelerationErrorSolution.SolutionCode solution = accelerationErrorCode.getSolution();
    if (accelerationErrorCode == ALREADY_INSTALLED) {
      if (installationIntention == InstallationIntention.INSTALL_WITHOUT_UPDATES) {
        setInstallerSuccessfullyCompleted(true);
        return;
      }
      // Force reinstall
      solution = AccelerationErrorSolution.SolutionCode.REINSTALL_GVM;
    }
    switch (solution) {
      case INSTALL_GVM:
      case REINSTALL_GVM:
        try {
          GeneralCommandLine commandLine = getInstallCommandLine(sdkRoot);
          runInstaller(installContext, commandLine);
        }
        catch (IOException e) {
          LOG.warn(String.format("Tried to install Android Emulator Hypervisor Driver for AMD Processors on %s OS with %s memory size",
                                 Platform.current().name(), AvdManagerConnection.getMemorySize()), e);
          installContext.print("Unable to install Android Emulator Hypervisor Driver for AMD Processors\n", ConsoleViewContentType.ERROR_OUTPUT);
          String message = e.getMessage();
          if (!StringUtil.endsWithLineBreak(message)) {
            message += "\n";
          }
          installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT);
        }
        break;

      case NONE:
        String message = String.format("Unable to install Android Emulator Hypervisor Driver for AMD Processors\n%1$s\n%2$s\n", accelerationErrorCode.getProblem(), accelerationErrorCode.getSolutionMessage());
        installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT);
        break;

      default:
        // Different error that is unrelated to the installation of Gvm
        break;
    }
  }

  private void configureForUninstall(@NotNull InstallContext installContext, @NotNull File sdkRoot) {
    if (isInstalled(installContext, sdkRoot)) {
      try {
        GeneralCommandLine commandLine = getUninstallCommandLine(sdkRoot);
        runInstaller(installContext, commandLine);
      }
      catch (IOException e) {
        LOG.warn(String.format("Tried to uninstall Android Emulator Hypervisor Driver for AMD Processors on %s OS", Platform.current().name()), e);
        installContext.print("Unable to uninstall Android Emulator Hypervisor Driver for AMD Processors\n", ConsoleViewContentType.ERROR_OUTPUT);
        String message = e.getMessage();
        if (!StringUtil.endsWithLineBreak(message)) {
          message += "\n";
        }
        installContext.print(message, ConsoleViewContentType.ERROR_OUTPUT);
      }
    }
    else {
      // GVM is not installed and the intention is to uninstall, so nothing to do here
      // This should not normally be the case unless some of the previous GVM installation/uninstallation
      // operations failed or were executed outside of Studio
      installContext.print("Android Emulator Hypervisor Driver for AMD Processors is not installed, so not proceeding with its uninstallation.", ConsoleViewContentType.NORMAL_OUTPUT);
      setInstallerSuccessfullyCompleted(true);
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
    catch (ExecutionException | IOException exception) {
      context.print("Failed to determine whether Android Emulator Hypervisor Driver for AMD Processors is installed: " + exception.getMessage(), ConsoleViewContentType.ERROR_OUTPUT);
      return false;
    }
  }

  private void runInstaller(@NotNull InstallContext installContext, @NotNull GeneralCommandLine commandLine) {
    try {
      ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
      if (progressIndicator != null) {
        progressIndicator.setIndeterminate(true);
        progressIndicator.setText(RUNNING_GOOGLE_GVM_INSTALLER_MESSAGE);
      }
      installContext.print(RUNNING_GOOGLE_GVM_INSTALLER_MESSAGE + "\n", ConsoleViewContentType.SYSTEM_OUTPUT);
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
      if (exitCode != GOOGLE_GVM_INSTALLER_EXIT_CODE_SUCCESS) {
        // According to the installer docs for Windows, installer may signify that a reboot is required
        if (SystemInfo.isWindows && exitCode == GOOGLE_GVM_INSTALLER_EXIT_CODE_REBOOT_REQUIRED) {
          String rebootMessage = "Reboot required: Android Emulator Hypervisor Driver for AMD Processors installation succeeded, however the installer reported that a reboot is " +
                                 "required in order for the changes to take effect";
          installContext.print(rebootMessage, ConsoleViewContentType.NORMAL_OUTPUT);
          AccelerationErrorSolution.promptAndRebootAsync(rebootMessage, ModalityState.NON_MODAL);
          setInstallerSuccessfullyCompleted(true);

          return;
        }

        // GVM is not required so we do not stop setup process if this install failed.
        if (installationIntention == InstallationIntention.UNINSTALL) {
          installContext.print("Android Emulator Hypervisor Driver for AMD Processors uninstallation failed", ConsoleViewContentType.ERROR_OUTPUT);
        }
        else {
          installContext.print(
            String.format("Android Emulator Hypervisor Driver for AMD Processors installation failed. To install Android Emulator Hypervisor Driver for AMD Processors follow the instructions found at: %s",
                          FirstRunWizardDefaults.GVM_WINDOWS_INSTALL_URL),
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
        setInstallerSuccessfullyCompleted(false);
        return;
      }
      if (progressIndicator != null) {
        progressIndicator.setFraction(1);
      }
      setInstallerSuccessfullyCompleted(true);
    }
    catch (ExecutionException e) {
      installContext.print("Unable to run Android Emulator Hypervisor Driver for AMD Processors installer: " + e.getMessage() + "\n", ConsoleViewContentType.ERROR_OUTPUT);
      LOG.warn(e);
    }
  }

  /**
   * Create a platform-dependant command line for running the silent GVM installer.
   *
   * @return command line object
   * @throws IllegalStateException if called on an unsupported OS
   */
  @NotNull
  private GeneralCommandLine getInstallCommandLine(@NotNull File sdk) throws IOException {
    return getInstallerCommandLine(sdk);
  }

  @NotNull
  private static GeneralCommandLine getInstallerCommandLine(@NotNull File sdk) throws IOException {
    if (SystemInfo.isWindows) {
      return getWindowsGvmCommandLine(getSourceLocation(sdk));
    }
    else {
      assert !canRun();
      throw new IllegalStateException("Unsupported OS");
    }
  }

  @NotNull
  private static GeneralCommandLine getUninstallCommandLine(File sdk) throws IOException {
    if (SystemInfo.isWindows) {
      return addUninstallParameters(getWindowsGvmCommandLine(getSourceLocation(sdk)));
    }
    else {
      assert !canRun();
      throw new IllegalStateException("Unsupported OS");
    }
  }

  @NotNull
  private static File getSourceLocation(File sdk) {
    String path = FileUtil.join(SdkConstants.FD_EXTRAS, ID_GOOGLE.getId(), COMPONENT_PATH);
    return new File(sdk, path);
  }

  @NotNull
  @Override
  public Collection<String> getRequiredSdkPackages() {
    return ImmutableList.of(GVM_REPO_PACKAGE_PATH);
  }
}
