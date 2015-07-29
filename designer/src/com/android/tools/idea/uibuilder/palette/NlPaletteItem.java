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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.google.common.base.Splitter;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.util.Collections;
import java.util.List;

public class NlPaletteItem {
  private final String myTitle;
  private final String myTooltip;
  private final String myIconPath;
  private final String myRepresentation;
  private final String myId;
  private final List<String> myLibraries;
  private String myStructureTitle;
  private String myFormat;
  private Icon myIcon;

  public NlPaletteItem(@NonNull String title, @NonNull String iconPath, @NonNull String tooltip, @NonNull String representation,
                       @NonNull String id, @NonNull String libraries, @NonNull String structureTitle, @Nullable String format) {
    myTitle = title;
    myIconPath = iconPath;
    myTooltip = tooltip;
    myRepresentation = representation;
    myId = id;
    myLibraries = fromCommaSeparatedList(libraries);
    myStructureTitle = structureTitle;
    myFormat = format;
  }

  @NonNull
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

  @NonNull
  public String getIconPath() {
    return myIconPath;
  }

  @NonNull
  public String getTooltip() {
    return myTooltip;
  }

  @NonNull
  public String getRepresentation() {
    return myRepresentation;
  }

  @NonNull
  public String getId() {
    return myId;
  }

  @NonNull
  public List<String> getLibraries() {
    return myLibraries;
  }

  @NonNull
  public String getStructureTitle() {
    return myStructureTitle;
  }

  @Nullable
  public String getStructureFormat() {
    return myFormat;
  }

  @NonNull
  private static List<String> fromCommaSeparatedList(@NonNull String libraries) {
    if (libraries.isEmpty()) {
      return Collections.emptyList();
    }
    return Splitter.on(",").splitToList(libraries);
  }
}
