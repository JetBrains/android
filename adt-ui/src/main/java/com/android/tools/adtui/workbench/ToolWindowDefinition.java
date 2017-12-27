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

import com.intellij.openapi.util.Factory;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import java.awt.*;

import static com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;

/**
 * A definition of an {@link AttachedToolWindow} that can be attached on the side
 * of a {@link WorkBench}.
 *
 * @param <T> Specifies the type of data controlled by a {@link WorkBench}.
 */
public class ToolWindowDefinition<T> {
  public static final int DEFAULT_SIDE_WIDTH = JBUI.scale(225);

  private final String myTitle;
  private final Icon myIcon;
  private final String myName;
  private final Side mySide;
  private final Split mySplit;
  private final AutoHide myAutoHide;
  private final int myMinimumSize;
  private final Dimension myButtonSize;
  private final Factory<ToolContent<T>> myFactory;

  public ToolWindowDefinition(@NotNull String title,
                              @NotNull Icon icon,
                              @NotNull String name,
                              @NotNull Side side,
                              @NotNull Split split,
                              @NotNull AutoHide autoHide,
                              @NotNull Factory<ToolContent<T>> factory) {
    this(title, icon, name, side, split, autoHide, DEFAULT_SIDE_WIDTH, DEFAULT_MINIMUM_BUTTON_SIZE, factory);
  }

  public ToolWindowDefinition(@NotNull String title,
                              @NotNull Icon icon,
                              @NotNull String name,
                              @NotNull Side side,
                              @NotNull Split split,
                              @NotNull AutoHide autoHide,
                              int minimumSize,
                              @NotNull Dimension buttonSize,
                              @NotNull Factory<ToolContent<T>> factory) {
    myTitle = title;
    myIcon = icon;
    myName = name;
    mySide = side;
    mySplit = split;
    myAutoHide = autoHide;
    myMinimumSize = minimumSize;
    myButtonSize = buttonSize;
    myFactory = factory;
  }

  /**
   * @return the title visible in the header of the tool window and on the minimized button
   */
  @NotNull
  public String getTitle() {
    return myTitle;
  }

  /**
   * @return the 13x13 icon visible in the header of the tool window and on the minimized button
   */
  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  /**
   * @return the name to identify this tool window. Also used for associating properties of this tool window.
   */
  @NotNull
  public String getName() {
    return myName;
  }

  /**
   * Returns a factory for creating the content component for this tool window.<br/>
   * This method will be used to create the initial content. It may also be called whenever the user makes a
   * transition between an attached window and a floating tool window.
   *
   * @return a factory for creating the component with the content for this tool window
   */
  @NotNull
  public Factory<ToolContent<T>> getFactory() {
    return myFactory;
  }

  /**
   * Specifies the initial minimum width of this tool window.
   * The system will save the last width used and restore that next time the window is shown.
   * This value is only used for the initial value when no prior width is known.
   *
   * @return the minimum initial width.
   */
  public int getInitialMinimumWidth() {
    return myMinimumSize;
  }

  /**
   * Specifies the button size for actions in the header of this tool window.
   *
   * @return the button size
   */
  @NotNull
  public Dimension getButtonSize() {
    return myButtonSize;
  }

  /**
   * Specifies on which side of the {@link WorkBench} this tool window should be shown initially.
   * The system will save the last location and restore that next time the window is shown.
   *
   * @return the {@link Side} of {@link WorkBench} an {@link AttachedToolWindow} window should be placed at.
   */
  public Side getSide() {
    return mySide;
  }

  /**
   * Specifies which part (top or bottom) this tool window should be shown initially.
   * The system will save the last location and restore that next time the window is shown.
   *
   * @return the {@link Split} of the window area an {@link AttachedToolWindow} should be placed at.
   */
  public Split getSplit() {
    return mySplit;
  }

  /**
   * Specifies if a tool window should initially be docked or overlay the {@link WorkBench}.
   * The system will save the last saved value and restore that next time the window is shown.
   *
   * @return the initial {@link AutoHide} mode of an {@link AttachedToolWindow}.
   */
  public AutoHide getAutoHide() {
    return myAutoHide;
  }
}
