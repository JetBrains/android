/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.project;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.framework.detection.DetectionExcludesConfiguration;
import com.intellij.framework.detection.FrameworkDetector;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;

/** Disables Android framework detection since it doesn't work with Blaze projects. */
final class DisableAndroidFrameworkActivity implements StartupActivity, DumbAware {

  @Override
  public void runActivity(Project project) {
    if (Blaze.isBlazeProject(project)) {
      for (FrameworkDetector frameworkDetector : FrameworkDetector.EP_NAME.getExtensions()) {
        if (frameworkDetector.getDetectorId().equals("android")) {
          DetectionExcludesConfiguration.getInstance(project)
              .addExcludedFramework(frameworkDetector.getFrameworkType());
        }
      }
    }
  }
}
