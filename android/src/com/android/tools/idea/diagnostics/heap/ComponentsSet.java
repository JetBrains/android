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

  public static final String MEMORY_USAGE_REPORTING_SERVER_FLAG_NAME = "diagnostics/memory_usage_reporting";

  static final String UNCATEGORIZED_CATEGORY_LABEL = "android:uncategorized";
  static final String UNCATEGORIZED_COMPONENT_LABEL = "main";

  @NotNull
  private final Component myUncategorizedComponent;
  @NotNull
  private final List<Component> myComponents;
  @NotNull
  private final List<ComponentCategory> myComponentCategories;
  @NotNull
  private final Map<String, Component> myClassNameToComponent;
  @NotNull
  private final Map<String, Component> myPackageNameToComponentCache;

  ComponentsSet() {
    myPackageNameToComponentCache = Maps.newHashMap();
    myClassNameToComponent = Maps.newHashMap();
    myComponents = Lists.newArrayList();
    myComponentCategories = Lists.newArrayList();
    myUncategorizedComponent =
      registerComponent(UNCATEGORIZED_COMPONENT_LABEL,
                        registerCategory(UNCATEGORIZED_CATEGORY_LABEL));
  }

  @NotNull
  public List<ComponentCategory> getComponentsCategories() {
    return myComponentCategories;
  }

  @NotNull
  public Component getUncategorizedComponent() {
    return myUncategorizedComponent;
  }

  @NotNull
  public List<Component> getComponents() {
    return myComponents;
  }

  @NotNull
  Component registerComponent(@NotNull final String componentLabel,
                              @NotNull final ComponentCategory category) {
    Component component = new Component(componentLabel, myComponents.size(), category);
    myComponents.add(component);
    return component;
  }

  ComponentCategory registerCategory(@NotNull final String componentCategoryLabel) {
    ComponentCategory category = new ComponentCategory(componentCategoryLabel, myComponentCategories.size());
    myComponentCategories.add(category);
    return category;
  }

  void addComponentWithPackagesAndClassNames(@NotNull final String componentLabel,
                                             @NotNull final ComponentCategory componentCategory,
                                             @NotNull final List<String> packageNames,
                                             @NotNull final List<String> classNames) {
    Component newComponent = registerComponent(componentLabel, componentCategory);

    for (String name : classNames) {
      myClassNameToComponent.put(name, newComponent);
    }

    for (String name : packageNames) {
      myPackageNameToComponentCache.put(name, newComponent);
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

    if (myClassNameToComponent.containsKey(objClassName)) {
      return myClassNameToComponent.get(objClassName);
    }
    String packageName = aClass.getPackageName();

    int lastDot = packageName.length();
    String packageNamePrefix = packageName;
    do {
      packageNamePrefix = packageNamePrefix.substring(0, lastDot);
      if (myPackageNameToComponentCache.containsKey(packageNamePrefix)) {
        Component ans = myPackageNameToComponentCache.get(packageNamePrefix);
        myPackageNameToComponentCache.put(packageName, ans);
        return ans;
      }
      lastDot = packageNamePrefix.lastIndexOf('.');
    }
    while (lastDot > 0);

    myPackageNameToComponentCache.put(packageName, null);
    return null;
  }

  @NotNull
  public static ComponentsSet getComponentSet() {
    ComponentsSet components = new ComponentsSet();

    MemoryUsageReportConfiguration
      memoryUsageReportConfiguration = ServerFlagService.Companion.getInstance()
      .getProto(MEMORY_USAGE_REPORTING_SERVER_FLAG_NAME, MemoryUsageReportConfiguration.getDefaultInstance());

    for (MemoryUsageComponentCategory protoCategory : memoryUsageReportConfiguration.getCategoriesList()) {
      ComponentCategory category = components.registerCategory(protoCategory.getLabel());
      for (MemoryUsageComponent component : protoCategory.getComponentsList()) {
        components.addComponentWithPackagesAndClassNames(component.getLabel(), category, component.getPackageNamesList(),
                                                         component.getClassNamesList());
      }
    }

    return components;
  }

  public static final class Component {
    private final int myId;
    @NotNull
    private final String myComponentLabel;

    @NotNull
    private final ComponentCategory myComponentCategory;

    private Component(@NotNull final String componentLabel,
                      int id, @NotNull final ComponentCategory category) {
      myComponentLabel = componentLabel;
      myId = id;
      myComponentCategory = category;
    }

    @NotNull
    public ComponentCategory getComponentCategory() {
      return myComponentCategory;
    }

    @NotNull
    public String getComponentLabel() {
      return myComponentLabel;
    }

    public int getId() {
      return myId;
    }
  }

  public static final class ComponentCategory {
    private final int myId;
    @NotNull
    private final String myLabel;

    private ComponentCategory(@NotNull final String label,
                              int id) {
      myLabel = label;
      myId = id;
    }

    @NotNull
    public String getComponentCategoryLabel() {
      return myLabel;
    }

    public int getId() {
      return myId;
    }
  }
}
