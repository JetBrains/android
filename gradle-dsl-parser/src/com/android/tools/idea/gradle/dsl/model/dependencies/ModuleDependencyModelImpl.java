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
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.android.tools.idea.gradle.util.GradleUtil.getPathSegments;

public class ModuleDependencyModelImpl extends DependencyModelImpl implements
                                                                   ModuleDependencyModel {
  private static final Logger LOG = Logger.getInstance(ModuleDependencyModelImpl.class);

  @NonNls private static final String PROJECT = "project";
  @NonNls private static final String PATH = "path";
  @NonNls private static final String CONFIGURATION = "configuration";

  @NotNull private String myConfigurationName;
  @NotNull private GradleDslMethodCall myDslElement;
  @NotNull private GradleDslExpression myPath;

  @Nullable private GradleDslExpression myConfiguration;

  @NotNull
  static List<ModuleDependencyModel> create(@NotNull String configurationName, @NotNull GradleDslMethodCall methodCall) {
    List<ModuleDependencyModel> result = Lists.newArrayList();
    if (PROJECT.equals(methodCall.getName())) {
      for (GradleDslElement argument : methodCall.getArguments()) {
        if (argument instanceof GradleDslExpression) {
          result.add(new ModuleDependencyModelImpl(configurationName, methodCall, (GradleDslExpression)argument, null));
        }
        else if (argument instanceof GradleDslExpressionMap) {
          GradleDslExpressionMap dslMap = (GradleDslExpressionMap)argument;
          GradleDslExpression pathElement = dslMap.getPropertyElement(PATH, GradleDslExpression.class);
          if (pathElement == null) {
            assert methodCall.getPsiElement() != null;
            String msg = String.format("'%1$s' is not a valid module dependency", methodCall.getPsiElement().getText());
            LOG.warn(msg);
            continue;
          }
          GradleDslExpression configuration = dslMap.getPropertyElement(CONFIGURATION, GradleDslExpression.class);
          result.add(new ModuleDependencyModelImpl(configurationName, methodCall, pathElement, configuration));
        }
      }
    }
    return result;
  }

  static void createAndAddToList(@NotNull GradleDslElementList list,
                                 @NotNull String configurationName,
                                 @NotNull String path,
                                 @Nullable String config) {
    String methodName = PROJECT;
    GradleDslMethodCall methodCall = new GradleDslMethodCall(list, methodName, configurationName);
    GradleDslExpressionMap mapArguments = new GradleDslExpressionMap(methodCall, methodName);
    mapArguments.setNewLiteral(PATH, path);
    if (config != null) {
      mapArguments.setNewLiteral(CONFIGURATION, config);
    }
    methodCall.addNewArgument(mapArguments);
    list.addNewElement(methodCall);
  }

  private ModuleDependencyModelImpl(@NotNull String configurationName,
                                    @NotNull GradleDslMethodCall dslElement,
                                    @NotNull GradleDslExpression path,
                                    @Nullable GradleDslExpression configuration) {
    myConfigurationName = configurationName;
    myDslElement = dslElement;
    myPath = path;
    myConfiguration = configuration;
  }

  @Override
  @NotNull
  protected GradleDslMethodCall getDslElement() {
    return myDslElement;
  }

  @Override
  @NotNull
  public String configurationName() {
    return myConfigurationName;
  }

  @Override
  @NotNull
  public String name() {
    List<String> pathSegments = getPathSegments(path().value());
    int segmentCount = pathSegments.size();
    return segmentCount > 0 ? pathSegments.get(segmentCount - 1) : "";
  }

  @Override
  public void setName(@NotNull String name) {
    String newPath;

    // Keep empty spaces, needed when putting the path back together
    List<String> segments = Splitter.on(GRADLE_PATH_SEPARATOR).splitToList(path().value());
    List<String> modifiableSegments = Lists.newArrayList(segments);
    int segmentCount = modifiableSegments.size();
    if (segmentCount == 0) {
      newPath = GRADLE_PATH_SEPARATOR + name.trim();
    }
    else {
      modifiableSegments.set(segmentCount - 1, name);
      newPath = Joiner.on(GRADLE_PATH_SEPARATOR).join(modifiableSegments);
    }
    setPath(newPath);
  }

  @Override
  @NotNull
  public GradleNotNullValue<String> path() {
    String path = myPath.getValue(String.class);
    assert path != null;
    return new GradleNotNullValueImpl<>(myPath, path);
  }

  @Override
  public void setPath(@NotNull String path) {
    myPath.setValue(path);
  }

  @Override
  @NotNull
  public GradleNullableValue<String> configuration() {
    if (myConfiguration == null) {
      return new GradleNullableValueImpl<>(myDslElement, null);
    }
    return new GradleNullableValueImpl<>(myConfiguration, myConfiguration.getValue(String.class));
  }

  @Override
  public void setConfiguration(@NotNull String configuration) {
    if (myConfiguration != null) {
      myConfiguration.setValue(configuration);
      return;
    }

    GradleDslElement parent = myPath.getParent();
    if (parent instanceof GradleDslExpressionMap) {
      ((GradleDslExpressionMap)parent).setNewLiteral(CONFIGURATION, configuration);
    }
    else {
      String path = path().value();
      if (myPath instanceof GradleDslLiteral) { // TODO: support copying non string literal path values into map form.
        GradleDslExpressionMap newMapArgument = new GradleDslExpressionMap(myDslElement, PROJECT);
        newMapArgument.setNewLiteral(PATH, path);
        newMapArgument.setNewLiteral(CONFIGURATION, configuration);
        myDslElement.remove(myPath);
        myDslElement.addNewArgument(newMapArgument);
      }
    }
  }

  @Override
  public void removeConfiguration() {
    if (myConfiguration != null) {
      GradleDslElement parent = myConfiguration.getParent();
      if (parent instanceof GradleDslExpressionMap) {
        ((GradleDslExpressionMap)parent).removeProperty(CONFIGURATION);
        myConfiguration = null;
      }
    }
  }
}
