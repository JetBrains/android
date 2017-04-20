/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.fonts.MoreFontsDialog;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString.ValueSelector;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.util.text.StringUtil;
import icons.AndroidIcons;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.uibuilder.property.ToggleDownloadableFontsAction.ENABLE_DOWNLOADABLE_FONTS;

public class FontEnumSupport extends EnumSupport {

  public FontEnumSupport(@NotNull NlProperty property) {
    super(property);
  }

  @NotNull
  @Override
  public List<ValueWithDisplayString> getAllValues() {
    List<ValueWithDisplayString> values = new ArrayList<>();
    for (String stringValue : AndroidDomUtil.AVAILABLE_FAMILIES) {
      values.add(new ValueWithDisplayString(stringValue, stringValue));
    }
    AndroidFacet facet = myProperty.getModel().getFacet();
    ResourceResolver resolver = myProperty.getResolver();
    if (resolver != null) {
      for (String font : resolver.getProjectResources().get(ResourceType.FONT).keySet()) {
        values.add(new ValueWithDisplayString(font, "@font/" + font));
      }
      if (PropertiesComponent.getInstance().getBoolean(ENABLE_DOWNLOADABLE_FONTS)) {
        values.add(new ValueWithDisplayString("More Fonts...", null, null, null,
                                              new MoreFontSelector(facet, resolver)));
      }
    }
    return values;
  }

  @NotNull
  @Override
  protected ValueWithDisplayString createFromResolvedValue(@NotNull String resolvedValue, @Nullable String value, @Nullable String hint) {
    return new ValueWithDisplayString(resolvedValue, value);
  }

  private static class MoreFontSelector implements ValueSelector {
    private final AndroidFacet myFacet;
    private final ResourceResolver myResolver;

    MoreFontSelector(@NotNull AndroidFacet facet, @NotNull ResourceResolver resolver) {
      myFacet = facet;
      myResolver = resolver;
    }

    @Nullable
    @Override
    public ValueWithDisplayString selectValue(@Nullable String currentValue) {
      MoreFontsDialog dialog = new MoreFontsDialog(myFacet, myResolver, currentValue);
      dialog.show();
      String font = dialog.isOK() ? dialog.getResultingFont() : null;
      if (font == null) {
        return null;
      }
      return new ValueWithDisplayString(StringUtil.trimStart(font, "@font/"), font);
    }
  }
}
