/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.resources;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.tools.idea.projectsystem.LightResourceClassService;
import com.android.tools.idea.res.AndroidLightPackage;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.idea.blaze.base.sync.data.BlazeDataStorage;
import com.google.idea.common.experiments.BoolExperiment;
import com.google.idea.common.experiments.FeatureRolloutExperiment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.Nullable;

/** Implementation of {@link LightResourceClassService} set up at Blaze sync time. */
public class BlazeLightResourceClassService extends BlazeLightResourceClassServiceBase
    implements LightResourceClassService {

  @VisibleForTesting
  public static final FeatureRolloutExperiment workspaceResourcesFeature =
      new FeatureRolloutExperiment("aswb.workspace.light.class.enabled");

  // It should be harmless to create stub resource PsiPackages which shadow any "real" PsiPackages.
  // Based on the ordering of PsiElementFinder it would prefer the real package
  // (PsiElementFinderImpl has 'order="first"').
  // Put under experiment just in case we find a problem w/ other element finders.
  private static final BoolExperiment createStubResourcePackages =
      new BoolExperiment("create.stub.resource.packages", true);

  private final Project project;

  private Map<String, PsiPackage> rClassPackages = Maps.newHashMap();
  private Map<String, BlazeRClass> workspaceRClasses = Maps.newHashMap();
  private Set<String> workspaceRClassNames = ImmutableSet.of();
  private Set<String> workspaceResourcePackages = ImmutableSet.of();

  private PsiManager psiManager;

  public static BlazeLightResourceClassService getInstance(Project project) {
    return project.getService(BlazeLightResourceClassService.class);
  }

  private BlazeLightResourceClassService(Project project) {
    this.project = project;
  }

  /** Builds light R classes */
  public static class Builder {
    Map<String, BlazeRClass> rClassMap = Maps.newHashMap();
    Map<Module, BlazeRClass> rClassByModuleMap = Maps.newHashMap();
    Map<String, PsiPackage> rClassPackages = Maps.newHashMap();
    Set<String> workspaceRClassNames = ImmutableSet.of();
    Set<String> workspaceResourcePackages = ImmutableSet.of();

    PsiManager psiManager;

    public Builder(Project project) {
      this.psiManager = PsiManager.getInstance(project);
    }

    public void addRClass(String resourceJavaPackage, Module module) {
      AndroidFacet androidFacet = AndroidFacet.getInstance(module);
      if (androidFacet == null) {
        return; // Do not register R class if android facet is not present.
      }
      BlazeRClass rClass = new BlazeRClass(psiManager, androidFacet, resourceJavaPackage);
      rClassMap.put(getQualifiedRClassName(resourceJavaPackage), rClass);
      rClassByModuleMap.put(module, rClass);
      if (createStubResourcePackages.getValue()) {
        addStubPackages(resourceJavaPackage);
      }
    }

    public void addWorkspacePackages(Set<String> resourceJavaPackages) {
      if (!workspaceResourcesFeature.isEnabled()) {
        return;
      }
      this.workspaceResourcePackages = resourceJavaPackages;
      this.workspaceRClassNames =
          resourceJavaPackages.stream()
              .map(Builder::getQualifiedRClassName)
              .collect(toImmutableSet());
      resourceJavaPackages.forEach(this::addStubPackages);
    }

    private static String getQualifiedRClassName(String packageName) {
      return packageName + ".R";
    }

    private void addStubPackages(String resourceJavaPackage) {
      while (!resourceJavaPackage.isEmpty()) {
        if (rClassPackages.containsKey(resourceJavaPackage)) {
          return;
        }
        rClassPackages.put(
            resourceJavaPackage,
            AndroidLightPackage.withName(resourceJavaPackage, psiManager.getProject()));
        int nextIndex = resourceJavaPackage.lastIndexOf('.');
        if (nextIndex < 0) {
          return;
        }
        resourceJavaPackage = resourceJavaPackage.substring(0, nextIndex);
      }
    }
  }

  public Set<String> getWorkspaceResourcePackages() {
    return workspaceResourcePackages;
  }

  public void installRClasses(Builder builder) {
    this.rClasses = builder.rClassMap;
    this.rClassesByModule = builder.rClassByModuleMap;
    this.rClassPackages = builder.rClassPackages;

    this.workspaceResourcePackages = builder.workspaceResourcePackages;
    this.workspaceRClasses = new HashMap<>();
    this.workspaceRClassNames = ImmutableSet.copyOf(builder.workspaceRClassNames);
    this.psiManager = builder.psiManager;

    this.allRClasses.clear();
    this.allRClasses.addAll(rClasses.values());
  }

  @Override
  public Collection<? extends PsiClass> getLightRClasses(
      String qualifiedName, GlobalSearchScope scope) {
    BlazeRClass rClass = this.rClasses.get(qualifiedName);

    if (rClass == null) {
      rClass = getRClassForWorkspace(qualifiedName, scope);
    }

    if (rClass != null && scope.isSearchInModuleContent(rClass.getModule())) {
      return ImmutableList.of(rClass);
    }

    return ImmutableList.of();
  }

  @Nullable
  private BlazeRClass getRClassForWorkspace(String qualifiedName, GlobalSearchScope scope) {
    if (!workspaceResourcesFeature.isEnabled() || !workspaceRClassNames.contains(qualifiedName)) {
      return null;
    }

    BlazeRClass rClass = workspaceRClasses.get(qualifiedName);
    if (rClass != null) {
      if (scope.isSearchInModuleContent(rClass.getModule())) {
        return rClass;
      } else {
        return null;
      }
    }

    Module workspaceModule =
        ModuleManager.getInstance(project).findModuleByName(BlazeDataStorage.WORKSPACE_MODULE_NAME);
    if (workspaceModule == null || !scope.isSearchInModuleContent(workspaceModule)) {
      return null;
    }

    // Remove the .R suffix
    String packageName = qualifiedName.substring(0, qualifiedName.length() - 2);
    AndroidFacet workspaceFacet = AndroidFacet.getInstance(workspaceModule);
    if (workspaceFacet == null) {
      return null;
    }

    rClass = new BlazeRClass(psiManager, workspaceFacet, packageName);
    workspaceRClasses.put(qualifiedName, rClass);
    allRClasses.add(rClass);
    return rClass;
  }

  @Override
  public Collection<? extends PsiClass> getLightRClassesContainingModuleResources(Module module) {
    return rClasses.values();
  }

  @Override
  @Nullable
  public PsiPackage findRClassPackage(String qualifiedName) {
    return rClassPackages.get(qualifiedName);
  }

  @Override
  public Collection<? extends PsiClass> getAllLightRClasses() {
    return allRClasses;
  }
}
