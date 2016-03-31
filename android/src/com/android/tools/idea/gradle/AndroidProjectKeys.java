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
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.LIBRARY_DEPENDENCY;

/**
 * These keys determine the order in which the {@code ProjectDataService}s are invoked. The order is:
 * <ol>
 * <li>{@link com.android.tools.idea.gradle.service.GradleModelDataService}</li>
 * <li>{@link com.android.tools.idea.gradle.service.NativeAndroidGradleModelDataService}</li>
 * <li>{@link com.android.tools.idea.gradle.service.AndroidGradleModelDataService}</li>
 * <li>{@link com.android.tools.idea.gradle.service.JavaProjectDataService}</li>
 * <li>{@link com.android.tools.idea.gradle.service.ProjectCleanupDataService}</li>
 * </ol>
 * <br/>
 * The reason for having {@link com.android.tools.idea.gradle.service.GradleModelDataService} before all other services is that we need to
 * add the {@link com.android.tools.idea.gradle.facet.AndroidGradleFacet} to each of the modules. This facet contains the "Gradle path" of
 * each project module. This path is necessary when setting up inter-module dependencies.
 * <br/>
 * The reason for having {@link com.android.tools.idea.gradle.service.NativeAndroidGradleModelDataService} before
 * {@link com.android.tools.idea.gradle.service.AndroidGradleModelDataService} is to give more precedence to the jni sources reported in
 * {@link NativeAndroidGradleModel} than the ones reported in {@link AndroidGradleModel}. This is required because for a hybrid module,
 * both the models can contain jni sources information when using the latest Gradle plugin, but only {@link AndroidGradleModel} is provided
 * with the older plugin versions. Due to that we always use the information in {@link NativeAndroidGradleModel} when available and
 * fall back to {@link AndroidGradleModel} when required.
 */
public final class AndroidProjectKeys {
  // some of android ModuleCustomizer's should be run after core external system services
  // e.g. DependenciesModuleCustomizer - after core LibraryDependencyDataService,
  // since android dependencies can be removed because there is no respective LibraryDependencyData in the imported data from gradle
  private static final int PROCESSING_AFTER_BUILTIN_SERVICES = LIBRARY_DEPENDENCY.getProcessingWeight() + 1;

  @NotNull
  public static final Key<GradleModel> GRADLE_MODEL = Key.create(GradleModel.class, PROCESSING_AFTER_BUILTIN_SERVICES);

  @NotNull
  public static final Key<NativeAndroidGradleModel> NATIVE_ANDROID_MODEL = Key.create(NativeAndroidGradleModel.class,
                                                                                      GRADLE_MODEL.getProcessingWeight() + 10);

  @NotNull
  public static final Key<AndroidGradleModel> ANDROID_MODEL = Key.create(AndroidGradleModel.class,
                                                                         NATIVE_ANDROID_MODEL.getProcessingWeight() + 10);

  @NotNull
  public static final Key<JavaProject> JAVA_PROJECT = Key.create(JavaProject.class, NATIVE_ANDROID_MODEL.getProcessingWeight() + 10);

  @NotNull
  public static final Key<ImportedModule> IMPORTED_MODULE = Key.create(ImportedModule.class, JAVA_PROJECT.getProcessingWeight() + 10);

  private AndroidProjectKeys() {
  }
}
