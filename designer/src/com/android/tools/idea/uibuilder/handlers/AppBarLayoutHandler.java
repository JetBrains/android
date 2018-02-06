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
package com.android.tools.idea.uibuilder.handlers;

import com.android.tools.idea.uibuilder.handlers.linear.LinearLayoutHandler;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.common.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.ui.AppBarConfigurationDialog;
import com.android.tools.idea.common.model.NlComponent;

import java.util.Collections;
import java.util.List;

import static com.android.SdkConstants.*;

public class AppBarLayoutHandler extends LinearLayoutHandler {
  @Override
  @NotNull
  public List<String> getInspectorProperties() {
    return ImmutableList.of(
      ATTR_THEME,
      ATTR_FITS_SYSTEM_WINDOWS,
      ATTR_EXPANDED);
  }

  @NotNull
  @Override
  public List<String> getLayoutInspectorProperties() {
    return Collections.singletonList(ATTR_LAYOUT_SCROLL_FLAGS);
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType insertType) {
    if (insertType == InsertType.CREATE) {
      // The AppBarConfigurationDialog replaces the root XML node in the current file.
      AppBarConfigurationDialog dialog = new AppBarConfigurationDialog(editor);
      ApplicationManager.getApplication().invokeLater(() -> dialog.open());
      return false;
    }
    return true;
  }

  @Override
  public boolean isVertical(@NotNull NlComponent component) {
    // AppBarLayout is always vertical and does not support horizontal orientation
    // https://android.googlesource.com/platform/frameworks/support.git/+/master/design/src/android/support/design/widget/AppBarLayout.java#279
    return true;
  }
}
