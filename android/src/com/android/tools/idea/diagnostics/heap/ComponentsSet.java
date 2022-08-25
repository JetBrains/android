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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ComponentsSet {

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
  }

  @NotNull
  public List<ComponentCategory> getComponentsCategories() {
    return myComponentCategories;
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

  void addComponentWithPackages(@NotNull final String componentLabel,
                                @NotNull final ComponentCategory componentCategory,
                                String... packageNames) {
    Component newComponent = registerComponent(componentLabel, componentCategory);

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
    // Android: ResourceManager
    components.addComponentWithPackages("ANDROID_RESOURCE_MANAGER",
                                        components.registerCategory("ANDROID_RESOURCE_MANAGER_CATEGORY"),
                                        "org.jetbrains.android.resourceManagers");
    // Android: ResourceRepository
    components.addComponentWithPackages("ANDROID_RESOURCE_REPOSITORY",
                                        components.registerCategory("ANDROID_RESOURCE_REPOSITORY_CATEGORY"),
                                        "org.jetbrains.android.facet", "com.android.tools.idea.res");
    // Android: DesignTools
    ComponentCategory designToolsCategory = components.registerCategory("ANDROID_DESIGN_TOOLS_CATEGORY");
    components.addComponentWithPackages("ANDROID_RENDERING",
                                        designToolsCategory, "android", "androidx");
    components.addComponentWithPackages("ANDROID_LAYOUT_EDITOR",
                                        designToolsCategory,
                                        "com.android.tools.idea.common.surface", "com.android.tools.idea.common.scene",
                                        "com.android.tools.idea.common.editor", "com.android.tools.idea.uibuilder",
                                        "com.android.tools.idea.configurations");
    components.addComponentWithPackages("ANDROID_LAYOUTLIB",
                                        designToolsCategory, "com.android.layoutlib",
                                        "com.android.tools.idea.layoutlib");
    components.addComponentWithPackages("ANDROID_NAVEDITOR",
                                        designToolsCategory,
                                        "com.android.tools.idea.naveditor");
    // Android: Gradle
    components.addComponentWithPackages("ANDROID_GRADLE",
                                        components.registerCategory("ANDROID_GRADLE_CATEGORY"), "com.android.tools.idea.gradle",
                                        "org.jetbrains.plugins.gradle");
    // Android: Profiler
    ComponentCategory profilerCategory = components.registerCategory("ANDROID_PROFILER_CATEGORY");
    components.addComponentWithPackages("ANDROID_PROFILER",
                                        profilerCategory, "com.android.tools.profilers",
                                        "com.android.tools.profiler", "com.android.tools.idea.profilers", "com.android.tools.perflib",
                                        "trebuchet.io");
    components.addComponentWithPackages("ANDROID_TRANSPORT",
                                        profilerCategory,
                                        "com.android.tools.idea.transport", "com.android.tools.datastore");

    // Android: Templates
    components.addComponentWithPackages("ANDROID_TEMPLATES",
                                        components.registerCategory("ANDROID_TEMPLATES_CATEGORY"),
                                        "com.android.tools.idea.templates");
    // Android: RunDebug
    ComponentCategory runDebugCategory = components.registerCategory("ANDROID_RUN_DEBUG_CATEGORY");
    components.addComponentWithPackages("ANDROID_RUN_DEPLOYER",
                                        runDebugCategory, "com.android.tools.idea.run",
                                        "com.android.tools.deployer");
    components.addComponentWithPackages("ANDROID_LOGCAT",
                                        runDebugCategory,
                                        "com.android.tools.idea.logcat");
    components.addComponentWithPackages("ANDROID_DDM",
                                        runDebugCategory, "com.android.tools.idea.ddms",
                                        "com.android.ddmlib");
    components.addComponentWithPackages("ANDROID_DEBUG",
                                        runDebugCategory,
                                        "com.android.tools.idea.debug");
    components.addComponentWithPackages("ANDROID_APK_VIEW",
                                        runDebugCategory,
                                        "com.android.tools.idea.apk.viewer");

    // Android: Lint
    ComponentCategory lintCategory = components.registerCategory("ANDROID_LINT_CATEGORY");
    components.addComponentWithPackages("ANDROID_LINT",
                                        lintCategory, "com.android.tools.lint",
                                        "com.android.tools.idea.lint", "com.android.tools.idea.common.lint");
    // Android: C++
    components.addComponentWithPackages("ANDROID_CPP",
                                        lintCategory, "com.android.tools.ndk",
                                        "com.jetbrains.cidr");
    // Android: AVDManager
    ComponentCategory avdmanagerCategory = components.registerCategory("ANDROID_AVDMANAGER_CATEGORY");
    components.addComponentWithPackages("ANDROID_AVDMANAGER",
                                        avdmanagerCategory,
                                        "com.android.tools.idea.avdmanager", "com.android.sdklib.internal.avd");
    // Android: Editor
    ComponentCategory editorsCategory = components.registerCategory("ANDROID_EDITORS_CATEGORY");
    components.addComponentWithPackages("ANDROID_EDITORS",
                                        editorsCategory,
                                        "com.android.tools.idea.editors");
    components.addComponentWithPackages("ANDROID_INSPECTIONS",
                                        editorsCategory,
                                        "org.jetbrains.android.inspections");
    // Android: Diagnostics
    components.addComponentWithPackages("ANDROID_DIAGNOSTICS",
                                        components.registerCategory("ANDROID_DIAGNOSTICS_CATEGORY"),
                                        "com.android.tools.idea.diagnostics", "com.android.tools.analytics");
    // Android: DataBinding
    components.addComponentWithPackages("ANDROID_DATA_BINDINGS",
                                        components.registerCategory("ANDROID_DATA_BINDINGS_CATEGORY"),
                                        "com.android.tools.idea.databinding", "com.android.tools.idea.lang.databinding");
    // Android: SDK
    components.addComponentWithPackages("ANDROID_SDK",
                                        components.registerCategory("ANDROID_SDK_CATEGORY"), "com.android.sdklib",
                                        "org.jetbrains.android.sdk");
    // Intellij: Platform
    ComponentCategory platformCategory = components.registerCategory("ANDROID_SDK_CATEGORY");
    components.addComponentWithPackages("INTELLIJ_KOTLIN",
                                        platformCategory, "org.jetbrains.kotlin");
    components.addComponentWithPackages("INTELLIJ_PROJECT",
                                        platformCategory,
                                        "com.intellij.openapi.project");
    components.addComponentWithPackages("INTELLIJ_APPLICATION",
                                        platformCategory,
                                        "com.intellij.openapi.application");
    components.addComponentWithPackages("INTELLIJ_MODULE",
                                        platformCategory,
                                        "com.intellij.workspaceModel.ide.impl.legacyBridge.module", "com.intellij.openapi.module");
    components.addComponentWithPackagesAndClassNames("INTELLIJ_VFS",
                                                     platformCategory,
                                                     ImmutableList.of("com.intellij.openapi.vfs"),
                                                     ImmutableList.of("com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl",
                                                                      "com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher"));
    components.addComponentWithPackages("INTELLIJ_EDITOR",
                                        platformCategory, "com.intellij.openapi.editor",
                                        "com.intellij.openapi.fileEditor");
    components.addComponentWithPackages("INTELLIJ_PSI",
                                        platformCategory, "com.intellij.psi");
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
