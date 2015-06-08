/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.compatibility;

import com.android.tools.idea.gradle.service.notification.hyperlink.NotificationHyperlink;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Obtains the version of a component in a project (e.g. Gradle, Android Gradle plugin, etc.)
 */
interface ComponentVersionReader {
  ComponentVersionReader IDE = new IdeVersionReader();
  ComponentVersionReader ANDROID_GRADLE_PLUGIN = new AndroidGradlePluginVersionReader();
  ComponentVersionReader GRADLE = new GradleVersionReader();

  /**
   * Indicates whether this reader can obtain a component's version from the given module.
   *
   * @param module the given module.
   * @return {@code true} if this reader can obtain a component's version from the given module; {@code false} otherwise.
   */
  boolean appliesTo(@NotNull Module module);

  /**
   * Returns the version of a specific component from the given module.
   *
   * @param module the given module.
   * @return the version of a specific component from the given module, or {@code null} if this reader cannot obtain it.
   */
  @Nullable
  String getComponentVersion(@NotNull Module module);

  /**
   * Returns the location where a component's version can be read from.
   *
   * @param module the module that might contain the location to return.
   * @return the location where a component's version can be read from, or {@code null} if the version is not read from a file in the given
   * module.
   */
  @Nullable
  FileLocation getVersionSource(@NotNull Module module);

  /**
   * Returns the "quick fixes" that can be used when the component version return by this reader does not match an expected value or range
   * of values.
   *
   * @param module          the module from where the component version was read.
   * @param expectedVersion contains the expected version (or range of versions.)
   * @param location        the location where a component version was read from.
   * @return the "quick fixes" that can be used when the component version return by this reader does not match an expected value or range
   * of values.
   */
  @NotNull
  List<NotificationHyperlink> getQuickFixes(@NotNull Module module, @Nullable VersionRange expectedVersion, @Nullable FileLocation location);

  /**
   * Indicates whether the component version is applicable to all modules in the project.
   *
   * @return {@code true} if the component version is applicable to all modules in the project; or {@code false}, if the component version
   * is applicable to a single module in the project.
   */
  boolean isProjectLevel();

  /**
   * @return the component name (e.g. "Gradle", "Android Gradle plugin", etc.)
   */
  @NotNull
  String getComponentName();
}
