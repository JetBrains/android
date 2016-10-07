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
package com.android.tools.idea.structure.services;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Collection;

/**
 * Plugins should use this extension point to provide {@link DeveloperServiceCreator}s.
 */
public interface DeveloperServiceCreators {
  ExtensionPointName<DeveloperServiceCreators> EP_NAME =
    ExtensionPointName.create("com.android.tools.idea.structure.services.developerServiceCreators");

  /**
   * Retrieves {@code DeveloperServiceCreator} configurations for this
   * grouping.  There is no strict requirement between what is included in this
   * collection and the content rendered from {@code getPanel()}.
   *
   * @return Service creators.
   */
  @NotNull
  Collection<? extends DeveloperServiceCreator> getCreators();

  /**
   * Get's the unique id of the bundle.
   */
  @NotNull
  String getBundleId();

  /**
   * Accessor to a custom root panel for the {@code DeveloperServiceSidePanel}.
   *
   * The component should typically use {@code BorderLayout}, filling the
   * entire panel and try to match pre-existing patterns and styles as much as
   * is possible.
   *
   * Note that this method will go away in favor of a more structured type. Designs pending refactor.
   */
  @NotNull
  Component getPanel(@NotNull DeveloperServiceMap serviceMap);
}
