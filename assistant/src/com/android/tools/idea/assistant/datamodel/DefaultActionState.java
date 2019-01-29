/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.assistant.AssistActionState;
import com.android.tools.idea.assistant.view.UIUtils;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * Default set of action states for StatefulButtons that offers enough states for a loading button
 */
public enum DefaultActionState implements AssistActionState {
  /**
   * Action is not complete
    */
  INCOMPLETE(true, true, false),

  /**
   * Action is complete and may not be run again.
   */
  COMPLETE(false, false, true, AllIcons.RunConfigurations.TestPassed, UIUtils.getSuccessColor()),

  /**
   * Action is complete for a subset of cases and may still be run again.
   */
  PARTIALLY_COMPLETE(true, true, true, AllIcons.RunConfigurations.TestPassed, UIUtils.getSuccessColor()),

  /**
   * Use this to disable the button and indicate the state is being determined.
   */
  IN_PROGRESS(true, false, true),

  /**
   * There is a pre-existing error condition that prevents completion.
   */
  ERROR(false, false, true, AllIcons.RunConfigurations.TestFailed, UIUtils.getFailureColor()),

  /**
   * Similar to INCOMPLETE but action completion doesn't make sense. e.g. Trigger debug event.
   */
  NOT_APPLICABLE(true, true, true),

  /**
   * Action failed but can be retried.
    */
  ERROR_RETRY(true, true, true, AllIcons.RunConfigurations.TestFailed, UIUtils.getFailureColor());

  private final boolean myIsButtonEnabled;
  private final boolean myIsButtonVisible;
  private final boolean myIsMessageVisible;
  @Nullable private final Icon myIcon;
  @NotNull private final Color myForegroundColor;

  DefaultActionState(boolean isButtonVisible, boolean isButtonEnabled, boolean isMessageVisible) {
    this(isButtonVisible, isButtonEnabled, isMessageVisible, null, JBColor.BLACK);
  }

  DefaultActionState(boolean isButtonVisible,
                     boolean isButtonEnabled,
                     boolean isMessageVisible,
                     @Nullable Icon icon,
                     @NotNull Color foregroundColor) {
    myIsButtonVisible = isButtonVisible;
    myIsButtonEnabled = isButtonEnabled;
    myIsMessageVisible = isMessageVisible;
    myIcon = icon;
    myForegroundColor = foregroundColor;
  }

  @Override
  public boolean isButtonVisible() {
    return myIsButtonVisible;
  }

  @Override
  public boolean isButtonEnabled() {
    return myIsButtonEnabled;
  }

  @Override
  public boolean isMessageVisible() {
    return myIsMessageVisible;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return myIcon;
  }

  @NotNull
  @Override
  public Color getForeground() {
    return myForegroundColor;
  }
}
