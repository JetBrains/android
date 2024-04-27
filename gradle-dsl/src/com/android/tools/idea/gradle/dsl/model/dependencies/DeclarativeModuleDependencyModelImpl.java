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

import static com.android.tools.idea.gradle.dsl.utils.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.google.common.base.Splitter.on;

import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeclarativeModuleDependencyModelImpl extends DependencyModelImpl implements ModuleDependencyModel {
  @NonNls public static final String PROJECT = "project";
  @NonNls private static final String CONFIGURATION = "configuration";

  @NotNull private GradleDslExpressionMap myDslElement;


  @NotNull
  static DeclarativeModuleDependencyModelImpl createNew(@NotNull GradlePropertiesDslElement parent,
                                         @NotNull String configurationName,
                                         @NotNull String path,
                                         @Nullable String config) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    GradleDslExpressionMap map = new GradleDslExpressionMap(parent, name, false);
    map.setNewLiteral(PROJECT, path);
    map.setNewLiteral(CONFIGURATION, config);
    parent.setNewElement(map);
    return new DeclarativeModuleDependencyModelImpl(configurationName, map, ScriptDependenciesModelImpl.Maintainers.SINGLE_ITEM_MAINTAINER);
  }

  DeclarativeModuleDependencyModelImpl(@NotNull String configurationName,
                                       @NotNull GradleDslExpressionMap dslElement,
                                       @NotNull Maintainer maintainer) {
    super(configurationName, maintainer);
    myDslElement = dslElement;
  }

  @Override
  @NotNull
  protected GradleDslExpressionMap getDslElement() {
    return myDslElement;
  }

  @Override
  void setDslElement(@NotNull GradleDslElement dslElement) {
    myDslElement = (GradleDslExpressionMap)dslElement;
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
    return GradlePropertyModelBuilder.create(myDslElement, PROJECT).buildResolved();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel configuration() {
    return GradlePropertyModelBuilder.create(myDslElement, CONFIGURATION).buildResolved();
  }
}
