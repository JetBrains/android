/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class NlPaletteItem {
  @NotNull private final String myTitle;
  @NotNull private final String myTooltip;
  @NotNull private final String myIconPath;
  @NotNull private final String myRepresentation;
  @NotNull private final String myId;
  @Nullable private Icon myIcon;

  public NlPaletteItem(@NotNull String title, @NotNull String iconPath, @NotNull String tooltip, @NotNull String representation,
                       @NotNull String id) {
    myTitle = title;
    myIconPath = iconPath;
    myTooltip = tooltip;
    myRepresentation = representation;
    myId = id;
  }

  @NotNull
  public String getTitle() {
    return myTitle;
  }

  @Nullable
  public Icon getIcon() {
    if (myIcon == null) {
      myIcon = IconLoader.findIcon(myIconPath);
    }
    return myIcon;
  }

  @NotNull
  public String getIconPath() {
    return myIconPath;
  }

  @NotNull
  public String getTooltip() {
    return myTooltip;
  }

  @NotNull
  public String getRepresentation() {
    return myRepresentation;
  }

  @NotNull
  public String getId() {
    return myId;
  }
}
