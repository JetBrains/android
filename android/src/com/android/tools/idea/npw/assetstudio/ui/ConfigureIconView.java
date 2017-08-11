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
package com.android.tools.idea.npw.assetstudio.ui;

import com.android.tools.idea.npw.assetstudio.icon.AndroidIconGenerator;
import com.android.tools.idea.observable.core.StringProperty;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionListener;

public interface ConfigureIconView {
  /**
   * Returns the root panel for this view
   */
  @NotNull
  JComponent getRootComponent();

  /**
   * Add a listener which will be triggered whenever the asset represented by this view is
   * modified in any way.
   */
  void addAssetListener(@NotNull ActionListener listener);

  /**
   * The asset output name
   * @return
   */
  @NotNull
  StringProperty outputName();

  /**
   * The {@link AndroidIconGenerator} for this view
   * @return
   */
  @NotNull
  AndroidIconGenerator getIconGenerator();
}
