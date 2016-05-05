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
package com.android.tools.idea.uibuilder.handlers.preference;

import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.intellij.psi.xml.XmlTag;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

import static com.android.SdkConstants.*;
import static com.android.SdkConstants.PreferenceAttributes.KEY;

abstract class PreferenceHandler extends ViewHandler {
  @Language("XML")
  @NotNull
  @Override
  public abstract String getXml(@NotNull String tagName, @NotNull XmlType xmlType);

  @Override
  public boolean onCreate(@NotNull ViewEditor editor,
                          @Nullable NlComponent parent,
                          @NotNull NlComponent newChild,
                          @NotNull InsertType type) {
    // TODO Should I alter the logic at NlModel.java:798?
    newChild.removeAndroidAttribute(ATTR_LAYOUT_HEIGHT);
    newChild.removeAndroidAttribute(ATTR_LAYOUT_WIDTH);

    return true;
  }

  // TODO This generates a key unique across the file. Keys really should be unique across the application.
  @NotNull
  static String generateKey(@NotNull NlComponent component, @NotNull String tagName, @NotNull String keyPrefix) {
    XmlTag preferenceScreen = component.getModel().getFile().getRootTag();

    if (preferenceScreen == null) {
      return "";
    }

    XmlTag[] preferences = preferenceScreen.findSubTags(tagName);
    int i = 1;
    String key = keyPrefix + i++;

    while (anyPreferenceKeyEquals(preferences, key)) {
      key = keyPrefix + i++;
    }

    return key;
  }

  private static boolean anyPreferenceKeyEquals(@NotNull XmlTag[] preferences, @NotNull String key) {
    return Arrays.stream(preferences)
      .map(preference -> preference.getAttributeValue(KEY, ANDROID_URI))
      .anyMatch(key::equals);
  }
}
