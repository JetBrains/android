/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea;

import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.notification.NotificationType.WARNING;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.ExceptionUtil.getRootCause;

import com.android.tools.idea.gradle.project.ProxySettingsDialog;
import com.android.tools.idea.gradle.project.sync.hyperlink.OpenFileHyperlink;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.gradle.util.ProxySettings;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.net.HttpConfigurable;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

// See https://code.google.com/p/android/issues/detail?id=169743
public final class HttpProxySettingsCleanUp {
  public static void cleanUp(@NotNull Project project) {
    HttpConfigurable ideHttpProxySettings = HttpConfigurable.getInstance();
    boolean usingProxy = (ideHttpProxySettings.USE_HTTP_PROXY && isNotEmpty(ideHttpProxySettings.PROXY_HOST))
                         || ideHttpProxySettings.USE_PROXY_PAC;
    if (!usingProxy) {
      return;
    }
    GradleProperties properties;
    try {
      properties = new GradleProperties(GradleUtil.getUserGradlePropertiesFile(project));
    }
    catch (IOException e) {
      Logger.getInstance(HttpProxySettingsCleanUp.class).info("Failed to read gradle.properties file", e);
      // Let sync continue, even though it may fail.
      return;
    }
    ProxySettings gradleProxySettings = properties.getHttpProxySettings();
    ProxySettingsDialog dialog = null;

    if (ideHttpProxySettings.USE_PROXY_PAC) {
      // Confirm current configuration from the gradle.properties file (see b/135102054)
      if (isEmpty(gradleProxySettings.getHost()) || !properties.getProperties().containsKey("systemProp.http.proxyPort")) {
        dialog = new ProxySettingsDialog(project, gradleProxySettings);
      }
    }
    else {
      // Show proxy settings dialog only if the IDE configuration is different to Gradle's
      ProxySettings ideProxySettings = new ProxySettings(ideHttpProxySettings);
      if (!ideProxySettings.equals(gradleProxySettings)) {
        dialog = new ProxySettingsDialog(project, ideProxySettings);
      }
    }

    if (dialog != null) {
      if (dialog.showAndGet()) {
        boolean needsPassword = dialog.applyProxySettings(properties.getProperties());
        try {
          properties.save();
          if (needsPassword) {
            String msg = "Proxy passwords are not defined.";
            OpenFileHyperlink openLink = new OpenFileHyperlink(GradleUtil.getUserGradlePropertiesFile(project).getPath());
            AndroidNotification.getInstance(project).showBalloon("Proxy Settings", msg, WARNING, openLink);
          }
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

          Logger.getInstance(HttpProxySettingsCleanUp.class).info("Failed to save changes to gradle.properties file", root);
        }
      }
    }
  }
}
