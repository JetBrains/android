/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.assistant;

import com.android.tools.idea.assistant.datamodel.AnalyticsProvider;
import com.android.tools.idea.assistant.datamodel.TutorialBundleData;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.FileNotFoundException;
import java.net.URL;

/**
 * Extension point for exposing assistant bundle data. May either return a config file for default processing or a custom instance
 * of a bundle may be returned.
 */
public interface AssistantBundleCreator {
  ExtensionPointName<AssistantBundleCreator> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.assistant.assistantBundleCreator");

  /**
   * Get's the unique id of the bundle.
   */
  @NotNull
  String getBundleId();

  /**
   * Gets a custom instance of the bundle data. Only to be used when the default bundle is insufficient. {@see #getConfig}
   *
   * Should only be null when {@see #getConfig} is non-null.
   */
  @Nullable
  TutorialBundleData getBundle(@NotNull Project project);

  /**
   * Gets the config resource for use with a default bundle. If a custom bundle is necessary, use {@code #getBundle} instead.
   *
   * Should only be null when {@see #getBundle} is non-null.
   */
  @Nullable
  URL getConfig() throws FileNotFoundException;

  /**
   * Retrieves an analytics wrapper to be used with Assistant panel events with your bundle's elements.
   * Defaults to a no-op to avoid excessive null checks.
   */
  @NotNull
  default AnalyticsProvider getAnalyticsProvider() {
    return AnalyticsProvider.getNoOp();
  }
}
