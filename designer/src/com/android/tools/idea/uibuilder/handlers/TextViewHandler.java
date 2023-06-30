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

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.target.ComponentAssistantViewAction;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.api.XmlType;
import com.android.tools.idea.uibuilder.api.actions.ViewAction;
import com.android.tools.idea.uibuilder.handlers.actions.PickTextAppearanceViewAction;
import com.android.tools.idea.uibuilder.handlers.assistant.TextViewAssistant;
import com.android.tools.idea.uibuilder.assistant.ComponentAssistantFactory;
import com.android.xml.XmlBuilder;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.android.SdkConstants.*;

/**
 * Handler for several widgets that have a {@code text} attribute.
 */
public class TextViewHandler extends ViewHandler {
  private static final Set<String> HAVE_REDUCED_SCALE_IN_PREVIEW =
    ImmutableSet.of(AUTO_COMPLETE_TEXT_VIEW, EDIT_TEXT, MULTI_AUTO_COMPLETE_TEXT_VIEW);

  /**
   * Set of possible default values for a TextView <code>android:text</code> attribute.
   */
  private static final Set<String> TEXT_DEFAULT_VALUES =
    ImmutableSet.of("Hello World!", "TextView", "@string/hello_first_fragment", "@string/hello_blank_fragment", "@string/hello_world");

  @NotNull
  @Override
  public String getTitleAttributes(@NotNull NlComponent component) {
    String text = component.getAttribute(ANDROID_URI, ATTR_TEXT);
    if (!StringUtil.isEmpty(text)) {
      // Display the android:text attribute if this component has such an attribute.
      return String.format("\"%1$s\"", text);
    }
    return super.getTitleAttributes(component);
  }

  @Override
  @Language("XML")
  @NotNull
  public String getXml(@NotNull String tagName, @NotNull XmlType xmlType) {
    return new XmlBuilder()
      .startTag(tagName)
      .androidAttribute(ATTR_TEXT, tagName)
      .androidAttribute(ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT)
      .androidAttribute(ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT)
      .endTag(tagName)
      .toString();
  }

  @Override
  public double getPreviewScale(@NotNull String tagName) {
    // EditText components are scaled to avoid a large presentation on the palette
    if (HAVE_REDUCED_SCALE_IN_PREVIEW.contains(tagName)) {
      return 0.8;
    }
    return super.getPreviewScale(tagName);
  }

  @Override
  @NotNull
  public String getPreferredProperty() {
    return ATTR_TEXT;
  }

  @Nullable
  private static ComponentAssistantFactory getComponentAssistant(@NotNull NlComponent component) {
    String toolsText = component.getAttribute(TOOLS_URI, ATTR_TEXT);
    String text = component.getAttribute(ANDROID_URI, ATTR_TEXT);

    // Only display the assistant if:
    //  - tools:text is a tools sample data value
    //  OR
    //  - tools:text is null or empty AND android:text is null, empty or a default value
    boolean shouldDisplay = Strings.nullToEmpty(toolsText).startsWith(TOOLS_SAMPLE_PREFIX)
                            || (Strings.isNullOrEmpty(toolsText) && (Strings.isNullOrEmpty(text) || TEXT_DEFAULT_VALUES.contains(text)));

    if (!shouldDisplay) {
      return null;
    }

    return TextViewAssistant::createComponent;
  }

  @Override
  public boolean addPopupMenuActions(@NotNull SceneComponent component, @NotNull List<ViewAction> actions) {
    boolean cacheable = super.addPopupMenuActions(component, actions);

    actions.add(new ComponentAssistantViewAction(TextViewHandler::getComponentAssistant));

    return cacheable;
  }

  @Override
  public List<ViewAction> getPropertyActions(@NotNull List<NlComponent> components) {
    return ImmutableList.of(new PickTextAppearanceViewAction(ANDROID_URI, ATTR_TEXT_APPEARANCE));
  }
}
