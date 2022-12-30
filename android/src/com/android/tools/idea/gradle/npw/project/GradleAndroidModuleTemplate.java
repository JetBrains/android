/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.npw.project;

import static com.android.SdkConstants.FD_AIDL;
import static com.android.SdkConstants.FD_JAVA;
import static com.android.SdkConstants.FD_MAIN;
import static com.android.SdkConstants.FD_ML_MODELS;
import static com.android.SdkConstants.FD_RESOURCES;
import static com.android.SdkConstants.FD_SOURCES;
import static com.android.SdkConstants.FD_TEST;
import static com.android.SdkConstants.FD_UNIT_TEST;

import com.android.tools.idea.projectsystem.AndroidModulePathsImpl;
import com.android.tools.idea.projectsystem.NamedModuleTemplate;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * Project paths for a Gradle Android project.
 *
 * <p>Generally looks like this:
 *
 * <pre>
 * app/ (module root)
 * `-src/
 *  |-main/ (manifest directory)
 *  | |-AndroidManifest.xml
 *  | |-java/... (src root)
 *  | | `-com/google/foo/bar/... (src directory of package com.google.foo.bar)
 *  | |-res/... (res directory)
 *  | `-aidl/... (aidl directory)
 *  `-test/
 *    `-java/... (test root)
 *      `-com/google/foo/bar/... (test directory of package com.google.foo.bar)
 * </pre>
 */
public final class GradleAndroidModuleTemplate {
  public static NamedModuleTemplate createSampleTemplate() {
    return createDefaultTemplateAt(new File(""));
  }

  /**
   * Create an {@link NamedModuleTemplate} with default values for a new Module inside an existing project.
   * Assumes the 'main' flavor and default android locations for the source, test, res,
   * aidl and manifest.
   * @param moduleName Module names may use ":" for sub folders. This mapping is only true when creating new modules, as the user
   *                   can later customize the Module Path (called Project Path in gradle world) in "settings.gradle"
   */
  public static NamedModuleTemplate createDefaultModuleTemplate(@NotNull Project project, @NotNull String moduleName) {
    String projectLocation = project.getBasePath();
    File moduleRoot = getModuleRootForNewModule(projectLocation, moduleName);
    return createDefaultTemplateAt(moduleRoot);
  }

  /**
   * Create an {@link NamedModuleTemplate} with default values.
   * Assumes the 'main' flavor and default android locations for the source, test, res,
   * aidl and manifest.
   */
  public static NamedModuleTemplate createDefaultTemplateAt(@NotNull File moduleRoot) {
    File baseSrcDir = new File(moduleRoot, FD_SOURCES);
    File baseFlavorDir = new File(baseSrcDir, FD_MAIN);
    return new NamedModuleTemplate("main", new AndroidModulePathsImpl(
      moduleRoot,
      baseFlavorDir,
      new File(baseFlavorDir, FD_JAVA),
      new File(baseSrcDir.getPath(), FD_UNIT_TEST + File.separatorChar + FD_JAVA),
      new File(baseSrcDir.getPath(), FD_TEST + File.separatorChar + FD_JAVA),
      new File(baseFlavorDir, FD_AIDL),
      ImmutableList.of(new File(baseFlavorDir, FD_RESOURCES)),
      ImmutableList.of(new File(baseFlavorDir, FD_ML_MODELS))
    ));
  }


  /**
   * Module names may use ":" for sub folders. This mapping is only true when creating new modules, as the user can later customize
   * the Module Path (called Project Path in gradle world) in "settings.gradle"
   */
  public static File getModuleRootForNewModule(String projectLocation, String moduleName) {
    return new File(projectLocation, moduleName.replace(':', File.separatorChar));
  }
}
