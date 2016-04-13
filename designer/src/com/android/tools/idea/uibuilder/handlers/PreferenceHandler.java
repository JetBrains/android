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

import com.android.SdkConstants;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class PreferenceHandler extends ViewHandler {
  @Language("XML")
  @NotNull
  @Override
  public abstract String getXml(@NotNull String tagName, @NotNull XmlType xmlType);

  @Override
  public final boolean onCreate(@NotNull ViewEditor editor,
                                @Nullable NlComponent parent,
                                @NotNull NlComponent child,
                                @NotNull InsertType type) {
    // TODO Should I alter the logic at NlModel.java:798?
    child.removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_HEIGHT);
    child.removeAndroidAttribute(SdkConstants.ATTR_LAYOUT_WIDTH);

    return true;
  }
}
