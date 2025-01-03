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

import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.MapMethodTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgToMapTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.utils.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.google.common.base.Splitter.on;

public class ScriptModuleDependencyModelImpl extends DependencyModelImpl implements ModuleDependencyModel {
  @NonNls public static final String PROJECT = "project";
  @NonNls private static final String PATH = "path";
  @NonNls private static final String CONFIGURATION = "configuration";

  @NotNull private GradleDslMethodCall myDslElement;

  @Nullable
  static ModuleDependencyModel create(@NotNull String configurationName,
                                      @NotNull GradleDslMethodCall methodCall,
                                      @NotNull Maintainer maintainer,
                                      @Nullable String platformMethodName) {
    if (PROJECT.equals(methodCall.getMethodName())) {
      if (platformMethodName != null) {
        return new PlatformModuleDependencyModelImpl(configurationName, methodCall, maintainer, platformMethodName);
      }
      else {
        return new ScriptModuleDependencyModelImpl(configurationName, methodCall, maintainer);
      }
    }
    return null;
  }

  @NotNull
  static ModuleDependencyModel createNew(@NotNull GradlePropertiesDslElement parent,
                                         @NotNull String configurationName,
                                         @NotNull String path,
                                         @Nullable String config) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    GradleDslMethodCall methodCall = new GradleDslMethodCall(parent, name, PROJECT);
    if (config == null) {
      GradleDslLiteral pathLiteral = new GradleDslLiteral(methodCall.getArgumentsElement(), GradleNameElement.empty());
      pathLiteral.setValue(path);
      methodCall.addNewArgument(pathLiteral);
    }
    else {
      GradleDslExpressionMap mapArguments = new GradleDslExpressionMap(methodCall, GradleNameElement.empty());
      mapArguments.setNewLiteral(PATH, path);
      mapArguments.setNewLiteral(CONFIGURATION, config);
      methodCall.addNewArgument(mapArguments);
    }
    parent.setNewElement(methodCall);
    return new ScriptModuleDependencyModelImpl(configurationName, methodCall, ScriptDependenciesModelImpl.Maintainers.SINGLE_ITEM_MAINTAINER);
  }

  ScriptModuleDependencyModelImpl(@NotNull String configurationName,
                                  @NotNull GradleDslMethodCall dslElement,
                                  @NotNull Maintainer maintainer) {
    super(configurationName, maintainer);
    myDslElement = dslElement;
  }

  @Override
  @NotNull
  protected GradleDslMethodCall getDslElement() {
    return myDslElement;
  }

  @Override
  void setDslElement(@NotNull GradleDslElement dslElement) {
    myDslElement = (GradleDslMethodCall)dslElement;
  }

  @Override
  @NotNull
  public String name() {
    List<String> pathSegments = on(GRADLE_PATH_SEPARATOR).omitEmptyStrings().splitToList(path().forceString());
    int segmentCount = pathSegments.size();
    return segmentCount > 0 ? pathSegments.get(segmentCount - 1) : "";
  }

  @Override
  @NotNull
  public ResolvedPropertyModel path() {
    return GradlePropertyModelBuilder.create(myDslElement).addTransform(new MapMethodTransform(PROJECT, PATH))
                                     .addTransform(new SingleArgumentMethodTransform(PROJECT)).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel configuration() {
    return GradlePropertyModelBuilder.create(myDslElement).addTransform(new SingleArgToMapTransform(PATH, CONFIGURATION))
                                     .addTransform(new MapMethodTransform(PROJECT, CONFIGURATION)).buildResolved();
  }
}
