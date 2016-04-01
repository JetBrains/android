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
package com.android.tools.idea.uibuilder.handlers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import icons.AndroidIcons;
import org.intellij.lang.annotations.Language;

import javax.swing.*;

/**
 * Handler for the {@code requestFocus} tag.
 */
public class RequestFocusHandler extends ViewHandler {

  @Override
  @NotNull
  public String getTitle(@NotNull String tagName) {
    return "<requestFocus>";
  }

  @Override
  @NotNull
  public String getTitle(@NotNull NlComponent component) {
    return "<requestFocus>";
  }

  @Override
  @NotNull
  public Icon getIcon(@NotNull String tagName) {
    return AndroidIcons.Views.RequestFocus;
  }

  @Override
  @NotNull
  public Icon getIcon(@NotNull NlComponent component) {
    return AndroidIcons.Views.RequestFocus;
  }

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    switch (xmlType) {
      case COMPONENT_CREATION:
        return "<requestFocus/>";
      default:
        return NO_PREVIEW;
    }
  }
}
