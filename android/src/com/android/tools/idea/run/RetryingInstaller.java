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
package com.android.tools.idea.run;

import com.android.annotations.VisibleForTesting;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.InstallException;
import com.android.tools.idea.run.util.LaunchStatus;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

/**
 * {@link RetryingInstaller} encapsulates the logic to re-try apk installation attempts when installation fails.
 *
 * When APK installation fails, the package manager typically just gives an error code/string. This class maps some of the commonly known
 * ones to more understandable descriptions, prompts the users whether they want to attempt re-installation, etc.
 */
public class RetryingInstaller {
  private final IDevice myDevice;
  private final Installer myInstaller;
  private final String myApplicationId;
  private final ConsolePrinter myPrinter;
  private final LaunchStatus myLaunchStatus;
  private final Prompter myPrompter;

  public interface Installer {
    @NotNull
    InstallResult installApp(@NotNull IDevice device, @NotNull LaunchStatus launchStatus);
  }

  public interface Prompter {
    boolean showQuestionPrompt(@NotNull String message);

    void showErrorMessage(@NotNull String message);
  }

  public RetryingInstaller(@NotNull Project project,
                           @NotNull IDevice device,
                           @NotNull Installer installer,
                           @NotNull String applicationId,
                           @NotNull ConsolePrinter printer,
                           @NotNull LaunchStatus launchStatus) {
    this(device, installer, applicationId, new UserPrompter(project), printer, launchStatus);
  }

  @VisibleForTesting
  public RetryingInstaller(@NotNull IDevice device,
                           @NotNull Installer installer,
                           @NotNull String applicationId,
                           @NotNull Prompter prompter,
                           @NotNull ConsolePrinter printer,
                           @NotNull LaunchStatus launchStatus) {
    myDevice = device;
    myInstaller = installer;
    myApplicationId = applicationId;
    myPrompter = prompter;
    myPrinter = printer;
    myLaunchStatus = launchStatus;
  }

  public boolean install() {
    InstallResult result = null;
    boolean retry = true;

    while (!myLaunchStatus.isLaunchTerminated() && retry) {
      result = myInstaller.installApp(myDevice, myLaunchStatus);
      if (result.installOutput != null) {
        if (result.failureCode == InstallResult.FailureCode.NO_ERROR) {
          myPrinter.stdout(result.installOutput);
        }
        else {
          myPrinter.stderr(result.installOutput);
        }
      }

      String reason;
      switch (result.failureCode) {
        case DEVICE_NOT_RESPONDING:
          int waitTime = 2;
          myPrinter.stdout("Device is not ready. Waiting for " + waitTime + " seconds.");
          try {
            TimeUnit.SECONDS.sleep(waitTime);
          }
          catch (InterruptedException e) {
            Logger.getInstance(RetryingInstaller.class).info(e);
          }
          retry = true;
          break;
        case INSTALL_FAILED_VERSION_DOWNGRADE:
          reason = AndroidBundle
            .message("deployment.failed.uninstall.prompt.text", AndroidBundle.message("deployment.failed.reason.version.downgrade"));
          retry = myPrompter.showQuestionPrompt(reason) && uninstallPackage(myDevice, myApplicationId);
          break;
        case INSTALL_FAILED_UPDATE_INCOMPATIBLE:
        case INCONSISTENT_CERTIFICATES:
          reason = AndroidBundle
            .message("deployment.failed.uninstall.prompt.text", AndroidBundle.message("deployment.failed.reason.different.signature"));
          retry = myPrompter.showQuestionPrompt(reason) && uninstallPackage(myDevice, myApplicationId);
          break;
        case INSTALL_FAILED_DEXOPT:
          reason =
            AndroidBundle.message("deployment.failed.uninstall.prompt.text", AndroidBundle.message("deployment.failed.reason.dexopt"));
          retry = myPrompter.showQuestionPrompt(reason) && uninstallPackage(myDevice, myApplicationId);
          break;
        case NO_CERTIFICATE:
          myPrinter.stderr(AndroidBundle.message("deployment.failed.no.certificates.explanation"));
          myPrompter.showErrorMessage(AndroidBundle.message("deployment.failed.no.certificates.explanation"));
          retry = false;
          break;
        case INSTALL_FAILED_OLDER_SDK: // TODO: this should not happen and should have been caught by the device picker
          String message = AndroidBundle.message("deployment.failed.reason.oldersdk", myDevice.getVersion().toString());
          myPrinter.stderr(message);
          retry = false;
          break;
        case DEVICE_NOT_FOUND:
          reason = AndroidBundle.message("deployment.failed.reason.devicedisconnected", myDevice.getName());
          myPrompter.showErrorMessage(reason);
          retry = false;
          break;
        case UNTYPED_ERROR:
          reason = AndroidBundle.message("deployment.failed.uninstall.prompt.generic.text", result.failureMessage);
          retry = myPrompter.showQuestionPrompt(reason) && uninstallPackage(myDevice, myApplicationId);
          break;
        default:
          retry = false;
          break;
      }
    }

    return result != null && result.failureCode == InstallResult.FailureCode.NO_ERROR;
  }

  public static class UserPrompter implements Prompter {
    private final Project myProject;

    public UserPrompter(@NotNull Project project) {
      myProject = project;
    }

    @Override
    public boolean showQuestionPrompt(@NotNull String message) {
      return UIUtil.invokeAndWaitIfNeeded(() -> {
        int result = Messages.showOkCancelDialog(
          myProject,
          message,
          AndroidBundle.message("deployment.failed.title"),
          Messages.getQuestionIcon());
        return result == Messages.OK;
      });
    }

    @Override
    public void showErrorMessage(@NotNull String message) {
      ApplicationManager.getApplication().invokeLater(() -> Messages.showErrorDialog(
        myProject,
        message,
        AndroidBundle.message("deployment.failed.title")));
    }
  }

  private boolean uninstallPackage(@NotNull IDevice device, @NotNull String packageName) {
    myPrinter.stdout("$ adb shell pm uninstall " + packageName);
    String output;
    try {
      output = device.uninstallPackage(packageName);
    }
    catch (InstallException e) {
      return false;
    }

    if (output != null) {
      myPrinter.stderr(output);
      return false;
    }
    return true;
  }
}
