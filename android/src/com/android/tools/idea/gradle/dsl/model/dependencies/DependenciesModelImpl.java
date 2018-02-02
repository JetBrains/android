/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.*;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class DependenciesModelImpl extends GradleDslBlockModel implements DependenciesModel {
  public DependenciesModelImpl(@NotNull DependenciesDslElement dslElement) {
    super(dslElement);
  }

  /**
   * @return all the dependencies (artifact, module, etc.)
   * WIP: Do not use.
   */
  @NotNull
  @Override
  public List<DependencyModel> all() {
    List<DependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslElement element : list.getElements(GradleDslElement.class)) {
          dependencies.addAll(ArtifactDependencyModelImpl.create(element));
          if (element instanceof GradleDslMethodCall) {
            GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
            dependencies.addAll(ModuleDependencyModelImpl.create(configurationName, methodCall));
          }
        }
      }
    }
    return dependencies;
  }

  @NotNull
  @Override
  public List<ArtifactDependencyModel> artifacts(@NotNull String configurationName) {
    List<ArtifactDependencyModel> dependencies = Lists.newArrayList();
    addArtifacts(configurationName, dependencies);
    return dependencies;
  }

  @NotNull
  @Override
  public List<ArtifactDependencyModel> artifacts() {
    List<ArtifactDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      addArtifacts(configurationName, dependencies);
    }
    return dependencies;
  }

  private void addArtifacts(@NotNull String configurationName, @NotNull List<ArtifactDependencyModel> dependencies) {
    GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
    if (list != null) {
      for (GradleDslElement element : list.getElements(GradleDslElement.class)) {
        dependencies.addAll(ArtifactDependencyModelImpl.create(element));
      }
    }
  }

  @Override
  public boolean containsArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency) {
    for (ArtifactDependencyModel artifactDependencyModel : artifacts(configurationName)) {
      if (ArtifactDependencySpecImpl.create(artifactDependencyModel).equals(dependency)) {
        return true;
      }
    }
    return false;
  }

  @NotNull
  @Override
  public DependenciesModel addArtifact(@NotNull String configurationName, @NotNull String compactNotation) {
    ArtifactDependencySpec dependency = ArtifactDependencySpecImpl.create(compactNotation);
    if (dependency == null) {
      String msg = String.format("'%1$s' is not a valid artifact dependency", compactNotation);
      throw new IllegalArgumentException(msg);
    }
    addArtifact(configurationName, dependency);
    return this;
  }

  @NotNull
  @Override
  public DependenciesModel addArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency) {
    return addArtifact(configurationName, dependency, Collections.emptyList());
  }

  @NotNull
  @Override
  public DependenciesModel addArtifact(@NotNull String configurationName,
                                       @NotNull ArtifactDependencySpec dependency,
                                       @NotNull List<ArtifactDependencySpec> excludes) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    ArtifactDependencyModelImpl.createAndAddToList(list, configurationName, dependency, excludes);
    return this;
  }

  @Override
  public boolean replaceArtifactByPsiElement(@NotNull PsiElement psiElement, @NotNull ArtifactDependencySpec dependency) {
    GradleDslElement element = findByPsiElement(psiElement);
    if (element == null) {
      return false;
    }

    performDependencyReplace(psiElement, element, dependency);
    return true;
  }

  @NotNull
  @Override
  public List<ModuleDependencyModel> modules() {
    List<ModuleDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
          dependencies.addAll(ModuleDependencyModelImpl.create(configurationName, element));
        }
      }
    }
    return dependencies;
  }


  @NotNull
  @Override
  public DependenciesModel addModule(@NotNull String configurationName, @NotNull String path) {
    return addModule(configurationName, path, null);
  }

  @NotNull
  @Override
  public DependenciesModel addModule(@NotNull String configurationName, @NotNull String path, @Nullable String config) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    ModuleDependencyModelImpl.createAndAddToList(list, configurationName, path, config);
    return this;
  }

  @NotNull
  @Override
  public List<FileTreeDependencyModel> fileTrees() {
    List<FileTreeDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
          dependencies.addAll(FileTreeDependencyModelImpl.create(configurationName, element));
        }
      }
    }
    return dependencies;
  }

  @Override
  @NotNull
  public DependenciesModel addFileTree(@NotNull String configurationName, @NotNull String dir) {
    return addFileTree(configurationName, dir, null, null);
  }

  @Override
  @NotNull
  public DependenciesModel addFileTree(@NotNull String configurationName,
                                       @NotNull String dir,
                                       @Nullable List<String> includes,
                                       @Nullable List<String> excludes) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    FileTreeDependencyModelImpl.createAndAddToList(list, configurationName, dir, includes, excludes);
    return this;
  }

  @NotNull
  @Override
  public List<FileDependencyModel> files() {
    List<FileDependencyModel> dependencies = Lists.newArrayList();
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
      if (list != null) {
        for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
          dependencies.addAll(FileDependencyModelImpl.create(configurationName, element));
        }
      }
    }
    return dependencies;
  }

  @Override
  @NotNull
  public DependenciesModel addFile(@NotNull String configurationName, @NotNull String file) {
    GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
    FileDependencyModelImpl.createAndAddToList(list, configurationName, file);
    return this;
  }

  @NotNull
  private GradleDslElementList getOrCreateGradleDslElementList(@NotNull String configurationName) {
    GradleDslElementList list = myDslElement.getPropertyElement(configurationName, GradleDslElementList.class);
    if (list == null) {
      GradleNameElement name = GradleNameElement.create(configurationName);
      list = new GradleDslElementList(myDslElement, name);
      myDslElement.setNewElement(configurationName, list);
    }
    return list;
  }

  @NotNull
  @Override
  public DependenciesModel remove(@NotNull DependencyModel dependency) {
    GradleDslElementList gradleDslElementList = myDslElement.getPropertyElement(dependency.configurationName(), GradleDslElementList.class);
    if (gradleDslElementList != null) {
      if (!(dependency instanceof DependencyModelImpl)) {
        Logger.getInstance(DependenciesModelImpl.class)
          .warn("Tried to remove an unknown dependency type!");
        return this;
      }
      GradleDslElement dependencyElement = ((DependencyModelImpl)dependency).getDslElement();
      GradleDslElement parent = dependencyElement.getParent();
      if (parent instanceof GradleDslMethodCall) {
        GradleDslMethodCall methodCall = (GradleDslMethodCall)parent;
        List<GradleDslElement> arguments = methodCall.getArguments();
        if (arguments.size() == 1 && arguments.get(0).equals(dependencyElement)) {
          // If this is the last argument, remove the method call altogether.
          gradleDslElementList.removeElement(methodCall);
        }
        else {
          methodCall.remove(dependencyElement);
        }
      }
      else {
        gradleDslElementList.removeElement(dependencyElement);
      }
    }
    return this;
  }

  private void performDependencyReplace(@NotNull PsiElement psiElement,
                                        @NotNull GradleDslElement element,
                                        @NotNull ArtifactDependencySpec dependency) {
    if (element instanceof GradleDslLiteral) {
      ((GradleDslLiteral)element).setValue(dependency.compactNotation());
    }
    else if (element instanceof GradleDslExpressionMap) {
      updateGradleExpressionMapWithDependency((GradleDslExpressionMap)element, dependency);
    }
    else if (element instanceof GradleDslMethodCall) {
      // There may be multiple arguments here, check find the one with correct PsiElement.
      GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
      for (GradleDslElement e : methodCall.getArguments()) {
        if (e.getPsiElement() == psiElement) {
          performDependencyReplace(psiElement, e, dependency);
        }
      }
    }
  }

  /**
   * Updates a {@link GradleDslExpressionMap} so that it represents the given {@link ArtifactDependencySpec}.
   */
  private void updateGradleExpressionMapWithDependency(@NotNull GradleDslExpressionMap map, @NotNull ArtifactDependencySpec dependency) {
    // We need to create a copy of the new map so that we can track the r
    Map<String, Function<ArtifactDependencySpec, String>> properties = new LinkedHashMap<>(ArtifactDependencySpecImpl.COMPONENT_MAP);
    // Update any existing properties.
    for (Map.Entry<String, GradleDslElement> entry : map.getPropertyElements().entrySet()) {
      if (properties.containsKey(entry.getKey())) {
        String value = properties.get(entry.getKey()).fun(dependency);
        if (value == null) {
          continue;
        }

        map.setNewLiteral(entry.getKey(), value);
        properties.remove(entry.getKey());
      }
      else {
        map.removeProperty(entry.getKey()); // Removes any unknown properties.
      }
    }
    // Add the remaining properties.
    for (Map.Entry<String, Function<ArtifactDependencySpec, String>> entry : properties.entrySet()) {
      String value = entry.getValue().fun(dependency);
      if (value != null) {
        map.addNewLiteral(entry.getKey(), value);
      }
      else {
        map.removeProperty(entry.getKey()); // Remove any properties that are null in the new dependency,
      }
    }
  }

  /**
   * Returns {@code true} if {@code child} is a descendant of the {@code parent}, {@code false} otherwise.
   */
  private static boolean isChildOfParent(@NotNull PsiElement child, @NotNull PsiElement parent) {
    List<PsiElement> childElements = Lists.newArrayList(parent);
    while (!childElements.isEmpty()) {
      PsiElement element = childElements.remove(0);
      if (element.equals(child)) {
        return true;
      }
      childElements.addAll(Arrays.asList(element.getChildren()));
    }
    return false;
  }

  /**
   * Finds a {@link GradleDslElement} corresponding to an artifact which is represented by the given {@link PsiElement}. This method will
   * split up
   */
  @Nullable
  private GradleDslElement findByPsiElement(@NotNull PsiElement child) {
    for (String configurationName : myDslElement.getProperties()) {
      GradleDslElementList list = getOrCreateGradleDslElementList(configurationName);
      for (GradleDslElement element : list.getElements()) {
        // For method calls we need to check each of the arguments individually.
        if (element instanceof GradleDslMethodCall) {
          GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
          for (GradleDslElement el : methodCall.getArguments()) {
            if (el.getPsiElement() != null && isChildOfParent(child, el.getPsiElement())) {
              return el;
            }
          }
        }
        else {
          if (element.getPsiElement() != null && isChildOfParent(child, element.getPsiElement())) {
            return element;
          }
        }
      }
    }
    return null;
  }
}
