// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.converter;

import com.intellij.conversion.ModuleSettings;
import java.util.Collection;
import org.jdom.Element;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class AndroidConversionUtil {
  @NonNls static final String OPTION_VALUE_ATTRIBUTE = "value";

  private AndroidConversionUtil() {
  }

  @Nullable
  public static String getOptionValue(@NotNull Element e, @NotNull String optionName) {
    Element element = getOptionElement(e, optionName);
    return element != null ? element.getAttributeValue(OPTION_VALUE_ATTRIBUTE) : null;
  }

  @Nullable
  public static Element getOptionElement(@NotNull Element e, @NotNull String optionName) {
    for (Element optionElement : e.getChildren("option")) {
      if (optionName.equals(optionElement.getAttributeValue("name"))) {
        return optionElement;
      }
    }
    return null;
  }

  @Nullable
  public static Element findAndroidFacetConfigurationElement(@Nullable ModuleSettings moduleSettings) {
    if (moduleSettings != null) {
      AndroidFacetType facetType = AndroidFacet.getFacetType();
      if (facetType != null) {
        final Collection<? extends Element> facetElements = moduleSettings.getFacetElements(facetType.getStringId());
        if (!facetElements.isEmpty()) {
          return facetElements.iterator().next().getChild("configuration");
        }
      }
    }
    return null;
  }
}
