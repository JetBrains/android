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
package com.android.tools.idea.editors;

import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem;
import com.intellij.lang.properties.codeInspection.unused.ImplicitPropertyUsageProvider;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;
import java.util.Arrays;
import java.util.Collection;

import static com.android.SdkConstants.*;

/**
 * Provider which defines some properties as implicitly used, such that they don't get
 * flagged by the inspections as unused.
 */
public class GradleImplicitPropertyUsageProvider implements ImplicitPropertyUsageProvider {
  @Override
  public boolean isUsed(Property property) {
    Project project = property.getProject();
    if (!(ProjectSystemUtil.getProjectSystem(project) instanceof GradleProjectSystem)) {
      return false;
    }
    PsiFile file = property.getContainingFile();
    boolean caseSensitive = file.getViewProvider().getVirtualFile().isCaseSensitive();
    if (Comparing.equal(file.getName(), FN_GRADLE_WRAPPER_PROPERTIES, caseSensitive)) {
      // Ignore all properties in the gradle wrapper: read by the gradle wrapper .jar code
      return true;
    }

    if (Comparing.equal(file.getName(), FN_GRADLE_PROPERTIES, caseSensitive) ||
        Comparing.equal(file.getName(), FN_RESOURCES_PROPERTIES, caseSensitive)) {
      // Ignore all properties in the gradle.properties; we don't have a complete set of what's used
      // and we don't want to suggest to the user that these are unused
      return true;
    }

    // The android gradle plugin reads sdk.dir, ndk.dir and some others from local.properties
    if (Comparing.equal(file.getName(), FN_LOCAL_PROPERTIES, caseSensitive)) {
      String name = property.getName();
      return AGP_USED_LOCAL_PROPERTIES.contains(name);
    }

    return false;
  }

  private static final Collection<String> AGP_USED_LOCAL_PROPERTIES =
    Arrays.asList(SDK_DIR_PROPERTY, NDK_DIR_PROPERTY, ANDROID_DIR_PROPERTY, CMAKE_DIR_PROPERTY, NDK_SYMLINK_DIR);
}
