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
import com.android.ide.common.resources.ResourceRepository;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * A project-wide registry for class lookup of resource classes (R classes).
 */
public class ResourceClassRegistry implements ProjectComponent {
  private final Map<ResourceRepository, ResourceClassGenerator> myGeneratorMap = new HashMap<>();
  private Set<String> myPackages;

  /**
   * Adds definition of a new R class to the registry. The R class will contain resources from the given repo in the given namespace and
   * will be generated when the {@link #findClassDefinition} is called with a class name that matches the {@code aarPackageName} and
   * the {@code repo} resource repository can be found in the {@link ResourceRepositoryManager} passed to {@link #findClassDefinition}.
   *
   * <p>Note that the {@link ResourceClassRegistry} is a project-level component, so the same R class may be generated in different ways
   * depending on the repository used. In non-namespaced project, the repository is the full {@link AppResourceRepository} of the module
   * in question. In namespaced projects the repository is a {@link AarSourceResourceRepository} of just the AAR contents.
   */
  public void addLibrary(@NotNull ResourceRepository repo,
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
  public byte[] findClassDefinition(@NotNull String className, @NotNull ResourceRepositoryManager repositoryManager) {
    int index = className.lastIndexOf('.');
    if (index > 1 && className.charAt(index + 1) == 'R' && (index == className.length() - 2 || className.charAt(index + 2) == '$')) {
      // If this is an R class or one of its inner classes.
      String pkg = className.substring(0, index);
      if (myPackages != null && myPackages.contains(pkg)) {
        ResourceNamespace namespace = ResourceNamespace.fromPackageName(pkg);
        List<LocalResourceRepository> repositories = repositoryManager.getAppResourcesForNamespace(namespace);
        ResourceClassGenerator generator = findClassGenerator(repositories, className);
        if (generator != null) {
          return generator.generate(className);
        }
      }
    }
    return null;
  }

  @Nullable
  private ResourceClassGenerator findClassGenerator(@NotNull List<LocalResourceRepository> repositories, @NotNull String className) {
    ResourceClassGenerator foundGenerator = null;
    for (int i = 0; i < repositories.size(); i++) {
      ResourceClassGenerator generator = myGeneratorMap.get(repositories.get(i));
      if (generator != null) {
        if (foundGenerator == null) {
          foundGenerator = generator;
        }
        else {
          // There is a package name collision between libraries. Throw NoClassDefFoundError exception.
          throw new NoClassDefFoundError(className + " class could not be loaded because of package name collision between libraries");
        }
      }
    }
    return foundGenerator;
  }

  /**
   * Ideally, this method would not exist. But there are potential bugs in the caching mechanism. So, the method should be called when
   * rendering fails due to hard to explain causes: like NoSuchFieldError.
   *
   * @see ResourceIdManager#resetDynamicIds()
   */
  public void clearCache() {
    myGeneratorMap.clear();
  }

  /**
   * Lazily instantiates a registry with the target project.
   */
  @NotNull
  public static ResourceClassRegistry get(@NotNull Project project) {
    return project.getComponent(ResourceClassRegistry.class);
  }

  // ProjectComponent method.
  @Override
  @NotNull
  public String getComponentName() {
    return ResourceClassRegistry.class.getName();
  }

  @VisibleForTesting
  @NotNull
  Collection<String> getPackages() {
    return myPackages == null ? Collections.emptySet() : myPackages;
  }

  @VisibleForTesting
  @NotNull
  Map<ResourceRepository, ResourceClassGenerator> getGeneratorMap() {
    return myGeneratorMap;
  }
}
