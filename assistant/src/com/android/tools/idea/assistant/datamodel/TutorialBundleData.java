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
 * Representation of an assistant tutorial configuration. Used to render an {@see AssistSidePanel}.
 */
public interface TutorialBundleData {

  /**
   * Workaround for classloading issues. Sets a class from your plugin (typically your
   * implementation of {@see AssistantBundleCreator} to use with icon loading. Implemntors
   * of this class do not need to worry about calling this though they should be aware that
   * it is not set until after {@code afterUnmarshall} in the bundle.
   */
  void setResourceClass(@NotNull Class clazz);

  /**
   * Returns the Assistant name, used as a label in the panel.
   */
  @NotNull
  String getName();

  /**
   * Returns logo rendered instead of the name and icon when present.
   *
   * @see #getLogo()
   */
  @Nullable
  Icon getIcon();

  /**
   * Returns icon that accompanies the name.
   * Supersedes {@link #getIcon()} if non-null.
   */
  @Nullable
  Icon getLogo();

  /**
   * Returns a list of features that will contain your individual tutorials.
   */
  @NotNull
  List<? extends FeatureData> getFeatures();

  /**
   * Returns summary content displayed in the entry-point view of the panel.
   */
  @NotNull
  String getWelcome();

  /**
   * If true, all tutorials should be rendered single step at a time with a
   * next/prev button to navigate between steps, Otherwise, all steps for a
   * tutorial will be displayed on a single page flowing vertically.
   */
  @Nullable
  boolean isStepByStep();
}
