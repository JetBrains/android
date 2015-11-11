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

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ModuleDependencyModel extends DependencyModel {
  @NotNull private String myConfigurationName;
  @NotNull private GradleDslMethodCall myDslElement;
  @NotNull private GradleDslExpression myPath;
  @Nullable private GradleDslExpression myConfiguration;

  ModuleDependencyModel(@NotNull String configurationName, @NotNull GradleDslMethodCall dslElement, @NotNull GradleDslExpression path,
                        @Nullable GradleDslExpression configuration) {
    myConfigurationName = configurationName;
    myDslElement = dslElement;
    myPath = path;
    myConfiguration = configuration;
  }

  @NotNull
  @Override
  protected GradleDslMethodCall getDslElement() {
    return myDslElement;
  }

  @Override
  @NotNull
  public String getConfigurationName() {
    return myConfigurationName;
  }

  @Nullable
  public String path() {
    return myPath.getValue(String.class);
  }

  public void setPath(@NotNull String path) {
    myPath.setValue(path);
  }

  @Nullable
  public String configuration() {
    if (myConfiguration == null) {
      return null;
    }
    return myConfiguration.getValue(String.class);
  }

  void setConfiguration(@NotNull String configuration) {
    if (myConfiguration != null) {
      myConfiguration.setValue(configuration);
    } else {
      // TODO implement logic of adding configuration
    }
  }


  @NotNull
  protected static List<ModuleDependencyModel> create(@NotNull String configurationName, @NotNull GradleDslMethodCall methodCall) {
    List<ModuleDependencyModel> result = Lists.newArrayList();
    if ("project".equals(methodCall.getName())) {
      for (GradleDslElement argument : methodCall.getArguments()) {
        if (argument instanceof GradleDslExpression) {
          result.add(new ModuleDependencyModel(configurationName, methodCall, (GradleDslExpression)argument, null));
        } else if (argument instanceof GradleDslExpressionMap) {
          GradleDslExpressionMap dslMap = (GradleDslExpressionMap)argument;
          GradleDslExpression pathElement = dslMap.getProperty("path", GradleDslExpression.class);
          if (pathElement == null) {
            assert methodCall.getPsiElement() != null;
            throw new IllegalArgumentException("'" + methodCall.getPsiElement().getText() + "' is not valid module dependency.");
          }
          GradleDslExpression configuration = dslMap.getProperty("configuration", GradleDslExpression.class);
          result.add(new ModuleDependencyModel(configurationName, methodCall, pathElement, configuration));
        }
      }
    }
    return result;
  }
}
