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
package com.android.tools.idea.assistant.datamodel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * Representation of a "feature" within the context of a tutorial side panel.
 * Generally used to express a collection of tutorials based on similarity in
 * subject matter.
 */
public interface FeatureData {

  /**
   * Returns the short name of the feature/tutorial-group.
   */
  @NotNull
  String getName();

  /**
   * Returns an icon for the feature if one is provided. It is suggested that
   * your features either all have an icon or none do. When only a subset are
   * provided, the layout becomes difficult to read.
   */
  @Nullable/*Feature icons are optional*/
  Icon getIcon();

  /**
   * Returns the descriptive text for the feature. May contain limited markup such as links
   * and bolding.
   */
  @NotNull
  String getDescription();

  /**
   * Returns the collection of tutorials that fall under this feature. The list is
   * not displayed by default, the UI element for the feature needs to be toggled for
   * them to be shown.
   */
  @NotNull
  List<? extends TutorialData> getTutorials();

  /**
   * Returns whether the list of tutorials should be displayed when first opening a Feature.
   */
  default boolean displayTutorials() {
    return false;
  }


  /**
   * Sets a class for use in loading resources such as icons.
   */
  void setResourceClass(@NotNull Class clazz);
}
