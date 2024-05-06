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

import static com.intellij.openapi.actionSystem.ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE;

import com.intellij.openapi.Disposable;
import com.intellij.util.ui.JBUI;
import java.awt.Dimension;
import java.util.function.Function;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * A definition of an {@link AttachedToolWindow} that can be attached on the side
 * of a {@link WorkBench}.
 *
 * @param <T> Specifies the type of data controlled by a {@link WorkBench}.
 */
public class ToolWindowDefinition<T> {
  public static final int DEFAULT_SIDE_WIDTH = JBUI.scale(225);
  public static final Dimension DEFAULT_BUTTON_SIZE = DEFAULT_MINIMUM_BUTTON_SIZE;
  public static final int ALLOW_BASICS = 0x0;
  public static final int ALLOW_FLOATING = 0x01;
  public static final int ALLOW_AUTO_HIDE = 0x02;
  public static final int ALLOW_SPLIT_MODE = 0x04;
  public static final int ALLOW_ALL = ALLOW_BASICS | ALLOW_FLOATING | ALLOW_AUTO_HIDE | ALLOW_SPLIT_MODE;

  private final String myTitle;
  private final Icon myIcon;
  private final String myName;
  private final Side mySide;
  private final Split mySplit;
  private final AutoHide myAutoHide;
  private final int myMinimumSize;
  private final Dimension myButtonSize;
  private final int myFeatures;
  private final Function<Disposable, ToolContent<T>> myFactory;
  private final boolean myShowGearAction;
  private final boolean myShowHideAction;
  private final boolean myOverrideSide;
  private final boolean myOverrideSplit;

  public ToolWindowDefinition(@NotNull String title,
                              @NotNull Icon icon,
                              @NotNull String name,
                              @NotNull Side side,
                              @NotNull Split split,
                              @NotNull AutoHide autoHide,
                              @NotNull Function<Disposable, ToolContent<T>> factory) {
    this(title, icon, name, side, split, autoHide, DEFAULT_SIDE_WIDTH, DEFAULT_BUTTON_SIZE, ALLOW_FLOATING | ALLOW_SPLIT_MODE, true, true, false, false, factory);
  }

  public ToolWindowDefinition(@NotNull String title,
                              @NotNull Icon icon,
                              @NotNull String name,
                              @NotNull Side side,
                              @NotNull Split split,
                              @NotNull AutoHide autoHide,
                              int minimumSize,
                              @NotNull Dimension buttonSize,
                              int features,
                              @NotNull Function<Disposable, ToolContent<T>> factory) {
    this(title, icon, name, side, split, autoHide, minimumSize, buttonSize, features, true, true, false, false, factory);
  }

  public ToolWindowDefinition(@NotNull String title,
                              @NotNull Icon icon,
                              @NotNull String name,
                              @NotNull Side side,
                              @NotNull Split split,
                              @NotNull AutoHide autoHide,
                              int minimumSize,
                              @NotNull Dimension buttonSize,
                              int features,
                              boolean showGearAction,
                              boolean showHideAction,
                              boolean overrideSide,
                              boolean overrideSplit,
                              @NotNull Function<Disposable, ToolContent<T>> factory) {
    myTitle = title;
    myIcon = icon;
    myName = name;
    mySide = side;
    mySplit = split;
    myAutoHide = autoHide;
    myMinimumSize = minimumSize;
    myButtonSize = buttonSize;
    myFeatures = features;
    myFactory = factory;
    myShowGearAction = showGearAction;
    myShowHideAction = showHideAction;
    myOverrideSide = overrideSide;
    myOverrideSplit = overrideSplit;
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
  public Function<Disposable, ToolContent<T>> getFactory() {
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

  /**
   * Specifies if a tool window will offer the option of being floating outside the {@link WorkBench}.
   */
  public boolean isFloatingAllowed() {
    return (myFeatures & ALLOW_FLOATING) != 0;
  }

  /**
   * Specifies if a tool window will offer the option of auto hide {@link WorkBench}.
   */
  public boolean isAutoHideAllowed() {
    return (myFeatures & ALLOW_AUTO_HIDE) != 0;
  }

  /**
   * Specifies if a tool window will offer the option of to change the split mode.
   */
  public boolean isSplitModeChangesAllowed() {
    return (myFeatures & ALLOW_SPLIT_MODE) != 0;
  }

  /**
   * When true, the gear action will be hidden from the tool window.
   */
  boolean showGearAction() {
    return myShowGearAction;
  }

  /**
   * When true, hide action will be hidden from the tool window.
   */
  boolean showHideAction() {
    return myShowHideAction;
  }

  /**
   * When true, the {@link Side} recorded by the system from the previous interaction with the Tool Window
   * will be overridden with the one defined in this {@link ToolWindowDefinition}.
   */
  boolean overrideSide() {
    return myOverrideSide;
  }

  /**
   * When true, the {@link Split} recorded by the system from the previous interaction with the Tool Window
   * will be overridden with the one defined in this {@link ToolWindowDefinition}.
   */
  boolean overrideSplit() {
    return myOverrideSplit;
  }
}
