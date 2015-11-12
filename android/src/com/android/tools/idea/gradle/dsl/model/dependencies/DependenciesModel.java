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

import com.android.tools.idea.gradle.dsl.dependencies.ExternalDependencySpec;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel.CONFIGURATION;
import static com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel.PATH;
import static com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel.PROJECT;

public class DependenciesModel {
  @NotNull private DependenciesDslElement myDslElement;

  public DependenciesModel(@NotNull DependenciesDslElement dslElement) {
    myDslElement = dslElement;
  }

  @NotNull
  public List<ArtifactDependencyModel> artifactDependencies(@NotNull String configurationName) {
    List<ArtifactDependencyModel> dependencies = Lists.newArrayList();
    GradleDslElementList list = myDslElement.getProperty(configurationName, GradleDslElementList.class);
    if (list != null) {
      for (GradleDslElement element : list.getElements(GradleDslElement.class)) {
        dependencies.addAll(ArtifactDependencyModel.create(element));
      }
    }
    return dependencies;
  }

  @NotNull
  public DependenciesModel addArtifactDependency(@NotNull String configurationName, @NotNull String compactNotation) {
    ExternalDependencySpec spec = ExternalDependencySpec.create(compactNotation);
    if (spec == null) {
      throw new IllegalArgumentException("'" + compactNotation + "' is not a valid dependency specification");
    }
    GradleDslElementList list = myDslElement.getProperty(configurationName, GradleDslElementList.class);
    if (list == null) {
      list = new GradleDslElementList(myDslElement, configurationName);
      myDslElement.setNewElement(configurationName, list);
    }
    GradleDslLiteral literal = new GradleDslLiteral(list, configurationName);
    literal.setValue(compactNotation);
    list.addNewElement(literal);
    return this;
  }

  @NotNull
  public List<ModuleDependencyModel> moduleDependencies(@NotNull String configurationName) {
    List<ModuleDependencyModel> dependencies = Lists.newArrayList();
    GradleDslElementList list = myDslElement.getProperty(configurationName, GradleDslElementList.class);
    if (list != null) {
      for (GradleDslMethodCall element : list.getElements(GradleDslMethodCall.class)) {
        dependencies.addAll(ModuleDependencyModel.create(configurationName, element));
      }
    }
    return dependencies;
  }

  @NotNull
  public DependenciesModel addModuleDependency(@NotNull String configurationName, @NotNull String path, @Nullable String config) {
    GradleDslElementList list = myDslElement.getProperty(configurationName, GradleDslElementList.class);
    if (list == null) {
      list = new GradleDslElementList(myDslElement, configurationName);
      myDslElement.setNewElement(configurationName, list);
    }
    GradleDslMethodCall methodCall = new GradleDslMethodCall(list, PROJECT, configurationName);
    GradleDslExpressionMap mapArguments = new GradleDslExpressionMap(methodCall, PROJECT);
    mapArguments.setNewLiteral(PATH, path);
    if (config != null) {
      mapArguments.setNewLiteral(CONFIGURATION, config);
    }
    methodCall.addNewArgument(mapArguments);
    list.addNewElement(methodCall);
    return this;
  }

  public DependenciesModel remove(@NotNull DependencyModel dependency) {
    GradleDslElementList gradleDslElementList = myDslElement.getProperty(dependency.getConfigurationName(), GradleDslElementList.class);
    if (gradleDslElementList != null) {
      GradleDslElement dependencyElement = dependency.getDslElement();
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
}
