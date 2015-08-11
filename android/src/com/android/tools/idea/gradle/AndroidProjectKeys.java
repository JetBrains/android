/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import org.jetbrains.annotations.NotNull;

/**
 * These keys determine the order in which the {@code ProjectDataService}s are invoked. The order is:
 * <ol>
 * <li>{@link com.android.tools.idea.gradle.service.GradleProjectDataService}</li>
 * <li>{@link com.android.tools.idea.gradle.service.AndroidProjectDataService}</li>
 * <li>{@link com.android.tools.idea.gradle.service.JavaProjectDataService}</li>
 * <li>{@link com.android.tools.idea.gradle.service.ProjectCleanupDataService}</li>
 * </ol>
 * <br/>
 * The reason for following this order is that we need to add the {@code AndroidGradleFacet} to each of the modules. This facet contains the
 * "Gradle path" of each project module. This path is necessary when setting up inter-module dependencies.
 */
public class AndroidProjectKeys {
  // some of android ModuleCustomizer's should be run after core external system services
  // e.g. DependenciesModuleCustomizer - after core LibraryDependencyDataService,
  // since android dependencies can be removed because there is no respective LibraryDependencyData in the imported data from gradle
  private static final int PROCESSING_AFTER_BUILTIN_SERVICES = ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight() + 1;

  @NotNull public static final Key<IdeaGradleProject> IDE_GRADLE_PROJECT =
    Key.create(IdeaGradleProject.class, PROCESSING_AFTER_BUILTIN_SERVICES);

  @NotNull public static final Key<IdeaAndroidProject> IDE_ANDROID_PROJECT =
    Key.create(IdeaAndroidProject.class, IDE_GRADLE_PROJECT.getProcessingWeight() + 10);

  @NotNull public static final Key<IdeaJavaProject> IDE_JAVA_PROJECT =
    Key.create(IdeaJavaProject.class, IDE_ANDROID_PROJECT.getProcessingWeight() + 10);

  @NotNull public static final Key<ImportedModule> IMPORTED_MODULE =
    Key.create(ImportedModule.class, IDE_JAVA_PROJECT.getProcessingWeight() + 10);

  private AndroidProjectKeys() {
  }
}
