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
package com.android.tools.adtui.ui;

import com.intellij.ui.HideableDecorator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Wraps {@link HideableDecorator} usage for package visibility.
 */
public class HideableDecoratorPanel extends JPanel {

  private final HideableDecorator myHideableDecorator;

  public HideableDecoratorPanel(@NotNull String title, @Nullable JComponent northEastComponent) {
    this(title, false, northEastComponent);
  }

  public HideableDecoratorPanel(@NotNull String title, boolean adjustWindow, @Nullable JComponent northEastComponent) {
    super(new BorderLayout());
    myHideableDecorator = new HideableDecorator(this, title, adjustWindow, northEastComponent);
  }

  public void setContentComponent(@Nullable JComponent content) {
    myHideableDecorator.setContentComponent(content);
  }

  public void setOn(boolean on) {
    myHideableDecorator.setOn(on);
  }

  public boolean isExpanded() {
    return myHideableDecorator.isExpanded();
  }
}
