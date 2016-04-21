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
package com.android.tools.idea.assistant.datamodel;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

/**
 * TODO document me
 */
public interface TutorialBundleData {
  @NotNull
  String getName();

  @Nullable("Optionally rendered")
  Icon getIcon();

  /**
   * Supersedes {@code getIcon} if non-null.
   * @return
   */
  @Nullable("Optionally rendered")
  Icon getLogo();

  @NotNull
  String getId();

  @NotNull
  String getContentRoot();

  @NotNull
  List<? extends FeatureData> getFeatures();

  @Nullable("Optionally supported")
  Integer getMinPluginVersion();

  @NotNull
  String getWelcome();

  @Nullable("Optionally rendered")
  String getLearnMoreLink();
}
