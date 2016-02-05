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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.util.ui.Header;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.ChildFocusWatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;

import static javax.swing.SwingUtilities.isDescendingFrom;

public abstract class ToolWindowPanel extends JPanel implements Disposable {
  @NotNull private final Header myHeader;
  @NotNull private final ChildFocusWatcher myFocusWatcher;

  protected ToolWindowPanel(@NotNull String title) {
    super(new BorderLayout());
    myHeader = new Header(title) {
      @Override
      public boolean isActive() {
        return isFocused();
      }
    };
    add(myHeader, BorderLayout.NORTH);

    myFocusWatcher = new ChildFocusWatcher(this) {
      @Override
      protected void onFocusGained(FocusEvent event) {
        myHeader.repaint();
      }

      @Override
      protected void onFocusLost(FocusEvent event) {
        myHeader.repaint();
      }
    };
  }

  private boolean isFocused() {
    KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
    Component focusOwner = focusManager.getFocusOwner();
    return focusOwner != null && isDescendingFrom(focusOwner, this);
  }

  @NotNull
  protected Header getHeader() {
    return myHeader;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myFocusWatcher);
  }
}
