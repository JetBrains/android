/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync.sdk;

import static com.android.SdkConstants.FN_FRAMEWORK_LIBRARY;
import static com.android.SdkConstants.RES_FOLDER;

import com.android.tools.idea.updater.configure.SdkUpdaterConfigurableProvider;
import com.android.tools.sdk.AndroidPlatform;
import com.google.idea.blaze.android.projectview.AndroidSdkPlatformSection;
import com.google.idea.blaze.android.sdk.BlazeSdkProvider;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.logging.EventLoggingService;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import java.util.Optional;
import org.jetbrains.android.sdk.AndroidPlatforms;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** SDK utilities. */
public class SdkUtil {
  private static final Logger logger = Logger.getInstance(SdkUtil.class);

  @Nullable
  private static String getAndroidSdkPlatform(Project project, BlazeProjectData blazeProjectData) {
    // TODO(b/271874279): Retrieve sdk from project data
    if (Blaze.getProjectType(project) == ProjectType.QUERY_SYNC) {
      ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
      if (projectViewSet == null) {
        return null;
      }
      return projectViewSet.getScalarValue(AndroidSdkPlatformSection.KEY).orElse(null);
    }

    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    return Optional.ofNullable(syncData)
        .map(data -> data.androidSdkPlatform)
        .map(sdk -> sdk.androidSdk)
        .orElse(null);
  }

  @Nullable
  public static AndroidPlatform getAndroidPlatform(@NotNull Project project) {
    BlazeProjectData blazeProjectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (blazeProjectData == null) {
      return null;
    }
    String androidSdkPlatform = getAndroidSdkPlatform(project, blazeProjectData);
    if (androidSdkPlatform == null) {
      return null;
    }
    Sdk sdk = BlazeSdkProvider.getInstance().findSdk(androidSdkPlatform);
    if (sdk == null) {
      return null;
    }
    return AndroidPlatforms.getInstance(sdk);
  }

  /** Opens the SDK manager settings page */
  public static void openSdkManager() {
    Configurable configurable =
        ConfigurableExtensionPointUtil.createApplicationConfigurableForProvider(
            SdkUpdaterConfigurableProvider.class);
    ShowSettingsUtil.getInstance().showSettingsDialog(null, configurable.getClass());
  }

  public static boolean containsJarAndRes(Sdk sdk) {
    VirtualFile[] classes = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    // A valid sdk must contains path to android.jar and res
    if (classes.length < 2) {
      return false;
    }
    boolean hasJar = false;
    boolean hasRes = false;
    for (VirtualFile file : classes) {
      if (FN_FRAMEWORK_LIBRARY.equals(file.getName())) {
        hasJar = true;
      }
      if (RES_FOLDER.equals(file.getName())) {
        hasRes = true;
      }
    }
    return hasJar && hasRes;
  }

  /**
   * Check if sdk is not null and have path to expected files (android.jar and res/). If it does not
   * have expected content, this sdk will be removed from jdktable.
   *
   * @param sdk sdk to check
   * @return true if sdk is valid
   */
  public static boolean checkSdkAndRemoveIfInvalid(@Nullable Sdk sdk) {
    if (sdk == null) {
      return false;
    } else if (containsJarAndRes(sdk)) {
      return true;
    } else {
      ProjectJdkTable jdkTable = ProjectJdkTable.getInstance();
      logger.info(
          String.format(
              "Some classes of Sdk %s is missing. Trying to remove and reinstall it.",
              sdk.getName()));
      EventLoggingService.getInstance().logEvent(SdkUtil.class, "Invalid SDK");

      Application application = ApplicationManager.getApplication();
      if (application.isDispatchThread()) {
        WriteAction.run(() -> jdkTable.removeJdk(sdk));
      } else {
        UIUtil.invokeAndWaitIfNeeded(
            (Runnable) () -> WriteAction.run(() -> jdkTable.removeJdk(sdk)));
      }
      return false;
    }
  }
}
