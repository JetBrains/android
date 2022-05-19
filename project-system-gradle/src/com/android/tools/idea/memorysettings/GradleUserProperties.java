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

import com.android.tools.idea.gradle.util.GradleProjectSystemUtil;
import com.android.tools.idea.gradle.util.GradleProperties;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

/**
 * This class specifies user-level gradle properties.
 */
public class GradleUserProperties {
  private static final Logger LOG = Logger.getInstance(GradleUserProperties.class);

  private GradleProperties gradleUserHomeProperties;
  private File propertiesPath;
  private int gradleXmx = -1;
  private int kotlinXmx = -1;

  GradleUserProperties(Project project) {
    gradleUserHomeProperties = getProperties(project);
    if (GradlePropertiesUtil.hasJvmArgs(gradleUserHomeProperties)) {
      propertiesPath = gradleUserHomeProperties.getPath();
      findGradleDaemonXmx(gradleUserHomeProperties);
      findKotlinDaemonXmx(gradleUserHomeProperties);
    }
  }

  File getPropertiesPath() {
    return propertiesPath;
  }

  int getGradleXmx() {
    return gradleXmx;
  }

  int getKotlinXmx() {
    return kotlinXmx;
  }

  private void findGradleDaemonXmx(GradleProperties properties) {
    // Check properties in order, and return the first Xmx value
    int xmx = getGradleDaemonXmx(properties);
    if (xmx > 0) {
      this.gradleXmx = xmx;
      return;
    }
  }

  private void findKotlinDaemonXmx(GradleProperties properties) {
    int xmx = getKotlinDaemonXmx(properties);
    if (xmx > 0) {
      this.kotlinXmx = xmx;
      return;
    }
    // Kotlin daemon inherits Gradle daemon Xmx.
    xmx = getGradleDaemonXmx(properties);
    if (xmx > 0) {
      this.kotlinXmx = xmx;
      return;
    }
  }

  @Nullable
  private static GradleProperties getProperties(Project project) {
    return null; /*
    File file = GradleProjectSystemUtil.getUserGradlePropertiesFile(project);
    try {
      if (file.exists()) {
        return new GradleProperties(file);
      } else {
        return null;
      }
    }
    catch (IOException e) {
      LOG.info("Failed to read " + file, e);
      return null;
    }*/
  }
}
