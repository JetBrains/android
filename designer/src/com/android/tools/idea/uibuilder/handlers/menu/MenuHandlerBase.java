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
package com.android.tools.idea.uibuilder.handlers.menu;

import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.google.common.base.MoreObjects;
import com.intellij.openapi.util.IconLoader;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.SdkConstants.ATTR_LAYOUT_HEIGHT;
import static com.android.SdkConstants.ATTR_LAYOUT_WIDTH;

public class MenuHandlerBase extends ViewGroupHandler {
  @Override
  public final boolean onCreate(@NotNull ViewEditor editor,
                                @Nullable NlComponent parent,
                                @NotNull NlComponent newChild,
                                @NotNull InsertType insertType) {
    newChild.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);
    newChild.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);

    return true;
  }

  @NotNull
  @Override
  protected Icon loadBuiltinIcon(@NotNull String tagName) {
    return MoreObjects.firstNonNull(IconLoader.findIcon("AndroidIcons.MenuIcons." + tagName, getClass()), AndroidIcons.Views.Unknown);
  }
}
