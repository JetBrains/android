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

import com.android.repository.Revision;
import com.android.sdklib.repository.descriptors.IPkgDesc;
import com.android.sdklib.repository.descriptors.PkgDesc;
import com.android.tools.idea.gradle.project.GradleProjectImporter;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.CapturingAnsiEscapesAwareProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.avdmanager.AvdManagerConnection.PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2;
import static com.android.tools.idea.avdmanager.AvdManagerConnection.TOOLS_REVISION_WITH_FIRST_QEMU2;

/**
 * Solution strings used in {@link AccelerationErrorCode}.
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

  static final String SOLUTION_ACCERATION_NOT_SUPPORTED =
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
    INSTALL_HAXM("Install Haxm"),
    REINSTALL_HAXM("Reinstall Haxm");

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
   * display a dialog box with text that the user can use as a guide for solving the problem on their own.</br>
   * In other cases we can install the component that is required.
   * @param error the problem we are creating an action for
   * @param project the project (may be null but this circumvents certain updates)
   * @param refresh a {@link Runnable} to execute after a change has been applied
   * @return a {link Runnable} to "fix" or display a "fix" for/to the user
   */
  public static Runnable getActionForFix(@NotNull AccelerationErrorCode error, @Nullable Project project, @Nullable Runnable refresh) {
    return new AccelerationErrorSolution(error, project, refresh).getAction();
  }

  private final AccelerationErrorCode myError;
  private final Project myProject;
  private final Runnable myRefresh;

  private AccelerationErrorSolution(@NotNull AccelerationErrorCode error, @Nullable Project project, @Nullable Runnable refresh) {
    assert error != AccelerationErrorCode.ALREADY_INSTALLED;
    myError = error;
    myProject = project;
    myRefresh = refresh;
  }

  private  Runnable getAction() {
    switch (myError.getSolution()) {
      case DOWNLOAD_EMULATOR:
      case UPDATE_EMULATOR:
        return new Runnable() {
          @Override
          public void run() {
            List<IPkgDesc> requested = Lists.newArrayList();
            requested.add(PkgDesc.Builder.newTool(TOOLS_REVISION_WITH_FIRST_QEMU2, PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2).create());
            showQuickFix(requested);
          }
        };

      case UPDATE_PLATFORM_TOOLS:
        return new Runnable() {
          @Override
          public void run() {
            List<IPkgDesc> requested = Lists.newArrayList();
            requested.add(PkgDesc.Builder.newPlatformTool(PLATFORM_TOOLS_REVISION_WITH_FIRST_QEMU2).create());
            showQuickFix(requested);
          }
        };

      case UPDATE_SYSTEM_IMAGES:
        return new Runnable() {
          @Override
          public void run() {
            AvdManagerConnection avdManager = AvdManagerConnection.getDefaultAvdManagerConnection();
            List<IPkgDesc> requested = avdManager.getSystemImageUpdates();
            showQuickFix(requested);
          }
        };

      case INSTALL_KVM:
        return new Runnable() {
          @Override
          public void run() {
            GeneralCommandLine install = createKvmInstallCommand();
            if (install == null) {
              BrowserUtil.browse(KVM_INSTRUCTIONS, myProject);
              refresh();
            }
            else {
              String text = String.format(
                "Linux systems vary a great deal; the installation steps we will attempt may not work in your particular scenario.\n\n" +
                "The steps are:\n\n" +
                "  %1$s\n\n" +
                "If you prefer, you can skip this step and perform the KVM installation steps on your own.\n\n" +
                "There might be more details at: %2$s\n",
                install.getCommandLineString(),
                KVM_INSTRUCTIONS);
              int response = Messages.showDialog(
                text, myError.getSolution().getDescription(), new String[]{"Skip", "Proceed"}, 1, Messages.getQuestionIcon());
              if (response == 1) {
                try {
                  execute(install);
                  refresh();
                }
                catch (ExecutionException ex) {
                  LOG.error(ex);
                  BrowserUtil.browse(KVM_INSTRUCTIONS, myProject);
                  Messages.showWarningDialog(myProject, "Please install KVM on your own", "Installation Failed");
                  refresh();
                }
              }
              else {
                BrowserUtil.browse(KVM_INSTRUCTIONS, myProject);
                refresh();
              }
            }
          }
        };

      case INSTALL_HAXM:
      case REINSTALL_HAXM:
        return new Runnable() {
          @Override
          public void run() {
            HaxmWizard wizard = new HaxmWizard();
            wizard.init();
            if (wizard.showAndGet()) {
              refresh();
            }
          }
        };

      default:
        return new Runnable() {
          @Override
          public void run() {
            Messages.showWarningDialog(myProject, myError.getSolutionMessage(), myError.getSolution().getDescription());
            refresh();
          }
        };
    }
  }

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
    catch (ExecutionException ex) {
      LOG.error(ex);
    }
    catch (NumberFormatException ex) {
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

  private void showQuickFix(@NotNull List<IPkgDesc> requested) {
    ModelWizardDialog sdkQuickfixWizard = SdkQuickfixUtils.createDialog(null, requested);
    if (sdkQuickfixWizard != null) {
      sdkQuickfixWizard.show();
      if (sdkQuickfixWizard.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        if (myProject != null) {
          GradleProjectImporter.getInstance().requestProjectSync(myProject, null);
        }
        refresh();
      }
    }
  }

  private void refresh() {
    if (myRefresh != null) {
      ApplicationManager.getApplication().invokeLater(myRefresh);
    }
  }
}
