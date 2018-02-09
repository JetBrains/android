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
package com.android.tools.idea.res;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.google.common.collect.Maps;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * A project-wide registry for class lookup of resource classes (R classes).
 */
public class ResourceClassRegistry implements ProjectComponent {

  private final Map<AbstractResourceRepository, ResourceClassGenerator> myGeneratorMap = Maps.newHashMap();
  private Collection<String> myPackages;

  /**
   * Adds definition of a new R class to the registry. The R class will contain resources from the given repo in the given namespace and
   * will be generated if the same repository is passed to {@link #findClassDefinition(String, AbstractResourceRepository)} together with
   * a class name that matches the {@code aarPackageName}.
   *
   * <p>Note that the {@link ResourceClassRegistry} is a project-level component, so the same R class may be generated in different ways
   * depending on the repository used. In non-namespaced project, the repository is the full {@link AppResourceRepository} of the module
   * in question. In namespaced projects the repository is the {@link FileResourceRepository} of just the AAR contents.
   */
  public void addLibrary(@NotNull AbstractResourceRepository repo,
                         @NotNull ResourceIdManager idManager,
                         @Nullable String aarPackageName,
                         @NotNull ResourceNamespace namespace) {
    if (StringUtil.isNotEmpty(aarPackageName)) {
      if (myPackages == null) {
        myPackages = new HashSet<>();
      }
      myPackages.add(aarPackageName);
      if (!myGeneratorMap.containsKey(repo)) {
        ResourceClassGenerator generator = ResourceClassGenerator.create(idManager, repo, namespace);
        myGeneratorMap.put(repo, generator);
      }
    }
  }

  /** Looks up a class definition for the given name, if possible */
  @Nullable
  public byte[] findClassDefinition(@NotNull String name, @NotNull AbstractResourceRepository repo) {
    int index = name.lastIndexOf('.');
    if (index != -1 && name.charAt(index + 1) == 'R' && (index == name.length() - 2 || name.charAt(index + 2) == '$') && index > 1) {
      // If this is an R class or one of its inner classes.
      String pkg = name.substring(0, index);
      if (myPackages != null && myPackages.contains(pkg)) {
        ResourceClassGenerator generator = myGeneratorMap.get(repo);
        if (generator != null) {
          return generator.generate(name);
        }
      }
    }
    return null;
  }

  /**
   * Ideally, this method will not exist. But there are potential bugs in the caching mechanism. So, the method should be called when
   * rendering fails due to hard to explain causes: like NoSuchFieldError.
   *
   * @see ResourceIdManager#resetDynamicIds()
   */
  public void clearCache() {
    myGeneratorMap.clear();
  }

  /**
   * Lazily instantiate a registry with the target project.
   */
  public static ResourceClassRegistry get(@NotNull Project project) {
    return project.getComponent(ResourceClassRegistry.class);
  }

  // ProjectComponent methods.

  @NotNull
  @Override
  public String getComponentName() {
    return ResourceClassRegistry.class.getName();
  }

  @VisibleForTesting
  Collection<String> getPackages() {
    return myPackages;
  }

  @VisibleForTesting
  Map<AbstractResourceRepository, ResourceClassGenerator> getGeneratorMap() {
    return myGeneratorMap;
  }
}
