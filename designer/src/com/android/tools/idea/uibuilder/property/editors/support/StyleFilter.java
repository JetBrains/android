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
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A class to find all styles that are derived from a given style.
 */
public class StyleFilter {
  private final AndroidFacet myFacet;
  private final ResourceResolver myResolver;

  public StyleFilter(@NotNull AndroidFacet facet, @Nullable ResourceResolver resolver) {
    myFacet = facet;
    myResolver = resolver;
  }

  /**
   * Returns true if the specified <code>tagName</code> has dedicated styles.
   *
   * @param tagName  the tagName of the view we are inspecting
   * @return true if dedicated styles exists
   */
  public boolean hasWidgetStyles(@NotNull String tagName) {
    return !getWidgetBaseStyles(tagName).isEmpty();
  }

  /**
   * Returns a {@link List<StyleResourceValue>} of styles that is derived from the dedicated
   * styles specified by the {@link ViewHandler} for this tag. The list sorted into groups:
   * <ul>
   * <li>User defined styles</li>
   * <li>Styles grouped by namespace</li>
   * </ul>
   * Each group is sorted by name.
   *
   * @param tagName an XML tag name
   * @return a sorted stream of styles grouped as mentioned above
   */
  public List<StyleResourceValue> getWidgetStyles(@NotNull String tagName) {
    return findDerivedStyles(getWidgetBaseStyles(tagName));
  }

  /**
   * Returns a {@link Stream<StyleResourceValue>} of styles that are derived from the <code>baseStyle</code>
   * specified. The resulting styles exclude all styles that start with "Base." and sorted into groups:
   * <ul>
   * <li>User defined styles</li>
   * <li>Styles grouped by namespace</li>
   * </ul>
   * Each group is sorted by name.
   *
   * @param baseStyle        the style that we want all styles to be derived from
   * @return a sorted list of styles grouped as mentioned above
   */
  @NotNull
  public List<StyleResourceValue> getStylesDerivedFrom(@NotNull StyleResourceValue baseStyle) {
    return findDerivedStyles(Collections.singletonList(baseStyle));
  }

  private List<StyleResourceValue> findDerivedStyles(@NotNull List<StyleResourceValue> baseStyles) {
    if (myResolver == null) {
      return baseStyles;
    }
    List<StyleResourceValue> bases = new ArrayList<>(baseStyles);
    Set<StyleResourceValue> styles = new HashSet<>();
    while (!bases.isEmpty()) {
      StyleResourceValue base = bases.remove(bases.size() -1);
      if (!styles.contains(base)) {
        styles.add(base);
        bases.addAll(myResolver.getChildren(base));
      }
    }
    return styles.stream()
      .filter(this::filter)
      .sorted(Comparator
                .comparing(ResourceValue::isUserDefined).reversed()
                .thenComparing(ResourceValue::getNamespace)
                .thenComparing(ResourceValue::getName))
      .collect(Collectors.toList());
  }

  @VisibleForTesting
  boolean filter(@NotNull StyleResourceValue style) {
    if (style.getName().startsWith("Base.")) {
      // AppCompat contains several styles that serves as base styles and that should not be selectable:
      return false;
    }
    if (style.getNamespace() == ResourceNamespace.ANDROID &&
        style.getName().toLowerCase(Locale.US).equals(style.getName())) {
      // All lowercase styles in the framework should typically be hidden:
      return false;
    }
    return true;
  }

  @NotNull
  private List<StyleResourceValue> getWidgetBaseStyles(@NotNull String tagName) {
    ViewHandlerManager manager = ViewHandlerManager.get(myFacet.getModule().getProject());
    ViewHandler handler = manager.getHandler(tagName);
    if (handler == null) {
      return Collections.emptyList();
    }
    List<StyleResourceValue> styles = new ArrayList<>();
    List<String> possibleNames = handler.getBaseStyles(tagName);
    Map<String, String> prefixMap = handler.getPrefixToNamespaceMap();
    for (String styleName : possibleNames) {
      StyleResourceValue style = resolve(styleName, prefixMap);
      if (style != null) {
        styles.add(style);
      }
    }
    return styles;
  }

  @Nullable
  private StyleResourceValue resolve(@NotNull String qualifiedStyleName, @NotNull Map<String, String> prefixMap) {
    if (myResolver == null) {
      return null;
    }
    ResourceUrl url = ResourceUrl.parseStyleParentReference(qualifiedStyleName);
    if (url == null) {
      return null;
    }
    ResourceReference reference = url.resolve(ResourceNamespace.ANDROID, prefixMap::get);
    if (reference == null) {
      return null;
    }
    return myResolver.getStyle(reference);
  }
}
