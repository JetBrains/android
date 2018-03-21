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
package com.android.tools.profilers.network.details;

import com.android.tools.profilers.analytics.FeatureTracker;
import com.android.tools.profilers.network.httpdata.HttpData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.adtui.common.AdtUiUtils.DEFAULT_TOP_BORDER;

/**
 * Base class for all tabs shown in the {@link ConnectionDetailsView}. To use, construct
 * subclass instances and add their title, icon, and component content to a target
 * {@link JTabbedPane} using {@link JTabbedPane#addTab(String, Icon, Component)}
 */
abstract class TabContent {

  /**
   * Cached component, set first time {@link #getComponent()} is called.
   */
  @Nullable private JComponent myComponent;

  @NotNull
  public abstract String getTitle();

  @Nullable
  public Icon getIcon() {
    return null;
  }

  /**
   * Return the component associated with this tab. Guaranteed to be the same value every time.
   */
  @NotNull
  public final JComponent getComponent() {
    if (myComponent == null) {
      myComponent = createComponent();
      myComponent.setBorder(DEFAULT_TOP_BORDER);
    }
    return myComponent;
  }

  /**
   * Populates the contents of this tab with information from the target {@code data}. This value
   * might possibly be {@code null}, if the user cleared the current selection.
   */
  abstract public void populateFor(@Nullable HttpData data);

  /**
   * Track this tab being selected using the passed in {@code featureTracker}.
   */
  abstract public void trackWith(@NotNull FeatureTracker featureTracker);

  /**
   * The subclass should create a panel that will populate a tab.
   *
   * This method will only be called once and cached thereafter.
   */
  @NotNull
  protected abstract JComponent createComponent();
}
