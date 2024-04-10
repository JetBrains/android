/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.followElement;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.FileTreeDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ReferenceTo;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslClosure;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public abstract class AbstractDependenciesModel extends GradleDslBlockModel implements DependenciesModel {

  public static final Logger LOG = Logger.getInstance(AbstractDependenciesModel.class);

  public AbstractDependenciesModel(@NotNull DependenciesDslElement dslElement) {
    super(dslElement);
  }


  /**
   * A strategy object to find and get specific DependencyModel objects from a GradleDslElement.
   */
  public interface Fetcher<T extends DependencyModel> {

    void fetch(@NotNull String configurationName,
               @NotNull GradleDslElement element,
               @NotNull GradleDslElement resolved,
               @Nullable GradleDslClosure configurationElement,
               @NotNull DependencyModelImpl.Maintainer maintainer,
               @NotNull List<? super T> dest);
  }

  public interface DependencyReplacer{
    void performDependencyReplace(@NotNull PsiElement psiElement,
                                  @NotNull GradleDslElement element,
                                  @NotNull ArtifactDependencySpec dependency);
  }

  // Map to allow iteration over each element in the ArtifactDependencySpec
  private static final Map<String, Function<ArtifactDependencySpec, String>> COMPONENT_MAP =
    ImmutableMap.<String, Function<ArtifactDependencySpec, String>>builder()
      .put("name", ArtifactDependencySpec::getName)
      .put("group", ArtifactDependencySpec::getGroup)
      .put("version", ArtifactDependencySpec::getVersion)
      .put("ext", ArtifactDependencySpec::getExtension)
      .put("classifier", ArtifactDependencySpec::getClassifier)
      .build();

  /**
   * @return all the dependencies (artifact, module, etc.)
   * WIP: Do not use:)
   */
  @NotNull
  @Override
  public List<DependencyModel> all() {
    return all(null, getAllFetcher());
  }

  @NotNull
  @Override
  public List<ArtifactDependencyModel> artifacts(@NotNull String configurationName) {
    return all(configurationName, getArtifactFetcher());
  }

  protected abstract Fetcher<ArtifactDependencyModel> getArtifactFetcher();
  protected abstract Fetcher<ModuleDependencyModel> getModuleFetcher();
  protected abstract Fetcher<FileTreeDependencyModel> getFileTreeFetcher();
  protected abstract Fetcher<FileDependencyModel> getFileFetcher();

  protected Fetcher<DependencyModel> getAllFetcher() {
   return (configurationName, element, resolved, configurationElement, maintainer, dest) -> {
     getArtifactFetcher().fetch(configurationName, element, resolved, configurationElement, maintainer, dest);
     getModuleFetcher().fetch(configurationName, element, resolved, configurationElement, maintainer, dest);
     getFileFetcher().fetch(configurationName, element, resolved, configurationElement, maintainer, dest);
     getFileTreeFetcher().fetch(configurationName, element, resolved, configurationElement, maintainer, dest);
   };
  }

  @NotNull
  @Override
  public List<ArtifactDependencyModel> artifacts() {
    return all(null, getArtifactFetcher());
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

  @Override
  public void addArtifact(@NotNull String configurationName, @NotNull String compactNotation) {
    addArtifact(configurationName, compactNotation, Collections.emptyList());
  }

  @Override
  public void addArtifact(@NotNull String configurationName,
                          @NotNull String compactNotation,
                          @NotNull List<ArtifactDependencySpec> excludes) {
    ArtifactDependencySpec dependency = ArtifactDependencySpecImpl.create(compactNotation);
    if (dependency == null) {
      String msg = String.format("'%1$s' is not a valid artifact dependency", compactNotation);
      LOG.warn(msg);
      return;
    }
    addArtifact(configurationName, dependency, excludes);
  }

  @Override
  public void addArtifact(@NotNull String configurationName, @NotNull ReferenceTo reference) {
    ArtifactDependencyModelImpl.createNew(myDslElement, configurationName, reference, Collections.emptyList());
  }

  @Override
  public void addArtifact(@NotNull String configurationName, @NotNull ReferenceTo reference, @NotNull List<ArtifactDependencySpec> excludes) {
     ArtifactDependencyModelImpl.createNew(myDslElement, configurationName, reference, excludes);
  }

  @Override
  public void addArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency) {
    addArtifact(configurationName, dependency, Collections.emptyList());
  }

  @Override
  public void addArtifact(@NotNull String configurationName,
                          @NotNull ArtifactDependencySpec dependency,
                          @NotNull List<ArtifactDependencySpec> excludes) {
    ArtifactDependencyModelImpl.createNew(myDslElement, configurationName, dependency, excludes);
  }

  @Override
  public void addPlatformArtifact(@NotNull String configurationName, @NotNull String compactNotation, boolean enforced) {
    ArtifactDependencySpec dependency = ArtifactDependencySpecImpl.create(compactNotation);
    if (dependency == null) {
      String msg = String.format("'%1$s' is not a valid artifact dependency", compactNotation);
      LOG.warn(msg);
      return;
    }
    addPlatformArtifact(configurationName, dependency, enforced);
  }

  @Override
  public void addPlatformArtifact(@NotNull String configurationName, @NotNull ReferenceTo reference, boolean enforced) {
    PlatformArtifactDependencyModelImpl.createNew(myDslElement, configurationName, reference, enforced);
  }

  @Override
  public void addPlatformArtifact(@NotNull String configurationName, @NotNull ArtifactDependencySpec dependency, boolean enforced) {
    PlatformArtifactDependencyModelImpl.createNew(myDslElement, configurationName, dependency, enforced);
  }

  @Override
  public boolean replaceArtifactByPsiElement(@NotNull PsiElement psiElement, @NotNull ArtifactDependencySpec dependency) {
    GradleDslElement element = findByPsiElement(psiElement);
    if (element == null) {
      return false;
    }

    getDependencyReplacer().performDependencyReplace(psiElement, element, dependency);
    return true;
  }

  /**
   * Finds a {@link GradleDslElement} corresponding to an artifact which is represented by the given {@link PsiElement}. This method will
   * split up
   */
  @Nullable
  protected abstract GradleDslElement findByPsiElement(@NotNull PsiElement child);

  protected abstract DependencyReplacer getDependencyReplacer();

  @NotNull
  @Override
  public List<ModuleDependencyModel> modules() {
    return all(null, getModuleFetcher());
  }


  @Override
  public void addModule(@NotNull String configurationName, @NotNull String path) {
    addModule(configurationName, path, null);
  }

  @NotNull
  @Override
  @TestOnly
  public List<FileTreeDependencyModel> fileTrees() {
    return all(null, getFileTreeFetcher());
  }

  @Override
  public void addFileTree(@NotNull String configurationName, @NotNull String dir) {
    addFileTree(configurationName, dir, null, null);
  }

  @Override
  public void addFileTree(@NotNull String configurationName,
                          @NotNull String dir,
                          @Nullable List<String> includes,
                          @Nullable List<String> excludes) {
    FileTreeDependencyModelImpl.createNew(myDslElement, configurationName, dir, includes, excludes);
  }

  @NotNull
  @Override
  @TestOnly
  public List<FileDependencyModel> files() {
    return all(null, getFileFetcher());
  }

  @Override
  public void addFile(@NotNull String configurationName, @NotNull String file) {
    FileDependencyModelImpl.createNew(myDslElement, configurationName, file);
  }

  @Override
  public void remove(@NotNull com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel dependency) {
    if (!(dependency instanceof DependencyModelImpl)) {
      LOG.warn("Tried to remove an unknown dependency type: " + dependency);
      return;
    }
    GradleDslElement dependencyElement = ((DependencyModelImpl)dependency).getDslElement();
    GradleDslElement parent = dependencyElement.getParent();
    if (parent instanceof GradleDslMethodCall) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)parent;
      List<GradleDslExpression> arguments = methodCall.getArguments();
      if (arguments.size() == 1 && arguments.get(0).equals(dependencyElement)) {
        // If this is the last argument, remove the method call altogether.
        myDslElement.removeProperty(methodCall);
      }
      else {
        methodCall.remove(dependencyElement);
      }
    }
    else if (parent instanceof GradleDslExpressionList) {
      List<GradleDslExpression> expressions = ((GradleDslExpressionList)parent).getExpressions();
      if (expressions.size() == 1 && expressions.get(0).equals(dependencyElement)) {
        if (parent.getParent() instanceof GradleDslMethodCall) {
          // We need to delete up two levels if this is a method call.
          myDslElement.removeProperty(parent.getParent());
        }
        else {
          myDslElement.removeProperty(parent);
        }
      }
      else {
        ((GradleDslExpressionList)parent).removeElement(dependencyElement);
      }
    }
    else {
      myDslElement.removeProperty(dependencyElement);
    }
  }

  @NotNull
  private <T extends DependencyModel> List<T> all(@Nullable String configurationName, @NotNull Fetcher<T> fetcher) {
    List<T> dependencies = new ArrayList<>();
    for (GradleDslElement element : configurationName != null
                                    ? myDslElement.getPropertyElementsByName(configurationName)
                                    : myDslElement.getAllPropertyElements()) {
      collectFrom(element.getName(), element, fetcher, dependencies);
    }
    return dependencies;
  }

  protected abstract <T extends DependencyModel> void collectFrom(@NotNull String configurationName,
                                                       @NotNull GradleDslElement element,
                                                       @NotNull Fetcher<T> byFetcher,
                                                       @NotNull List<T> dest);

  @NotNull
  protected static GradleDslElement resolveElement(@NotNull GradleDslElement element) {
    GradleDslElement resolved = element;
    GradleDslElement foundElement = followElement(element);
    if (foundElement instanceof GradleDslExpression) {
      resolved = foundElement;
    }
    return resolved;
  }

  /**
   * Returns {@code true} if {@code child} is a descendant of the {@code parent}, {@code false} otherwise.
   */
  protected static boolean isChildOfParent(@NotNull PsiElement child, @NotNull PsiElement parent) {
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
   * Updates a {@link GradleDslExpressionMap} so that it represents the given {@link ArtifactDependencySpec}.
   */
  protected static void updateGradleExpressionMapWithDependency(@NotNull GradleDslExpressionMap map,
                                                                @NotNull ArtifactDependencySpec dependency) {
    // We need to create a copy of the new map so that we can track the r
    Map<String, Function<ArtifactDependencySpec, String>> properties = new LinkedHashMap<>(COMPONENT_MAP);
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

}
