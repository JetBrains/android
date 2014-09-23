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
package com.android.tools.idea.ui;

import com.intellij.ui.ClickListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;

public abstract class WrapAwareLinkMouseListenerBase <T> extends ClickListener implements MouseMotionListener {

  @Nullable
  protected abstract T getTagAt(@NotNull MouseEvent e);

  @Override
  public boolean onClick(@NotNull MouseEvent e, int clickCount) {
    if (e.getButton() == MouseEvent.BUTTON1) {
      handleTagClick(getTagAt(e));
    }
    return false;
  }

  protected void handleTagClick(@Nullable T tag) {
    if (tag instanceof Runnable) {
      ((Runnable)tag).run();
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
  }

  @Override
  public void mouseMoved(@NotNull MouseEvent e) {
    Component component = (Component)e.getSource();
    Object tag = getTagAt(e);
    if (tag != null) {
      component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    else {
      component.setCursor(Cursor.getDefaultCursor());
    }
  }

  @Override
  public void installOn(@NotNull Component component) {
    super.installOn(component);

    component.addMouseMotionListener(this);
  }
}
