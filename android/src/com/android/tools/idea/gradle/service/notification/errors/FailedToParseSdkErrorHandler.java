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
package com.android.tools.idea.gradle.service.notification.errors;

import com.android.SdkConstants;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

import static org.jetbrains.android.sdk.AndroidSdkUtils.isAndroidSdk;

public class FailedToParseSdkErrorHandler extends AbstractSyncErrorHandler {
  @NonNls public static final String FAILED_TO_PARSE_SDK_ERROR = "failed to parse SDK";

  @Override
  public boolean handleError(@NotNull List<String> message,
                             @NotNull ExternalSystemException error,
                             @NotNull NotificationData notification,
                             @NotNull Project project) {
    String msg = message.get(0);
    if (!msg.contains(FAILED_TO_PARSE_SDK_ERROR)) {
      return false;
    }
    File pathOfBrokenSdk = findPathOfSdkMissingOrEmptyAddonsFolder(project);
    String newMsg;
    if (pathOfBrokenSdk != null) {
      newMsg = String.format("The directory '%1$s', in the Android SDK at '%2$s', is either missing or empty", SdkConstants.FD_ADDONS,
                             pathOfBrokenSdk);
      if (!pathOfBrokenSdk.canWrite()) {
        String format = "\n\nCurrent user (%1$s) does not have write access to the SDK directory.";
        newMsg += String.format(format, SystemProperties.getUserName());
      }
    }
    else {
      newMsg = msg;
    }
    updateNotification(notification, project, newMsg);
    return true;
  }

  @Nullable
  private static File findPathOfSdkMissingOrEmptyAddonsFolder(@NotNull Project project) {
    ModuleManager moduleManager = ModuleManager.getInstance(project);
    for (Module module : moduleManager.getModules()) {
      Sdk moduleSdk = ModuleRootManager.getInstance(module).getSdk();
      if (moduleSdk != null && isAndroidSdk(moduleSdk)) {
        String homePath = moduleSdk.getHomePath();
        if (homePath != null) {
          File sdkHomeDirPath = new File(FileUtil.toSystemDependentName(homePath));
          File addonsDir = new File(sdkHomeDirPath, SdkConstants.FD_ADDONS);
          if (!addonsDir.isDirectory() || FileUtil.notNullize(addonsDir.listFiles()).length == 0) {
            return sdkHomeDirPath;
          }
        }
      }
    }
    return null;
  }
}
