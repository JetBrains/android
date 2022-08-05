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
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public final class ComponentsSet {

  private static final int COMPONENT_NOT_FOUND = -1;

  @NotNull
  private final List<Component> myComponents;
  @NotNull
  private final Object2IntMap<String> myClassNameToComponentId;
  @NotNull
  private final Object2IntMap<String> myPackageNameToComponentIdCache;

  ComponentsSet() {
    myPackageNameToComponentIdCache = new Object2IntOpenHashMap<>();
    myClassNameToComponentId = new Object2IntOpenHashMap<>();
    myComponents = new ArrayList<>();
  }

  @NotNull
  public static ComponentsSet getComponentSet() {
    ComponentsSet components = new ComponentsSet();
    components.addComponentWithPackages("android: resourceManager", "org.jetbrains.android.resourceManagers");
    components.addComponentWithPackages("android: resourceRepository", "org.jetbrains.android.facet", "com.android.tools.idea.res");
    components.addComponentWithPackages("android: rendering", "android", "androidx", "com.android.tools.idea.rendering");
    components.addComponentWithPackages("intellij: psi", "com.intellij.psi");
    components.addComponentWithPackages("android: layoutlib", "com.android.layoutlib", "com.android.tools.idea.layoutlib");
    components.addComponentWithPackages("android: naveditor", "com.android.tools.idea.naveditor");
    components.addComponentWithPackages("android: gradle", "com.android.tools.idea.gradle", "org.jetbrains.plugins.gradle");
    components.addComponentWithPackages("android: profiler", "com.android.tools.profiling",
                                        "com.android.tools.profilers",
                                        "com.android.tools.profiler",
                                        "com.android.tools.datastore",
                                        "com.android.tools.idea.profilers");
    components.addComponentWithPackages("android: templates", "com.android.tools.idea.templates");
    components.addComponentWithPackages("android: runDebug", "com.android.tools.idea.ddms",
                                        "com.android.ddmlib",
                                        "com.android.tools.idea.debug",
                                        "com.android.tools.idea.logcat",
                                        "com.android.tools.idea.apk.viewer",
                                        "com.android.tools.idea.run");
    components.addComponentWithPackages("android: inspections", "org.jetbrains.android.inspections");
    components.addComponentWithPackages("android: lint", "com.android.tools.lint",
                                        "com.android.tools.idea.lint",
                                        "com.android.tools.idea.common.lint");
    components.addComponentWithPackages("android: c++", "com.android.tools.ndk",
                                        "com.jetbrains.cidr");
    components.addComponentWithPackages("android: avdmanager", "com.android.tools.idea.avdmanager",
                                        "com.android.sdklib.internal.avd");
    components.addComponentWithPackages("android: layoutEditor",
                                        "com.android.tools.idea.common.surface",
                                        "com.android.tools.idea.common.scene",
                                        "com.android.tools.idea.common.editor",
                                        "com.android.tools.idea.uibuilder",
                                        "com.android.tools.idea.configurations");
    components.addComponentWithPackages("android: editors", "com.android.tools.idea.editors");
    components.addComponentWithPackages("android: diagnostics", "com.android.tools.idea.diagnostics", "com.android.tools.analytics");
    components.addComponentWithPackages("android: dataBinding", "com.android.tools.idea.databinding",
                                        "com.android.tools.idea.lang.databinding");
    components.addComponentWithPackages("intellij: kotlin", "org.jetbrains.kotlin");
    components.addComponentWithPackages("intellij: project", "com.intellij.openapi.project");
    components.addComponentWithPackages("intellij: application", "com.intellij.openapi.application");
    components.addComponentWithPackages("intellij: module", "com.intellij.workspaceModel.ide.impl.legacyBridge.module",
                                        "com.intellij.openapi.module");
    components.addComponentWithPackagesAndClassNames("intellij: vfs", ImmutableList.of("com.intellij.openapi.vfs"),
                                                     "com.intellij.workspaceModel.ide.impl.IdeVirtualFileUrlManagerImpl",
                                                     "com.intellij.workspaceModel.ide.impl.legacyBridge.watcher.VirtualFileUrlWatcher");
    components.addComponentWithPackages("intellij: editor", "com.intellij.openapi.editor", "com.intellij.openapi.fileEditor");
    components.addComponentWithPackages("android: sdk", "com.android.sdklib", "org.jetbrains.android.sdk");
    components.addComponentWithPackagesAndClassNames("ide: IdeEventQueue", Collections.emptyList(), "com.intellij.ide.IdeEventQueue");
    return components;
  }

  @NotNull
  public List<Component> getComponents() {
    return myComponents;
  }

  @NotNull
  private Component registerComponent(@NotNull final String componentName) {
    Component component = new Component(componentName, myComponents.size());
    myComponents.add(component);
    return component;
  }

  void addComponentWithPackages(@NotNull final String componentName,
                                String... packageNames) {
    Component newComponent = registerComponent(componentName);

    for (String name : packageNames) {
      myPackageNameToComponentIdCache.put(name, newComponent.myId);
    }
  }

  void addComponentWithPackagesAndClassNames(@NotNull final String componentName,
                                             @NotNull final List<String> packageNames,
                                             String... classNames) {
    Component newComponent = registerComponent(componentName);

    for (String name : classNames) {
      myClassNameToComponentId.put(name, newComponent.myId);
    }

    for (String name : packageNames) {
      myPackageNameToComponentIdCache.put(name, newComponent.myId);
    }
  }

  public int getComponentId(@NotNull final Object obj) {
    return (obj instanceof Class) ? getClassComponentId((Class<?>)obj) : getObjectComponentId(obj);
  }

  private int getObjectComponentId(@NotNull final Object obj) {
    return getClassComponentId(obj.getClass());
  }

  private int getClassComponentId(@NotNull final Class<?> aClass) {
    String objClassName = aClass.getName();

    if (myClassNameToComponentId.containsKey(objClassName)) {
      return myClassNameToComponentId.getInt(objClassName);
    }
    String packageName = aClass.getPackageName();

    int lastDot = packageName.length();
    String packageNamePrefix = packageName;
    do {
      packageNamePrefix = packageNamePrefix.substring(0, lastDot);
      if (myPackageNameToComponentIdCache.containsKey(packageNamePrefix)) {
        int ans = myPackageNameToComponentIdCache.getInt(packageNamePrefix);
        myPackageNameToComponentIdCache.put(packageName, ans);
        return ans;
      }
      lastDot = packageNamePrefix.lastIndexOf('.');
    }
    while (lastDot > 0);

    myPackageNameToComponentIdCache.put(packageName, COMPONENT_NOT_FOUND);
    return COMPONENT_NOT_FOUND;
  }

  public static final class Component {
    @NotNull
    private final String myComponentName;
    private final int myId;

    private Component(@NotNull final String componentName, int id) {
      myComponentName = componentName;
      myId = id;
    }

    @NotNull
    String getComponentName() {
      return myComponentName;
    }
  }
}
