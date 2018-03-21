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
package com.android.tools.idea.gradle.project.sync.cleanup;

import com.android.tools.idea.gradle.project.ProxySettingsDialog;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.ProxySettings;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.net.HttpConfigurable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.GradleProperties.getUserGradlePropertiesFile;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ExceptionUtil.getRootCause;

// See https://code.google.com/p/android/issues/detail?id=169743
class HttpProxySettingsCleanUpTask extends AndroidStudioCleanUpTask {
  @Override
  void doCleanUp(@NotNull Project project) {
    HttpConfigurable ideHttpProxySettings = HttpConfigurable.getInstance();
    if (!ideHttpProxySettings.USE_HTTP_PROXY || isEmpty(ideHttpProxySettings.PROXY_HOST)) {
      return;
    }
    GradleProperties properties;
    try {
      File userPropertiesFile = getUserGradlePropertiesFile();
      properties = new GradleProperties(userPropertiesFile);
    }
    catch (IOException e) {
      getLogger().info("Failed to read gradle.properties file", e);
      // Let sync continue, even though it may fail.
      return;
    }
    ProxySettings gradleProxySettings = properties.getHttpProxySettings();
    ProxySettings ideProxySettings = new ProxySettings(ideHttpProxySettings);

    if (!ideProxySettings.equals(gradleProxySettings)) {
      ProxySettingsDialog dialog = new ProxySettingsDialog(project, ideProxySettings);

      if (dialog.showAndGet()) {
        dialog.applyProxySettings(properties.getProperties());
        try {
          properties.save();
        }
        catch (IOException e) {
          Throwable root = getRootCause(e);

          String cause = root.getMessage();
          String errMsg = "Failed to save HTTP proxy settings to gradle.properties file.";
          if (isNotEmpty(cause)) {
            if (!cause.endsWith(".")) {
              cause += ".";
            }
            errMsg += String.format("\nCause: %1$s", cause);
          }

          AndroidNotification notification = AndroidNotification.getInstance(project);
          notification.showBalloon("Proxy Settings", errMsg, ERROR);

          getLogger().info("Failed to save changes to gradle.properties file", root);
        }
      }
    }
  }

  @NotNull
  private Logger getLogger() {
    return Logger.getInstance(getClass());
  }
}
