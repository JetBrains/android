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

import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.XmlBuilder;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.ATTR_TITLE;
import static com.android.SdkConstants.PreferenceAttributes.DEFAULT_VALUE;
import static com.android.SdkConstants.PreferenceAttributes.KEY;
import static com.android.SdkConstants.PreferenceTags.CHECK_BOX_PREFERENCE;

final class CheckBoxPreferenceHandler extends PreferenceHandler {
  @Language("XML")
  @NotNull
  @Override
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    return new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(DEFAULT_VALUE, false)
      .androidAttribute(ATTR_TITLE, "Check box preference")
      .endTag(tagName)
      .toString();
  }

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    super.onCreate(editor, parent, newChild, type);
    newChild.setAndroidAttribute(KEY, generateKey(newChild, CHECK_BOX_PREFERENCE, "check_box_preference_"));

    return true;
  }
}
