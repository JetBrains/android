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
package com.android.tools.idea.gradle.project.sync;

import static com.android.tools.idea.sdk.NdkPaths.validateAndroidNdk;
import static com.android.tools.sdk.SdkPaths.validateAndroidSdk;
import static com.intellij.openapi.util.io.FileUtil.filesEqual;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;

import com.android.tools.idea.IdeInfo;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.util.LocalProperties;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.sdk.AndroidSdkPath;
import com.android.tools.sdk.SdkPaths.ValidationResult;
import com.android.tools.idea.sdk.SelectSdkDialog;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ModalityUiUtil;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SdkSync {
  private static final String ERROR_DIALOG_TITLE = "Sync Android SDKs";

  @NotNull
  public static SdkSync getInstance() {
    return ApplicationManager.getApplication().getService(SdkSync.class);
  }

  public void syncIdeAndProjectAndroidSdks(@NotNull LocalProperties localProperties) {
    syncIdeAndProjectAndroidSdk(localProperties, new FindValidSdkPathTask(), null);
    syncIdeAndProjectAndroidNdk(localProperties);
  }

  @VisibleForTesting
  void syncIdeAndProjectAndroidSdk(@NotNull LocalProperties localProperties,
                                   @NotNull FindValidSdkPathTask findSdkPathTask,
                                   @Nullable Project project) {
    if (localProperties.hasAndroidDirProperty()) {
      // if android.dir is specified, we don't sync SDKs. User is working with SDK sources.
      return;
    }

    File ideAndroidSdkPath = IdeSdks.getInstance().getAndroidSdkPath();
    File projectAndroidSdkPath = localProperties.getAndroidSdkPath();

    if (ideAndroidSdkPath != null && projectAndroidSdkPath != null) {
      reconcileIdeAndProjectPaths(localProperties, project, ideAndroidSdkPath, projectAndroidSdkPath);
    }
    else if (ideAndroidSdkPath == null && projectAndroidSdkPath == null) {
      setIdeSdkAndProjectSdkByAskingUser(localProperties, findSdkPathTask);
    }
    else if (ideAndroidSdkPath != null) {
      setProjectSdkFromIdeSdk(localProperties, ideAndroidSdkPath);
    }
    else {
      setIdeSdkFromProjectSdk(localProperties, findSdkPathTask, projectAndroidSdkPath);
    }
  }

  /**
   * Updates the IDE SDK with the project's SDK, which may or may not be valid.
   * If it is invalid, the user will be prompted to select a valid one.
   */
  private void setIdeSdkFromProjectSdk(@NotNull LocalProperties localProperties,
                                       @NotNull FindValidSdkPathTask findSdkPathTask,
                                       @NotNull File projectAndroidSdkPath) {
    if (AndroidSdkPath.isValid(projectAndroidSdkPath)) {
      setIdeSdk(localProperties, projectAndroidSdkPath);
    }
    else if (IdeInfo.getInstance().isAndroidStudio()) {
      File selectedPath = findSdkPathTask.selectValidSdkPath();
      if (selectedPath == null) {
        throw new ExternalSystemException("Unable to continue until an Android SDK is specified");
      }
      setIdeSdk(localProperties, selectedPath);
    }
  }

  /**
   * Updates local.properties with the IDE SDK path. In IDEA, we don't want
   * local.properties to be created in plain java-gradle projects, so we update
   * local.properties only if the file exists.
   */
  private void setProjectSdkFromIdeSdk(@NotNull LocalProperties localProperties, @NotNull File ideAndroidSdkPath) {
    if (localProperties.getPropertiesFilePath().exists() || IdeInfo.getInstance().isAndroidStudio()) {
      setProjectSdk(localProperties, ideAndroidSdkPath);
    }
  }

  /**
   * Sets the IDE SDK in the case where neither the IDE nor the project has an
   * SDK defined. In IDEA, there are non-Android gradle projects. IDEA should
   * not create local.properties and should not ask users to configure the
   * Android SDK unless we're sure that they are working with Android projects.
   */
  private void setIdeSdkAndProjectSdkByAskingUser(@NotNull LocalProperties localProperties, @NotNull FindValidSdkPathTask findSdkPathTask) {
    if (IdeInfo.getInstance().isAndroidStudio()) {
      File selectedPath = findSdkPathTask.selectValidSdkPath();
      if (selectedPath == null) {
        throw new ExternalSystemException("Unable to continue until an Android SDK is specified");
      }
      setIdeSdk(localProperties, selectedPath);
    }
  }

  /**
   * When both the IDE SDK and the Android SDK are defined, we have to choose
   * which to use. If the Android SDK validates, we go with that. Otherwise, we
   * ask the user to pick between the two.
   */
  private void reconcileIdeAndProjectPaths(@NotNull LocalProperties localProperties,
                                           @Nullable Project project,
                                           @NotNull File ideAndroidSdkPath,
                                           @NotNull File projectAndroidSdkPath) {
    ValidationResult validationResult = validateAndroidSdk(projectAndroidSdkPath.toPath(), true);
    if (!validationResult.success) {
      // If we have the IDE default SDK but not a valid project SDK, update local.properties with the default SDK path and exit.
      ApplicationManager.getApplication().invokeAndWait(() -> {
        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          String error = validationResult.message;
          if (isEmpty(error)) {
            error = String.format("The path \n'%1$s'\n" + "does not refer to a valid Android SDK.", projectAndroidSdkPath.getPath());
          }
          String format =
            "%1$s\n\n%3$s will use this Android SDK instead:\n'%2$s'\nand will modify the project's local.properties file.";
          Messages.showErrorDialog(String.format(format, error, ideAndroidSdkPath.getPath(),
                                                 ApplicationNamesInfo.getInstance().getFullProductName()),
                                   ERROR_DIALOG_TITLE);
        }
        setProjectSdk(localProperties, ideAndroidSdkPath);
      });
      return;
    }

    if (!filesEqual(ideAndroidSdkPath, projectAndroidSdkPath)) {
      String msg = String.format("The project and %3$s point to different Android SDKs.\n\n" +
                                 "%3$s's default SDK is in:\n" +
                                 "%1$s\n\n" +
                                 "The project's SDK (specified in local.properties) is in:\n" +
                                 "%2$s\n\n" +
                                 "To keep results consistent between IDE and command line builds, only one path can be used. " +
                                 "Do you want to:\n\n" +
                                 "[1] Use %3$s's default SDK (modifies the project's local.properties file.)\n\n" +
                                 "[2] Use the project's SDK (modifies %3$s's default.)\n\n" +
                                 "Note that switching SDKs could cause compile errors if the selected SDK doesn't have the " +
                                 "necessary Android platforms or build tools.",
                                 ideAndroidSdkPath.getPath(), projectAndroidSdkPath.getPath(),
                                 ApplicationNamesInfo.getInstance().getFullProductName());
      ApplicationManager.getApplication().invokeAndWait(() -> {
        // We need to pass the project, so on Mac, the "Mac sheet" showing this message shows inside the IDE during UI tests, otherwise
        // it will show outside and the UI testing infrastructure cannot see it. It is overall a good practice to pass the project when
        // showing a message, to ensure that the message shows in the IDE instance containing the project.
        boolean userChoseYes = MessageDialogBuilder.yesNo("Android SDK Manager", msg)
          .yesText("Use " + ApplicationNamesInfo.getInstance().getFullProductName() + "'s SDK")
          .noText("Use Project's SDK")
          .ask(project);
        if (userChoseYes) {
          // Use Android Studio's SDK
          setProjectSdk(localProperties, ideAndroidSdkPath);
        }
        else {
          // Use project's SDK
          setIdeSdk(localProperties, projectAndroidSdkPath);
        }
      });
    }
  }

  private void syncIdeAndProjectAndroidNdk(@NotNull LocalProperties localProperties) {
    if (StudioFlags.NDK_SIDE_BY_SIDE_ENABLED.get()) {
      // When side-by-side NDK is enabled, don't force ndk.dir. Instead, the more
      // recent gradle plugin will decide what the correct NDK folder is.
      // If this is an older plugin that doesn't support side-by-side NDK then
      // there may be a sync error about missing NDK. This should be fixed up after
      // the sync failure with error handlers.
      return;
    }
    File projectAndroidNdkPath = localProperties.getAndroidNdkPath();
    File ideAndroidNdkPath = IdeSdks.getInstance().getAndroidNdkPath();

    if (projectAndroidNdkPath != null) {
      if (!validateAndroidNdk(projectAndroidNdkPath.toPath(), false).success) {
        if (ideAndroidNdkPath != null) {
          Logger.getInstance(SdkSync.class).warn(String.format("Replacing invalid NDK path %1$s with %2$s",
                                                               projectAndroidNdkPath, ideAndroidNdkPath));
          setProjectNdk(localProperties, ideAndroidNdkPath);
          return;
        }
        Logger.getInstance(SdkSync.class).warn(String.format("Removing invalid NDK path: %s", projectAndroidNdkPath));
        setProjectNdk(localProperties, null);
      }
      return;
    }
    setProjectNdk(localProperties, ideAndroidNdkPath);
  }

  private static void setProjectNdk(@NotNull LocalProperties localProperties, @Nullable File ndkPath) {
    File currentNdkPath = localProperties.getAndroidNdkPath();
    if (filesEqual(currentNdkPath, ndkPath)) {
      return;
    }
    localProperties.setAndroidNdkPath(ndkPath);
    try {
      localProperties.save();
    }
    catch (IOException e) {
      String msg = String.format("Unable to save '%1$s'", localProperties.getPropertiesFilePath().getPath());
      throw new ExternalSystemException(msg, e);
    }
  }

  private void setIdeSdk(@NotNull LocalProperties localProperties, @NotNull File projectAndroidSdkPath) {
    // There is one case where DefaultSdks.setAndroidSdkPath will not update local.properties in the project. The conditions for this to
    // happen are:
    // 1. This is a fresh installation of Android Studio and user does not set Android SDK
    // 2. User imports a project that does not have a local.properties file
    // Just to be on the safe side, we update local.properties.
    setProjectSdk(localProperties, projectAndroidSdkPath);

    ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState(),
                                       () -> ApplicationManager.getApplication().runWriteAction(() -> {
                                         IdeSdks.getInstance().setAndroidSdkPath(projectAndroidSdkPath);
                                       }));
  }

  private static void setProjectSdk(@NotNull LocalProperties localProperties, @NotNull File androidSdkPath) {
    if (filesEqual(localProperties.getAndroidSdkPath(), androidSdkPath)) {
      return;
    }
    localProperties.setAndroidSdkPath(androidSdkPath);
    try {
      localProperties.save();
    }
    catch (IOException e) {
      String msg = String.format("Unable to save '%1$s'", localProperties.getPropertiesFilePath().getPath());
      throw new ExternalSystemException(msg, e);
    }
  }

  @VisibleForTesting
  static class FindValidSdkPathTask { // TODO: rename to "AskUserToProvideValidSdkPathTask"
    @Nullable
    File selectValidSdkPath() {
      Ref<File> pathRef = new Ref<>();
      ApplicationManager.getApplication().invokeAndWait(() -> findValidSdkPath(pathRef));
      return pathRef.get();
    }

    private static void findValidSdkPath(@NotNull Ref<File> pathRef) {
      Sdk jdk = IdeSdks.getInstance().getJdk();
      String jdkPath = jdk != null ? jdk.getHomePath() : null;
      SelectSdkDialog dialog = new SelectSdkDialog(jdkPath, null);
      dialog.setModal(true);
      if (!dialog.showAndGet()) {
        String msg = "An Android SDK is needed to continue. Would you like to try again?";
        if (MessageDialogBuilder.yesNo(ERROR_DIALOG_TITLE, msg).ask((Project)null)) {
          findValidSdkPath(pathRef);
        }
        return;
      }
      File path = new File(dialog.getAndroidHome());
      if (!AndroidSdkPath.isValid(path)) {
        String format = "The path\n'%1$s'\ndoes not refer to a valid Android SDK. Would you like to try again?";
        if (MessageDialogBuilder.yesNo(ERROR_DIALOG_TITLE, String.format(format, path.getPath())).ask((Project)null)) {
          findValidSdkPath(pathRef);
        }
        return;
      }
      pathRef.set(path);
    }
  }
}
