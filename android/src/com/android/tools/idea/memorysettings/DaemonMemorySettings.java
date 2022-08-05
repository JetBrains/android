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
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.system.CpuArch;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * This class specifies both user-level and project-level Gradle properties for a project.
 * The user-level properties take priority over the project-level ones.
 * See <a href="https://docs.gradle.org/current/userguide/build_environment.html#sec:gradle_configuration_properties">Gradle doc</a>
 */
public class DaemonMemorySettings {
  private static final Logger LOG = Logger.getInstance(DaemonMemorySettings.class);
  private static final int LOW_GRADLE_DAEMON_XMX_IN_MB = 1024;
  private static final int HIGH_GRADLE_DAEMON_XMX_IN_MB = 1536;

  static final int MAX_GRADLE_DAEMON_XMX_IN_MB = 2048;
  static final int MAX_KOTLIN_DAEMON_XMX_IN_MB = 2048;

  private final Project project;
  private final GradleProperties projectProperties;
  private final GradleUserProperties userProperties;

  DaemonMemorySettings(Project project) {
    this.project = project;
    this.projectProperties = getProjectProperties();
    this.userProperties = new GradleUserProperties(project);
  }

  @VisibleForTesting
  DaemonMemorySettings(Project project, GradleProperties projectProperties) {
    this.project = project;
    this.projectProperties = projectProperties;
    this.userProperties = new GradleUserProperties(project);
  }

  int getDefaultGradleDaemonXmx() {
    return CpuArch.is32Bit() ? LOW_GRADLE_DAEMON_XMX_IN_MB : HIGH_GRADLE_DAEMON_XMX_IN_MB;
  }

  boolean hasUserPropertiesPath() {
    return userProperties.getPropertiesPath() != null;
  }

  String getUserPropertiesPath() {
    File path = userProperties.getPropertiesPath();
    return path == null ? null : path.getPath();
  }

  int getProjectGradleDaemonXmx() {
    return hasUserPropertiesPath() ? userProperties.getGradleXmx() : getGradleDaemonXmx(projectProperties);
  }

  int getDefaultKotlinDaemonXmx() {
    // Kotlin Daemon inherits the memory settings from the Gradle daemon, unless specified explicitly.
    return getProjectGradleDaemonXmx();
  }

  int getProjectKotlinDaemonXmx() {
    if (hasUserPropertiesPath()) {
      return userProperties.getKotlinXmx();
    }
    int xmx = getKotlinDaemonXmx(projectProperties);
    return xmx > 0 ? xmx : getGradleDaemonXmx(projectProperties);
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
    if (projectProperties == null) {
      reportSaveError("Null gradle properties", null);
    }
    else {
      try {
        setDaemonXmx(projectProperties, newGradleValue, newKotlinValue);
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
