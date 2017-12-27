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
package com.android.tools.adtui.workbench;

import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collections;
import java.util.List;

class PalettePanelToolContent implements ToolContent<String> {
  private final AnAction myGearAction;
  private final AnAction myAdditionalAction;
  private final JComponent myComponent;
  private final JComponent myFocusComponent;
  private final KeyListener myKeyListener;
  private String myContext;
  private Runnable myRestore;
  private Runnable myAutoClose;
  private StartFilteringListener myStartFilteringListener;
  private boolean myDisposed;
  private boolean myGearActionPerformed;
  private boolean myAdditionalActionPerformed;
  private String myFilter;

  public PalettePanelToolContent() {
    myComponent = new JPanel();
    myFocusComponent = new JLabel();
    myGearAction = new AnAction("GearAction") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        myGearActionPerformed = true;
      }
    };
    myAdditionalAction = new AnAction("AdditionalAction") {
      @Override
      public void actionPerformed(@NotNull AnActionEvent event) {
        myAdditionalActionPerformed = true;
      }
    };
    myFilter = "";
    myKeyListener = new MyKeyListener();
  }

  public static ToolWindowDefinition<String> getDefinition() {
    return new ToolWindowDefinition<>(
      "Palette", AllIcons.Toolwindows.ToolWindowPalette, "PALETTE", Side.LEFT, Split.TOP, AutoHide.DOCKED, PalettePanelToolContent::new);
  }

  public static ToolWindowDefinition<String> getOtherDefinition() {
    return new ToolWindowDefinition<>(
      "Other", AllIcons.Toolwindows.ToolWindowHierarchy, "OTHER", Side.RIGHT, Split.BOTTOM, AutoHide.DOCKED, PalettePanelToolContent::new);
  }

  public static ToolWindowDefinition<String> getThirdDefinition() {
    return new ToolWindowDefinition<>(
      "Other", AllIcons.Toolwindows.ToolWindowAnt, "THIRD", Side.RIGHT, Split.TOP, AutoHide.DOCKED, PalettePanelToolContent::new);
  }

  public void restore() {
    if (myRestore != null) {
      myRestore.run();
    }
  }

  public void closeAutoHideWindow() {
    if (myAutoClose != null) {
      myAutoClose.run();
    }
  }

  @Override
  public void setToolContext(@Nullable String toolContext) {
    myContext = toolContext;
  }

  @Nullable
  public String getToolContext() {
    return myContext;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @NotNull
  @Override
  public JComponent getFocusedComponent() {
    return myFocusComponent;
  }

  @NotNull
  @Override
  public List<AnAction> getGearActions() {
    return Collections.singletonList(myGearAction);
  }

  @NotNull
  @Override
  public List<AnAction> getAdditionalActions() {
    return Collections.singletonList(myAdditionalAction);
  }

  @Override
  public void setRestoreToolWindow(@NotNull Runnable runnable) {
    myRestore = runnable;
  }

  @Override
  public void setCloseAutoHideWindow(@NotNull Runnable runnable) {
    myAutoClose = runnable;
  }

  @Override
  public void dispose() {
    myDisposed = true;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  public boolean isGearActionPerformed() {
    return myGearActionPerformed;
  }

  public boolean isAdditionalActionPerformed() {
    return myAdditionalActionPerformed;
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@NotNull String filter) {
    myFilter = filter;
  }

  @NotNull
  public String getFilter() {
    return myFilter;
  }

  @Override
  public void setStartFiltering(@NotNull StartFilteringListener listener) {
    myStartFilteringListener = listener;
  }

  public void startFiltering(char character) {
    if (myStartFilteringListener != null) {
      myStartFilteringListener.startFiltering(character);
    }
  }

  @Override
  public KeyListener getFilterKeyListener() {
    return myKeyListener;
  }

  private class MyKeyListener extends KeyAdapter {
    private final List<String> RECOGNIZED_FILTERS = ImmutableList.of("elevation", "visible", "context");

    @Override
    public void keyPressed(@NotNull KeyEvent event) {
      if (event.getKeyCode() == KeyEvent.VK_ENTER && event.getModifiers() == 0 && myFilter.length() >= 4) {
        for (String recognized : RECOGNIZED_FILTERS) {
          if (recognized.startsWith(myFilter)) {
            event.consume();
          }
        }
      }
    }
  }
}
