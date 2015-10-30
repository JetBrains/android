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

import com.android.annotations.NonNull;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.model.NlComponent;
import org.intellij.lang.annotations.Language;

import static com.android.SdkConstants.RADIO_GROUP;

/**
 * Handler for the {@code <RadioGroup>} layout
 */
@SuppressWarnings("unused") // Loaded by reflection
public class RadioGroupHandler extends LinearLayoutHandler {

  @NonNull
  @Override
  public String getTitleAttributes(@NonNull NlComponent component) {
    if (!component.getTagName().equals(RADIO_GROUP)) {
      return super.getTitleAttributes(component);
    }
    return isVertical(component) ? "(vertical)" : "(horizontal)";
  }

  @Override
  @Language("XML")
  @NonNull
  public String getXml(@NonNull String tagName, @NonNull XmlType xmlType) {
    // Use the default layout parameters for a horizontal RadioGroup:
    switch (xmlType) {
      case COMPONENT_CREATION:
        return String.format("<%1$s\n" +
                             "  android:layout_width=\"wrap_content\"\n" +
                             "  android:layout_height=\"wrap_content\">\n" +
                             "</%1$s>\n", tagName);
      default:
        return super.getXml(tagName, xmlType);
    }
  }
}
