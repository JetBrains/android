/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.intellij.icons.AllIcons;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

/**
 * The possible attached locations of an AttachedToolWindow.
 */
public enum AttachedLocation {
  LeftTop("Left Top", AllIcons.Actions.MoveToLeftTop),
  LeftBottom("Left Bottom", AllIcons.Actions.MoveToLeftBottom),
  RightTop("Right Top", AllIcons.Actions.MoveToRightTop),
  RightBottom("Right Bottom", AllIcons.Actions.MoveToRightBottom);

  private final String myTitle;
  private final Icon myIcon;

  AttachedLocation(@NotNull String title, @NotNull Icon icon) {
    myTitle = title;
    myIcon = icon;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  public boolean isLeft() {
    return this == LeftTop || this == LeftBottom;
  }

  public boolean isBottom() {
    return this == LeftBottom || this == RightBottom;
  }
}
