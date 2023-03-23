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

import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.INSTALL_GVM;
import static com.android.tools.idea.avdmanager.AccelerationErrorSolution.SolutionCode.REINSTALL_GVM;

import com.android.SdkConstants;
import com.android.repository.Revision;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.sdk.wizard.VmWizard;
import com.android.tools.idea.sdk.install.VmType;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.ImmutableList;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtilRt;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Solution strings used in {@link AccelerationErrorCode}, and associated Runnables to fix them.
 */
public class AccelerationErrorSolution {
  private static final Logger LOG = Logger.getInstance(AccelerationErrorNotificationPanel.class);
  private static final Revision KARMIC_KERNEL = Revision.parseRevision("2.6.31");
  private static final String KVM_INSTRUCTIONS = "https://help.ubuntu.com/community/KVM/Installation";

  static final String SOLUTION_NESTED_VIRTUAL_MACHINE =
    "Unfortunately, the Android Emulator can't support virtual machine acceleration from within a virtual machine.\n" +
    "Here are some of your options:\n" +
    " 1) Use a physical device for testing\n" +
    " 2) Start the emulator on a non-virtualized operating system\n" +
    " 3) Use an Android Virtual Device based on an ARM system image (This is 10x slower than hardware accelerated virtualization)\n";

  static final String SOLUTION_ACCELERATION_NOT_SUPPORTED =
    "Unfortunately, your computer does not support hardware accelerated virtualization.\n" +
    "Here are some of your options:\n" +
    " 1) Use a physical device for testing\n" +
    " 2) Develop on a Windows/OSX computer with an Intel processor that supports VT-x and NX\n" +
    " 3) Develop on a Linux computer that supports VT-x or SVM\n" +
    " 4) Use an Android Virtual Device based on an ARM system image\n" +
    "   (This is 10x slower than hardware accelerated virtualization)\n";

  static final String SOLUTION_TURN_OFF_HYPER_V =
    "Unfortunately, you cannot have Hyper-V running and use the emulator.\n" +
    "Here is what you can do:\n" +
    "  1) Start a command prompt as Administrator\n" +
    "  2) Run the following command: C:\\Windows\\system32> bcdedit /set hypervisorlaunchtype off\n" +
    "  3) Reboot your machine.\n";

  static final String SOLUTION_REBOOT_AFTER_TURNING_HYPER_V_OFF =
    "Hyper-V was successfully turned off. However a system restart is required for this to take effect.\n\n" +
    "Do you want to reboot now?\n";

  private static final AtomicBoolean ourRebootRequestedAsync = new AtomicBoolean(false);

  /**
   * Solution to problems that we can fix from Android Studio.
   */
  public enum SolutionCode {
    NONE("Troubleshoot"),
    DOWNLOAD_EMULATOR("Install Emulator"),
    UPDATE_EMULATOR("Update Emulator"),
    UPDATE_PLATFORM_TOOLS("Update Platform Tools"),
    UPDATE_SYSTEM_IMAGES("Update System Images"),
    INSTALL_KVM("Install KVM"),
    INSTALL_HAXM("Install HAXM"),
    REINSTALL_HAXM("Reinstall HAXM"),
    TURNOFF_HYPER_V("Turn off Hyper-V"),
    INSTALL_GVM("Install Android Emulator hypervisor driver"),
    REINSTALL_GVM("Reinstall Android Emulator hypervisor driver");

    private final String myDescription;

    public String getDescription() {
      return myDescription;
    }

    SolutionCode(@NotNull String shortDescription) {
      myDescription = shortDescription;
    }
  }

  /**
   * Returns a {@link Runnable} with code for applying the solution for a given problem {@link AccelerationErrorCode}.
   * In some cases all we can do is present some text to the user in which case the returned {@link Runnable} will simply
   * display a dialog box with text that the user can use as a guide for solving the problem on their own.<br>
   * In other cases we can install the component that is required.<br>
   * It is guaranteed that one and only one of the callbacks {@code refresh} and {@code cancel} is called if they are both supplied.
   * @param error the problem we are creating an action for
   * @param project the project (may be null but this circumvents certain updates)
   * @param refresh a {@link Runnable} to execute after a change has been applied
   * @param cancel a {@link Runnable} to execute if no change was applied
   * @return a {@link Runnable} to "fix" or display a "fix" for/to the user
   */
  public static Runnable getActionForFix(@NotNull AccelerationErrorCode error, @Nullable Project project, @Nullable Runnable refresh, @Nullable Runnable cancel) {
    return new AccelerationErrorSolution(error, project, refresh, cancel).getAction();
  }

