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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.SdkConstants;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.resources.ResourceType;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Stream;

/**
 * A class to find all styles that are derived from a given style.
 */
public class StyleFilter {
  private final Project myProject;
  private final ResourceResolver myResolver;
  private final Map<String, ResourceValue> myFrameworkStyles;
  private final Map<String, ResourceValue> myProjectStyles;
  private final Set<String> myDerivedStyles;
  private final Set<String> myOtherStyles;
  private final Set<String> myCurrentInheritanceChain;

  public StyleFilter(@NotNull Project project, @NotNull ResourceResolver resolver) {
    myProject = project;
    myResolver = resolver;
    myFrameworkStyles = resolver.getFrameworkResources().get(ResourceType.STYLE);
    myProjectStyles = resolver.getProjectResources().get(ResourceType.STYLE);
    myDerivedStyles = new HashSet<>();
    myOtherStyles = new HashSet<>();
    myCurrentInheritanceChain = new HashSet<>();
  }

  /**
   * Returns true if the specified <code>tagName</code> has dedicated styles.
   *
   * @param project  the current project
   * @param resolver a resource resolver
   * @param tagName  the tagName of the view we are inspecting
   * @return true if dedicated styles exists
   */
  public static boolean hasWidgetStyles(@NotNull Project project, @NotNull ResourceResolver resolver, @NotNull String tagName) {
    return !getWidgetStyleNames(project, resolver, tagName).isEmpty();
  }

  /**
   * Returns a {@link Stream<StyleResourceValue>} of styles that is derived from the dedicated
   * styles specified by the {@link ViewHandler} for this tag. The stream sorted into 3 sections:
   * <ul>
   * <li>User defined styles</li>
   * <li>Styles from a library</li>
   * <li>Framework styles</li>
   * </ul>
   * Each section is sorted by name.
   *
   * @param tagName
   * @return a sorted stream of styles grouped as mentioned above
   */
  public Stream<StyleResourceValue> getWidgetStyles(@NotNull String tagName) {
    myDerivedStyles.clear();
    myOtherStyles.clear();
    myDerivedStyles.addAll(getWidgetStyleNames(myProject, myResolver, tagName));
    return findDerivedStyles();
  }

  /**
   * Returns a {@link Stream<StyleResourceValue>} of styles that are derived from the <code>baseStyle</code>
   * specified. The resulting styles exclude all styles that start with "Base." and sorted into 3 sections:
   * <ul>
   * <li>User defined styles</li>
   * <li>Styles from a library</li>
   * <li>Framework styles</li>
   * </ul>
   * Each section is sorted by name.
   *
   * @param baseStyle        the name of the style that we want all styles to be derived from
   * @param isFrameworkStyle true if the base style is a framework style
   * @return a sorted stream of styles grouped as mentioned above
   */
  @NotNull
  public Stream<StyleResourceValue> getStylesDerivedFrom(@NotNull String baseStyle, boolean isFrameworkStyle) {
    myDerivedStyles.clear();
    myOtherStyles.clear();
    if (isFrameworkStyle) {
      myDerivedStyles.add(SdkConstants.PREFIX_ANDROID + baseStyle);
    }
    else {
      myDerivedStyles.add(baseStyle);
    }
    return findDerivedStyles();
  }

  private Stream<StyleResourceValue> findDerivedStyles() {
    List<StyleResourceValue> styles = new ArrayList<>(myFrameworkStyles.size() + myProjectStyles.size());
    myProjectStyles.values().forEach(style -> styles.add((StyleResourceValue)style));
    myFrameworkStyles.values().forEach(style -> styles.add((StyleResourceValue)style));
    return styles.stream()
      .filter(this::filter)
      .sorted(Comparator
                .comparing(ResourceValue::isUserDefined)
                .reversed()
                .thenComparing(ResourceReference::isFramework)
                .thenComparing(ResourceReference::getName));
  }

  @VisibleForTesting
  boolean filter(@NotNull StyleResourceValue style) {
    if (style.getName().startsWith("Base.")) {
      // AppCompat contains several styles that serves as base styles and that should not be selectable:
      return false;
    }
    if (style.isFramework() && style.getName().toLowerCase(Locale.US).equals(style.getName())) {
      // All lowercase styles in the framework should typically be hidden:
      return false;
    }
    myCurrentInheritanceChain.clear();
    return isDerived(style);
  }

  @VisibleForTesting
  boolean isDerived(@NotNull StyleResourceValue style) {
    String styleName = getStyleName(style);
    if (myDerivedStyles.contains(styleName)) {
      return true;
    }
    if (myOtherStyles.contains(styleName)) {
      return false;
    }
    StyleResourceValue parentStyle = myResolver.getParent(style);
    if (parentStyle != null && !myCurrentInheritanceChain.contains(styleName)) {
      myCurrentInheritanceChain.add(styleName);
      return found(styleName, isDerived(parentStyle));
    }
    return found(styleName, false);
  }

  @NotNull
  private static String getStyleName(@NotNull StyleResourceValue style) {
    if (style.isFramework()) {
      return SdkConstants.PREFIX_ANDROID + style.getName();
    }
    return style.getName();
  }

  private boolean found(@NotNull String name, boolean isIncluded) {
    if (isIncluded) {
      myDerivedStyles.add(name);
    }
    else {
      myOtherStyles.add(name);
    }
    return isIncluded;
  }

  @NotNull
  private static List<String> getWidgetStyleNames(@NotNull Project project, @NotNull ResourceResolver resolver, @NotNull String tagName) {
    ViewHandlerManager manager = ViewHandlerManager.get(project);
    ViewHandler handler = manager.getHandler(tagName);
    if (handler == null) {
      return Collections.emptyList();
    }
    List<String> possibleNames = handler.getBaseStyles(tagName);
    Map<String, ResourceValue> frameworkStyles = resolver.getFrameworkResources().get(ResourceType.STYLE);
    Map<String, ResourceValue> projectStyles = resolver.getProjectResources().get(ResourceType.STYLE);
    List<String> names = new ArrayList<>(4); // This is usually a small list
    for (String styleName : possibleNames) {
      if (frameworkStyles.containsKey(styleName)) {
        names.add(SdkConstants.PREFIX_ANDROID + styleName);
      }
      if (projectStyles.containsKey(styleName)) {
        names.add(styleName);
      }
    }
    return names;
  }
}
