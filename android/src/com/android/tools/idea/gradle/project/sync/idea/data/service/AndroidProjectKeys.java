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
package com.android.tools.idea.gradle.project.sync.idea.data.service;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ImportedModule;
import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectCleanupModel;
import com.intellij.openapi.externalSystem.model.Key;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.LIBRARY_DEPENDENCY;

/**
 * These keys determine the order in which the {@code ProjectDataService}s are invoked. The order is:
 * <ol>
 * <li>{@link GradleModuleModelDataService}</li>
 * <li>{@link NdkModuleModelDataService}</li>
 * <li>{@link AndroidModuleModelDataService}</li>
 * <li>{@link JavaModuleModelDataService}</li>
 * <li>{@link ProjectCleanupDataService}</li>
 * <li>{@link ModuleDisposalDataService}</li>
 * </ol>
 * <br/>
 * The reason for having {@link GradleModuleModelDataService} before all other services is that we need to
 * add the {@link GradleFacet} to each of the modules. This facet contains the "Gradle path" of
 * each project module. This path is necessary when setting up inter-module dependencies.
 * <br/>
 * The reason for having {@link NdkModuleModelDataService} before
 * {@link AndroidModuleModelDataService} is to give more precedence to the jni sources reported in
 * {@link NdkModuleModel} than the ones reported in {@link AndroidModuleModel}. This is required because for a hybrid module,
 * both the models can contain jni sources information when using the latest Gradle plugin, but only {@link AndroidModuleModel} is provided
 * with the older plugin versions. Due to that we always use the information in {@link NdkModuleModel} when available and
 * fall back to {@link AndroidModuleModel} when required.
 */
public final class AndroidProjectKeys {
  // some of android ModuleCustomizer's should be run after core external system services
  // e.g. DependenciesModuleCustomizer - after core LibraryDependencyDataService,
  // since android dependencies can be removed because there is no respective LibraryDependencyData in the imported data from gradle
  private static final int PROCESSING_AFTER_BUILTIN_SERVICES = LIBRARY_DEPENDENCY.getProcessingWeight() + 1;

  @NotNull
  public static final Key<GradleModuleModel> GRADLE_MODULE_MODEL = Key.create(GradleModuleModel.class, PROCESSING_AFTER_BUILTIN_SERVICES);

  @NotNull
  public static final Key<NdkModuleModel> NDK_MODEL = Key.create(NdkModuleModel.class, GRADLE_MODULE_MODEL.getProcessingWeight() + 10);

  @NotNull
  public static final Key<AndroidModuleModel> ANDROID_MODEL = Key.create(AndroidModuleModel.class, NDK_MODEL.getProcessingWeight() + 10);

  @NotNull
  public static final Key<JavaModuleModel> JAVA_MODULE_MODEL = Key.create(JavaModuleModel.class, NDK_MODEL.getProcessingWeight() + 10);

  @NotNull
  public static final Key<ProjectCleanupModel>
    PROJECT_CLEANUP_MODEL = Key.create(ProjectCleanupModel.class, JAVA_MODULE_MODEL.getProcessingWeight() + 10);

  @NotNull
  public static final Key<ImportedModule> IMPORTED_MODULE =
    Key.create(ImportedModule.class, PROJECT_CLEANUP_MODEL.getProcessingWeight() + 10);

  private AndroidProjectKeys() {
  }
}