  private final AccelerationErrorCode myError;
  private final Project myProject;
  private final Runnable myRefresh;
  private final Runnable myCancel;
  private boolean myChangesMade;

  private AccelerationErrorSolution(@NotNull AccelerationErrorCode error, @Nullable Project project, @Nullable Runnable refresh, @Nullable Runnable cancel) {
    assert error != AccelerationErrorCode.ALREADY_INSTALLED;
    myError = error;
    myProject = project;
    myRefresh = refresh;
    myCancel = cancel;
  }

  private Runnable getAction() {
    switch (myError.getSolution()) {
      case DOWNLOAD_EMULATOR:
      case UPDATE_EMULATOR:
        return () -> {
          try {
            showQuickFix(ImmutableList.of(SdkConstants.FD_EMULATOR));
          }
          finally {
            reportBack();
          }
        };

      case UPDATE_PLATFORM_TOOLS:
        return () -> {
          try {
            showQuickFix(ImmutableList.of(SdkConstants.FD_PLATFORM_TOOLS));
          }
          finally {
            reportBack();
          }
        };

      case UPDATE_SYSTEM_IMAGES:
        return () -> {
          try {
            AvdManagerConnection avdManager = AvdManagerConnection.getDefaultAvdManagerConnection();
            showQuickFix(avdManager.getSystemImageUpdates());
          }
          finally {
            reportBack();
          }
        };

      case INSTALL_KVM:
        return () -> {
          try {
            GeneralCommandLine install = createKvmInstallCommand();
            if (install == null) {
              BrowserUtil.browse(KVM_INSTRUCTIONS, myProject);
            }
            else {
              String text = String.format(
                "Linux systems vary a great deal; the installation steps we will attempt may not work in your particular scenario.\n\n" +
                "The steps are:\n\n"                                                                                                    +
                "  %1$s\n\n"                                                                                                            +
                "If you prefer, you can skip this step and perform the KVM installation steps on your own.\n\n"                         +
                "There might be more details at: %2$s\n", install.getCommandLineString(), KVM_INSTRUCTIONS);
              int response = Messages
                .showDialog(text, myError.getSolution().getDescription(), new String[]{"Skip", "Proceed"}, 1, Messages.getQuestionIcon());
              if (response == 1) {
                try {
                  execute(install);
                  myChangesMade = true;
                }
                catch (ExecutionException ex) {
                  LOG.error(ex);
                  BrowserUtil.browse(KVM_INSTRUCTIONS, myProject);
                  Messages.showWarningDialog(myProject,
                                             "Automatic KVM installation failed, please retry manually.\n\n" +
                                             "For more details on the automatic installation failure, " +
                                             "please consult the IDE log (Help | Show Log)",
                                             "Installation Failed");
                }
              }
              else {
                BrowserUtil.browse(KVM_INSTRUCTIONS, myProject);
              }
            }
          }
          finally {
            reportBack();
          }
        };

      case INSTALL_HAXM:
      case REINSTALL_HAXM:
      case INSTALL_GVM:
      case REINSTALL_GVM:
        final VmType type = myError.getSolution() == INSTALL_GVM || myError.getSolution() == REINSTALL_GVM ? VmType.GVM : VmType.HAXM;
        return () -> {
          try {
            VmWizard wizard = new VmWizard(false, type);
            wizard.init();
            myChangesMade = wizard.showAndGet();
          }
          finally {
            reportBack();
          }
        };
      case TURNOFF_HYPER_V:
        return () -> {
          try {
            GeneralCommandLine turnHyperVOff = new ElevatedCommandLine().withTempFilePrefix("turn_hypervisor_off");
            turnHyperVOff.setExePath("bcdedit");
            turnHyperVOff.addParameters("/set", "hypervisorlaunchtype", "off");
            turnHyperVOff.setWorkDirectory(FileUtilRt.getTempDirectory());
            try {
              execute(turnHyperVOff);
              promptAndReboot(SOLUTION_REBOOT_AFTER_TURNING_HYPER_V_OFF);
            }
            catch (ExecutionException ex) {
              LOG.error(ex);
              Messages.showWarningDialog(myProject, SOLUTION_TURN_OFF_HYPER_V, "Operation Failed");
            }
          }
          finally {
            reportBack();
          }
        };

      default:
        return () -> {
          try {
            Messages.showWarningDialog(myProject, myError.getSolutionMessage(), myError.getSolution().getDescription());
          }
          finally {
            reportBack();
          }
        };
    }
  }

