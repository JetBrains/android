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

import static com.android.tools.adtui.workbench.ToolWindowDefinition.ALLOW_ALL;
import static com.android.tools.adtui.workbench.ToolWindowDefinition.ALLOW_BASICS;
import static com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;

import com.android.annotations.Nullable;
import com.google.common.collect.ImmutableList;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.util.Disposer;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

class PalettePanelToolContent implements ToolContent<String> {
  public static final int MIN_TOOL_WIDTH = 310;

  private final static ToolWindowDefinition<String> ourDefinition = new ToolWindowDefinition<>(
    "Palette", AllIcons.Toolwindows.ToolWindowPalette, "PALETTE", Side.LEFT, Split.TOP, AutoHide.DOCKED,
    MIN_TOOL_WIDTH, DEFAULT_MINIMUM_BUTTON_SIZE, ALLOW_ALL, PalettePanelToolContent::new);
  private final static ToolWindowDefinition<String> ourOtherDefinition = new ToolWindowDefinition<>(
    "Other", AllIcons.Toolwindows.ToolWindowHierarchy, "OTHER", Side.RIGHT, Split.BOTTOM, AutoHide.DOCKED,
    MIN_TOOL_WIDTH, DEFAULT_MINIMUM_BUTTON_SIZE, ALLOW_ALL, PalettePanelToolContent::new);
  private final static ToolWindowDefinition<String> ourThirdDefinition = new ToolWindowDefinition<>(
    "Other", AllIcons.Toolwindows.ToolWindowAnt, "THIRD", Side.RIGHT, Split.TOP, AutoHide.DOCKED,
    MIN_TOOL_WIDTH, DEFAULT_MINIMUM_BUTTON_SIZE, ALLOW_ALL, PalettePanelToolContent::new);
  private final static ToolWindowDefinition<String> ourBasicDefinition = new ToolWindowDefinition<>(
    "Other", AllIcons.Toolwindows.ToolWindowAnt, "THIRD", Side.RIGHT, Split.TOP, AutoHide.DOCKED,
    MIN_TOOL_WIDTH, DEFAULT_MINIMUM_BUTTON_SIZE, ALLOW_BASICS, PalettePanelToolContent::new);

  private final AnAction myGearAction;
  private final AnAction myAdditionalAction;
  private final JComponent myComponent;
  private final JComponent myFocusComponent;
  private final KeyListener myKeyListener;
  private String myContext;
  private ToolWindowCallback myToolWindow;
  private boolean myDisposed;
  private boolean myGearActionPerformed;
  private boolean myAdditionalActionPerformed;
  private boolean myIsFilteringActive;
  private String myFilter;

  private PalettePanelToolContent(@NotNull Disposable parentDisposable) {
    Disposer.register(parentDisposable, this);
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
    myIsFilteringActive = true;
  }

  public static ToolWindowDefinition<String> getDefinition() {
    return ourDefinition;
  }

  public static ToolWindowDefinition<String> getOtherDefinition() {
    return ourOtherDefinition;
  }

  public static ToolWindowDefinition<String> getThirdDefinition() {
    return ourThirdDefinition;
  }

  public static ToolWindowDefinition<String> getBasicDefinition() {
    return ourBasicDefinition;
  }

  public void restore() {
    if (myToolWindow != null) {
      myToolWindow.restore();
    }
  }

  public void closeAutoHideWindow() {
    if (myToolWindow != null) {
      myToolWindow.autoHide();
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
  public void registerCallbacks(@NotNull ToolWindowCallback toolWindow) {
    myToolWindow = toolWindow;
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

  public void startFiltering(char character) {
    if (myToolWindow != null) {
      myToolWindow.startFiltering(String.valueOf(character));
    }
  }

  public void stopFiltering() {
    if (myToolWindow != null) {
      myToolWindow.stopFiltering();
    }
  }

  @Override
  public KeyListener getFilterKeyListener() {
    return myKeyListener;
  }

  @Override
  public boolean isFilteringActive() {
    return myIsFilteringActive;
  }

  public void setFilteringActive(boolean active) {
    myIsFilteringActive = active;
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
