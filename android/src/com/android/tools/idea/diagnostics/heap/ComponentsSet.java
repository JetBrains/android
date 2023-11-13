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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import com.google.protobuf.TextFormat;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public final class ComponentsSet {

  private static final Logger LOG = Logger.getInstance(ComponentsSet.class);

  public static final String MEMORY_USAGE_REPORTING_SERVER_FLAG_NAME =
    "diagnostics/memory_usage_reporting";

  private static final String INTEGRATION_TEST_CONFIG_RESOURCE_NAME = "/diagnostics/integration_test_memory_usage_config.textproto";

  static final String UNCATEGORIZED_CATEGORY_LABEL = "android:uncategorized";
  static final String UNCATEGORIZED_COMPONENT_LABEL = "uncategorized_main";

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
  @NotNull
  private final Multimap<String, ComponentCategory> categoriesTrackingClassName = HashMultimap.create();
  @NotNull
  private final Multimap<String, Component> componentsTrackingClassName = HashMultimap.create();
  private final long sharedClusterExtendedReportThreshold;

  static final private long TEST_COMPONENTS_EXTENDED_REPORT_THRESHOLD = 100_000_000;

  @TestOnly
  ComponentsSet() {
    this(TEST_COMPONENTS_EXTENDED_REPORT_THRESHOLD, TEST_COMPONENTS_EXTENDED_REPORT_THRESHOLD);
  }

  ComponentsSet(long sharedClusterExtendedReportThreshold, long uncategorizedComponentThreshold) {
    packageNameToComponentCache = Maps.newHashMap();
    classNameToComponent = Maps.newHashMap();
    components = new ArrayList<>();
    componentCategories = new ArrayList<>();
    this.sharedClusterExtendedReportThreshold = sharedClusterExtendedReportThreshold;
    uncategorizedComponent =
      registerComponent(UNCATEGORIZED_COMPONENT_LABEL,
                        uncategorizedComponentThreshold,
                        registerCategory(UNCATEGORIZED_CATEGORY_LABEL, uncategorizedComponentThreshold, Collections.emptyList()),
                        Collections.emptyList(), Collections.emptyList());
  }

  public long getSharedClusterExtendedReportThreshold() {
    return sharedClusterExtendedReportThreshold;
  }

  @NotNull
  public static MemoryUsageReportConfiguration getServerFlagConfiguration() {
    return ServerFlagService.Companion.getInstance()
      .getProto(MEMORY_USAGE_REPORTING_SERVER_FLAG_NAME,
                MemoryUsageReportConfiguration.getDefaultInstance());
  }

  @NotNull
  public static MemoryUsageReportConfiguration getIntegrationTestConfiguration() {
    MemoryUsageReportConfiguration.Builder builder = MemoryUsageReportConfiguration.newBuilder();
    try {
      TextFormat.merge(
        Resources.toString(
          Resources.getResource(ComponentsSet.class, INTEGRATION_TEST_CONFIG_RESOURCE_NAME),
          StandardCharsets.UTF_8),
        builder);
    }
    catch (IOException e) {
      LOG.error("Failed to read memory usage components configuration", e);
    }
    return builder.build();
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
  public Multimap<String, ComponentCategory> getCategoriesTrackingClassName() {
    return categoriesTrackingClassName;
  }

  @NotNull
  public Multimap<String, Component> getComponentsTrackingClassName() {
    return componentsTrackingClassName;
  }

  @NotNull
  Component registerComponent(@NotNull final String componentLabel,
                              long extendedReportCollectionThresholdBytes,
                              @NotNull final ComponentCategory category,
                              @NotNull final List<String> trackedFQNs,
                              @NotNull final List<String> customClassLoaders) {
    Component component =
      new Component(componentLabel, extendedReportCollectionThresholdBytes, customClassLoaders, components.size(), category);

    for (String fqn : trackedFQNs) {
      componentsTrackingClassName.put(fqn, component);
    }
    components.add(component);
    return component;
  }

  @TestOnly
  ComponentCategory registerCategory(@NotNull final String componentCategoryLabel) {
    return registerCategory(componentCategoryLabel, TEST_COMPONENTS_EXTENDED_REPORT_THRESHOLD, Collections.emptyList());
  }

  @TestOnly
  void addComponentWithPackagesAndClassNames(@NotNull final String componentLabel,
                                             @NotNull final ComponentCategory componentCategory,
                                             @NotNull final List<String> packageNames,
                                             @NotNull final List<String> classNames) {
    addComponentWithPackagesAndClassNames(componentLabel, TEST_COMPONENTS_EXTENDED_REPORT_THRESHOLD, componentCategory, packageNames,
                                          classNames, Collections.emptyList(), Collections.emptyList());
  }

  ComponentCategory registerCategory(@NotNull final String componentCategoryLabel,
                                     long extendedReportCollectionThresholdBytes,
                                     @NotNull final List<String> trackedFQNs) {
    ComponentCategory category =
      new ComponentCategory(componentCategories.size(), componentCategoryLabel, extendedReportCollectionThresholdBytes);
    for (String fqn : trackedFQNs) {
      categoriesTrackingClassName.put(fqn, category);
    }
    componentCategories.add(category);
    return category;
  }

  ComponentCategory registerCategory(@NotNull final MemoryUsageComponentCategory protoCategory) {
    return registerCategory(protoCategory.getLabel(), protoCategory.hasExtendedReportThresholdBytes()
                                                      ? protoCategory.getExtendedReportThresholdBytes()
                                                      : Long.MAX_VALUE, protoCategory.getTrackedFqnsList());
  }

  void addComponentWithPackagesAndClassNames(@NotNull final ComponentCategory componentCategory,
                                             @NotNull final MemoryUsageComponent component) {
    addComponentWithPackagesAndClassNames(component.getLabel(), component.hasExtendedReportThresholdBytes()
                                                                ? component.getExtendedReportThresholdBytes()
                                                                : Long.MAX_VALUE,
                                          componentCategory, component.getPackageNamesList(), component.getClassNamesList(),
                                          component.getTrackedFqnsList(), component.getCustomClassLoadersList());
  }

  void addComponentWithPackagesAndClassNames(@NotNull final String componentLabel,
                                             long extendedReportCollectionThresholdBytes,
                                             @NotNull final ComponentCategory componentCategory,
                                             @NotNull final List<String> packageNames,
                                             @NotNull final List<String> classNames,
                                             @NotNull final List<String> trackedFQNs,
                                             @NotNull final List<String> customClassLoaders) {
    Component newComponent =
      registerComponent(componentLabel, extendedReportCollectionThresholdBytes, componentCategory, trackedFQNs, customClassLoaders);

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

    if (Strings.containsChar(objClassName, '$')) {
      String outerClassName = objClassName.substring(0, Strings.indexOf(objClassName, '$'));
      if (classNameToComponent.containsKey(outerClassName)) {
        return classNameToComponent.get(outerClassName);
      }
    }

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

  private static ComponentsSet buildComponentSetFromConfiguration(MemoryUsageReportConfiguration configuration) {
    ComponentsSet components =
      new ComponentsSet(configuration.getSharedClusterExtendedReportThresholdBytes(),
                        configuration.getUncategorizedComponentExtendedReportThresholdBytes());

    for (MemoryUsageComponentCategory protoCategory : configuration.getCategoriesList()) {
      ComponentCategory category = components.registerCategory(protoCategory);
      for (MemoryUsageComponent component : protoCategory.getComponentsList()) {
        components.addComponentWithPackagesAndClassNames(category, component);
      }
    }

    for (int i = 0; i < components.getComponents().size(); i++) {
      assert components.getComponents().get(i).getId() == i;
    }

    for (int i = 0; i < components.getComponentsCategories().size(); i++) {
      assert components.getComponentsCategories().get(i).getId() == i;
    }

    return components;
  }

  @NotNull
  public static ComponentsSet buildComponentSet() {
    return buildComponentSetFromConfiguration(getServerFlagConfiguration());
  }

  @NotNull
  public static ComponentsSet buildComponentSetForIntegrationTesting() {
    return buildComponentSetFromConfiguration(getIntegrationTestConfiguration());
  }

  public static abstract class Cluster {
    private final int id;

    @NotNull
    private final String label;

    private final long extendedReportCollectionThresholdBytes;

    public Cluster(int id, @NotNull final String label, long extendedReportCollectionThresholdBytes) {
      this.id = id;
      this.label = label;
      this.extendedReportCollectionThresholdBytes = extendedReportCollectionThresholdBytes;
    }

    @NotNull
    public String getLabel() {
      return label;
    }

    public int getId() {
      return id;
    }

    public long getExtendedReportCollectionThresholdBytes() {
      return extendedReportCollectionThresholdBytes;
    }
  }

  public static final class Component extends Cluster {
    @NotNull
    private final ComponentCategory componentCategory;

    @NotNull
    final Set<String> customClassLoaders;

    private Component(@NotNull final String componentLabel,
                      long extendedReportCollectionThresholdBytes,
                      @NotNull final List<String> customClassLoaders,
                      int id,
                      @NotNull final ComponentCategory category) {
      super(id, componentLabel, extendedReportCollectionThresholdBytes);
      this.customClassLoaders = Sets.newHashSet(customClassLoaders);
      componentCategory = category;
    }

    @NotNull
    public ComponentCategory getComponentCategory() {
      return componentCategory;
    }
  }

  public static final class ComponentCategory extends Cluster {
    public ComponentCategory(int id, @NotNull String label, long extendedReportCollectionThresholdBytes) {
      super(id, label, extendedReportCollectionThresholdBytes);
    }
  }
}