  /**
   * Prompts the user to reboot now, and performs the reboot if accepted.
   * HAXM Installer may need a reboot only on Windows, so this method is intended to work only on Windows
   * and only for HAXM installer use case
   *
   * @param prompt the message to display to the user
   * @exception ExecutionException if the shutdown command fails to execute
   */
  public static void promptAndReboot(@NotNull String prompt) throws ExecutionException {
    int response = Messages
      .showOkCancelDialog((Project)null, prompt, "Reboot Now", "Reboot", "Cancel", Messages.getQuestionIcon());
    if (response == Messages.OK) {
      GeneralCommandLine reboot = new ElevatedCommandLine();
      reboot.setExePath("shutdown");
      reboot.addParameters("/g", "/t", "10");  // shutdown & restart after a 10 sec delay
      reboot.setWorkDirectory(FileUtilRt.getTempDirectory());
      execute(reboot);
    }
  }

  /**
   * Async version of {@link #promptAndReboot(String)}, which uses {@link ModalityState} to determine when to prompt for the reboot.
   * This is required when a reboot may be requested from multiple places during one wizard execution, and:
   * - we don't want the prompt to appear before the wizard finishes
   * - we don't want the prompt to appear several times
   *
   * If one reboot request is already queued, subsequent calls will be a no-op even when called with a different
   * {@link ModalityState} or reboot message. Once a prompt is released to the user and the choice has been made,
   * another call to this method will queue another reboot request.
   *
   * @param prompt the message to display to the user
   * @param modality ModalityState which determines when the reboot prompt will actually appear
   */
  public static void promptAndRebootAsync(@NotNull String prompt, @NotNull ModalityState modality) {
    if (ourRebootRequestedAsync.compareAndSet(false, true)) {
      ApplicationManager.getApplication().invokeLater(() -> {
        try {
          promptAndReboot(prompt);
        }
        catch (ExecutionException e) {
          LOG.warn("Automatic reboot attempt failed due to an exception", e);
          Messages.showErrorDialog("Reboot attempt failed. Please reboot manually", "Automatic Reboot");
        }
        ourRebootRequestedAsync.set(false);
      }, modality);
    }
  }

  @SuppressWarnings("SameParameterValue")
  private static String execute(@NotNull String command, String... parameters) throws ExecutionException {
    return execute(generateCommand(command, parameters));
  }

  private static GeneralCommandLine generateCommand(@NotNull String command, String... parameters) {
    GeneralCommandLine commandLine = new GeneralCommandLine();
    commandLine.setExePath(command);
    commandLine.addParameters(parameters);
    return commandLine;
  }

  private static String execute(@NotNull GeneralCommandLine commandLine) throws ExecutionException {
    int exitValue;
    CapturingAnsiEscapesAwareProcessHandler process = new CapturingAnsiEscapesAwareProcessHandler(commandLine);
    ProcessOutput output = process.runProcess();
    exitValue = output.getExitCode();
    if (exitValue == 0) {
      return output.getStdout();
    }
    else {
      throw new ExecutionException(String.format("Error running: %1$s", process.getCommandLine()));
    }
  }

  @Nullable
  private static GeneralCommandLine createKvmInstallCommand() {
    try {
      String version = execute("uname", "-r");
      Revision revision = toRevision(version);
      if (revision.compareTo(KARMIC_KERNEL) <= 0) {
        return generateCommand("gksudo", "aptitude -y", "install", "kvm", "libvirt-bin", "ubuntu-vm-builder", "bridge-utils");
      }
      else {
        return generateCommand("gksudo", "apt-get --assume-yes", "install", "qemu-kvm", "libvirt-bin", "ubuntu-vm-builder", "bridge-utils");
      }
    }
    catch (ExecutionException | NumberFormatException ex) {
      LOG.error(ex);
    }
    return null;
  }

  private static Revision toRevision(@NotNull String version) {
    int index = version.indexOf('-');
    if (index > 0) {
      version = version.substring(0, index);
    }
    return Revision.parseRevision(version);
  }

  private void showQuickFix(@NotNull List<String> requested) {
    ModelWizardDialog sdkQuickfixWizard = SdkQuickfixUtils.createDialogForPaths(myProject, requested);
    if (sdkQuickfixWizard != null) {
      sdkQuickfixWizard.show();
      if (sdkQuickfixWizard.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        myChangesMade = true;
      }
    }
  }

  private void reportBack() {
    Runnable reporter = myChangesMade ? myRefresh : myCancel;
    if (reporter != null) {
      ApplicationManager.getApplication().invokeLater(reporter);
    }
  }
}
