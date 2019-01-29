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
package com.android.tools.idea.uibuilder.property.editors.support;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.tools.idea.common.property.NlProperty;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;

public class TextAppearanceEnumSupport extends StyleEnumSupport {
  static final String TEXT_APPEARANCE = "TextAppearance";
  static final Pattern TEXT_APPEARANCE_PATTERN = Pattern.compile("^((@(\\w+:)?)style/)?TextAppearance(\\.(.+))?$");

  public TextAppearanceEnumSupport(@NotNull NlProperty property) {
    super(property);
  }

  @VisibleForTesting
  TextAppearanceEnumSupport(@NotNull NlProperty property,
                            @NotNull StyleFilter styleFilter,
                            @NotNull ResourceRepositoryManager resourceManager) {
    super(property, styleFilter, resourceManager);
  }

  @Override
  @NotNull
  public List<ValueWithDisplayString> getAllValues() {
    StyleResourceValue style = resolve(ResourceNamespace.ANDROID, TEXT_APPEARANCE);
    if (style == null) {
      return Collections.emptyList();
    }
    return convertStylesToDisplayValues(myStyleFilter.getStylesDerivedFrom(style));
  }

  /**
   * Guess the correct prefix if it is missing.
   *
   * @TODO: The code here should really look at the prefixes used in XML.
   */
  @Override
  @NotNull
  protected ValueWithDisplayString createFromResolvedValue(@NotNull String resolvedValue, @Nullable String value, @Nullable String hint) {
    if (value != null && !value.startsWith(PREFIX_RESOURCE_REF) && !value.startsWith(PREFIX_THEME_REF)) {
      // The user did not specify a proper style value.
      // Lookup the value specified to see if there is a matching style.
      ResourceNamespace currentNamespace = myResourceManager.getNamespace();

      // Prefer the users styles:
      StyleResourceValue styleFound = resolve(currentNamespace, value);

      // Otherwise try each of the namespaces defined in the XML file:
      if (styleFound == null) {
        for (String namespaceUri : findKnownNamespaces()) {
          ResourceNamespace namespace = ResourceNamespace.fromNamespaceUri(namespaceUri);
          if (namespace == null) {
            continue;
          }
          StyleResourceValue resource = resolve(namespace, value);
          if (resource != null) {
            styleFound = resource;
            break;
          }
        }
      }

      value = styleFound != null ?
              styleFound.asReference().getRelativeResourceUrl(currentNamespace, getResolver()).toString() : STYLE_RESOURCE_PREFIX + value;
    }
    String display;
    Matcher matcher = TEXT_APPEARANCE_PATTERN.matcher(resolvedValue);
    if (matcher.matches()) {
      display = matcher.group(5);
      if (display == null) {
        display = TEXT_APPEARANCE;
      }
    }
    else {
      String shortDisplay = StringUtil.substringAfter(resolvedValue, REFERENCE_STYLE);
      display = shortDisplay != null ? shortDisplay : resolvedValue;
    }
    return new ValueWithDisplayString(display, value, generateHint(display, value));
  }

  @Nullable
  @Override
  protected StyleResourceValue resolve(@NotNull ResourceNamespace namespace, @NotNull String styleName) {
    StyleResourceValue value = super.resolve(namespace, styleName);
    if (value != null) {
      return value;
    }
    return super.resolve(namespace, TEXT_APPEARANCE + "." + styleName);
  }
}
