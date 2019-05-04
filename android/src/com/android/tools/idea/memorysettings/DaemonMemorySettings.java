/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.memorysettings;

import static com.android.tools.idea.memorysettings.GradlePropertiesUtil.getGradleDaemonXmx;
import static com.android.tools.idea.memorysettings.GradlePropertiesUtil.getKotlinDaemonXmx;
import static com.android.tools.idea.memorysettings.GradlePropertiesUtil.setDaemonXmx;
import static com.intellij.notification.NotificationType.ERROR;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.tools.idea.gradle.util.GradleProperties;
import com.android.tools.idea.project.AndroidNotification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

public class DaemonMemorySettings {
  private static final Logger LOG = Logger.getInstance(DaemonMemorySettings.class);

  private static final int LOW_GRADLE_DAEMON_XMX_IN_MB = 1024;
  private static final int HIGH_GRADLE_DAEMON_XMX_IN_MB = 1536;

  static final int MAX_GRADLE_DAEMON_XMX_IN_MB = 2048;
  static final int MAX_KOTLIN_DAEMON_XMX_IN_MB = 2048;

  private Project project;

  DaemonMemorySettings(Project project) {
    this.project = project;
  }

  int getDefaultGradleDaemonXmx() {
    return SystemInfo.is32Bit ? LOW_GRADLE_DAEMON_XMX_IN_MB : HIGH_GRADLE_DAEMON_XMX_IN_MB;
  }

  int getProjectGradleDaemonXmx() {
    return getGradleDaemonXmx(getProjectProperties());
  }

  int getDefaultKotlinDaemonXmx() {
    // Kotlin Daemon inherits the memory settings from the Gradle daemon, unless specified explicitly.
    return getProjectGradleDaemonXmx();
  }

  int getProjectKotlinDaemonXmx() {
    int xmx = getKotlinDaemonXmx(getProjectProperties());
    return xmx > 0 ? xmx : getDefaultKotlinDaemonXmx();
  }

  @Nullable
  GradleProperties getProjectProperties() {
    try {
      return project != null ? new GradleProperties(project) : null;
    }
    catch (IOException e) {
      return null;
    }
  }

  void saveProjectDaemonXmx(int newGradleValue, int newKotlinValue) {
    LOG.info(String.format(Locale.US, "saving new daemon Xmx value: Gradle %d, Kotlin %d", newGradleValue, newKotlinValue));
    GradleProperties properties = getProjectProperties();
    if (properties == null) {
      reportSaveError("Null gradle properties", null);
    }
    else {
      try {
        setDaemonXmx(properties, newGradleValue, newKotlinValue);
      }
      catch (IOException e) {
        String err = "Failed to save new Xmx value to gradle.properties";
        reportSaveError(err, e);
      }
    }
  }

  private void reportSaveError(String message, @Nullable Exception e) {
    LOG.info(message, e);
    if (e != null) {
      String cause = e.getMessage();
      if (isNotEmpty(cause)) {
        message += String.format("<br>\nCause: %1$s", cause);
      }
    }
    AndroidNotification.getInstance(project).showBalloon("Gradle Settings", message, ERROR);
  }
}
