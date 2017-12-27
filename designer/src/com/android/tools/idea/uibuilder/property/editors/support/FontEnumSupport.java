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

import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.fonts.MoreFontsDialog;
import com.android.tools.idea.fonts.ProjectFonts;
import com.android.tools.idea.uibuilder.property.NlProperty;
import com.android.tools.idea.uibuilder.property.editors.support.ValueWithDisplayString.ValueSelector;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ColoredListCellRenderer;
import icons.AndroidIcons;
import icons.StudioIcons;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.android.ide.common.fonts.FontFamilyKt.FILE_PROTOCOL_START;
import static com.android.ide.common.fonts.FontFamilyKt.HTTPS_PROTOCOL_START;

public class FontEnumSupport extends EnumSupport {
  private ProjectFonts myProjectFonts;

  public FontEnumSupport(@NotNull NlProperty property) {
    super(property);
    myProjectFonts = new ProjectFonts(myProperty.getResolver());
  }

  @NotNull
  @Override
  public List<ValueWithDisplayString> getAllValues() {
    List<ValueWithDisplayString> values = new ArrayList<>();
    List<FontFamily> fonts = myProjectFonts.getFonts();
    for (FontFamily font : fonts) {
      values.add(new ValueWithDisplayString(font.getName(), "@font/" + font.getName()));
    }
    if (!values.isEmpty()) {
      values.add(ValueWithDisplayString.SEPARATOR);
    }
    for (String stringValue : AndroidDomUtil.AVAILABLE_FAMILIES) {
      values.add(new ValueWithDisplayString(stringValue, stringValue));
    }
    AndroidFacet facet = myProperty.getModel().getFacet();
    ResourceResolver resolver = myProperty.getResolver();
    if (resolver != null) {
      values.add(ValueWithDisplayString.SEPARATOR);
      values.add(new ValueWithDisplayString("More Fonts...", null, null,
                                            new MoreFontSelector(facet, resolver)));
    }
    return values;
  }

  @Override
  public boolean customizeCellRenderer(@NotNull ColoredListCellRenderer<ValueWithDisplayString> renderer,
                                       @NotNull ValueWithDisplayString value,
                                       boolean selected) {
    String fontValue = value.getValue();
    if (fontValue == null && value.getValueSelector() == null) {
      fontValue = myProperty.resolveValue(null);
    }
    if (fontValue == null) {
      return false;
    }
    FontFamily fontFamily = myProjectFonts.getFont(fontValue);
    switch (fontFamily.getFontSource()) {
      case SYSTEM:
        renderer.setIcon(AndroidIcons.Android);
        break;
      case PROJECT:
        if (fontFamily.getMenu().startsWith(FILE_PROTOCOL_START)) {
          renderer.setIcon(AndroidIcons.FontFile);
        }
        else if (fontFamily.getMenu().startsWith(HTTPS_PROTOCOL_START)) {
          renderer.setIcon(StudioIcons.Common.LINK);
        }
        else {
          renderer.setIcon(AllIcons.General.BalloonError);
        }
        break;
      default:
        break;
    }
    return false;
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
