/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.diagnostics.heap;

import com.android.tools.idea.serverflags.ServerFlagService;
import com.android.tools.idea.serverflags.protos.MemoryUsageComponent;
import com.android.tools.idea.serverflags.protos.MemoryUsageComponentCategory;
import com.android.tools.idea.serverflags.protos.MemoryUsageReportConfiguration;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ComponentsSet {

  public static final String MEMORY_USAGE_REPORTING_SERVER_FLAG_NAME =
    "diagnostics/memory_usage_reporting";

  static final String UNCATEGORIZED_CATEGORY_LABEL = "android:uncategorized";
  static final String UNCATEGORIZED_COMPONENT_LABEL = "main";

  @NotNull
  private final Component uncategorizedComponent;
  @NotNull
  private final List<Component> components;
  @NotNull
  private final List<ComponentCategory> componentCategories;
  @NotNull
  private final Map<String, Component> classNameToComponent;
  @NotNull
  private final Map<String, Component> packageNameToComponentCache;

  ComponentsSet() {
    packageNameToComponentCache = Maps.newHashMap();
    classNameToComponent = Maps.newHashMap();
    components = Lists.newArrayList();
    componentCategories = Lists.newArrayList();
    uncategorizedComponent =
      registerComponent(UNCATEGORIZED_COMPONENT_LABEL,
                        registerCategory(UNCATEGORIZED_CATEGORY_LABEL));
  }

  @NotNull
  public static MemoryUsageReportConfiguration getMemoryUsageReportConfiguration() {
    return ServerFlagService.Companion.getInstance()
      .getProto(MEMORY_USAGE_REPORTING_SERVER_FLAG_NAME,
                MemoryUsageReportConfiguration.getDefaultInstance());
  }

  @NotNull
  public List<ComponentCategory> getComponentsCategories() {
    return componentCategories;
  }

  @NotNull
  public Component getUncategorizedComponent() {
    return uncategorizedComponent;
  }

  @NotNull
  public List<Component> getComponents() {
    return components;
  }

  @NotNull
  Component registerComponent(@NotNull final String componentLabel,
                              @NotNull final ComponentCategory category) {
    Component component = new Component(componentLabel, components.size(), category);
    components.add(component);
    return component;
  }

  ComponentCategory registerCategory(@NotNull final String componentCategoryLabel) {
    ComponentCategory category =
      new ComponentCategory(componentCategoryLabel, componentCategories.size());
    componentCategories.add(category);
    return category;
  }

  void addComponentWithPackagesAndClassNames(@NotNull final String componentLabel,
                                             @NotNull final ComponentCategory componentCategory,
                                             @NotNull final List<String> packageNames,
                                             @NotNull final List<String> classNames) {
    Component newComponent = registerComponent(componentLabel, componentCategory);

    for (String name : classNames) {
      classNameToComponent.put(name, newComponent);
    }

    for (String name : packageNames) {
      packageNameToComponentCache.put(name, newComponent);
    }
  }

  @Nullable
  public Component getComponentOfObject(@NotNull final Object obj) {
    return (obj instanceof Class) ? getClassComponent((Class<?>)obj) : getObjectComponent(obj);
  }

  @Nullable
  private Component getObjectComponent(@NotNull final Object obj) {
    return getClassComponent(obj.getClass());
  }

  @Nullable
  private Component getClassComponent(@NotNull final Class<?> aClass) {
    String objClassName = aClass.getName();

    if (classNameToComponent.containsKey(objClassName)) {
      return classNameToComponent.get(objClassName);
    }
    String packageName = aClass.getPackageName();

    int lastDot = packageName.length();
    String packageNamePrefix = packageName;
    do {
      packageNamePrefix = packageNamePrefix.substring(0, lastDot);
      if (packageNameToComponentCache.containsKey(packageNamePrefix)) {
        Component ans = packageNameToComponentCache.get(packageNamePrefix);
        packageNameToComponentCache.put(packageName, ans);
        return ans;
      }
      lastDot = packageNamePrefix.lastIndexOf('.');
    }
    while (lastDot > 0);

    packageNameToComponentCache.put(packageName, null);
    return null;
  }

  @NotNull
  public static ComponentsSet getComponentSet() {
    ComponentsSet components = new ComponentsSet();

    for (MemoryUsageComponentCategory protoCategory : getMemoryUsageReportConfiguration().getCategoriesList()) {
      ComponentCategory category = components.registerCategory(protoCategory.getLabel());
      for (MemoryUsageComponent component : protoCategory.getComponentsList()) {
        components.addComponentWithPackagesAndClassNames(component.getLabel(), category,
                                                         component.getPackageNamesList(),
                                                         component.getClassNamesList());
      }
    }

    return components;
  }

  public static final class Component {
    private final int id;
    @NotNull
    private final String componentLabel;

    @NotNull
    private final ComponentCategory componentCategory;

    private Component(@NotNull final String componentLabel,
                      int id, @NotNull final ComponentCategory category) {
      this.componentLabel = componentLabel;
      this.id = id;
      componentCategory = category;
    }

    @NotNull
    public ComponentCategory getComponentCategory() {
      return componentCategory;
    }

    @NotNull
    public String getComponentLabel() {
      return componentLabel;
    }

    public int getId() {
      return id;
    }
  }

  public static final class ComponentCategory {
    private final int id;
    @NotNull
    private final String label;

    private ComponentCategory(@NotNull final String label,
                              int id) {
      this.label = label;
      this.id = id;
    }

    @NotNull
    public String getComponentCategoryLabel() {
      return label;
    }

    public int getId() {
      return id;
    }
  }
}
