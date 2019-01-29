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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

public class FileDependencyModelImpl extends DependencyModelImpl implements FileDependencyModel {
  @NonNls public static final String FILES = "files";

  @NotNull private String myConfigurationName;
  @NotNull private final GradleDslSimpleExpression myFileDslExpression;

  static Collection<FileDependencyModel> create(@NotNull String configurationName, @NotNull GradleDslMethodCall methodCall) {
    List<FileDependencyModel> result = new ArrayList<>();
    if (FILES.equals(methodCall.getMethodName())) {
      List<GradleDslExpression> arguments = methodCall.getArguments();
      for (GradleDslElement argument : arguments) {
        if (argument instanceof GradleDslSimpleExpression) {
          result.add(new FileDependencyModelImpl(configurationName, (GradleDslSimpleExpression)argument));
        }
      }
    }
    return result;
  }

  static void create(@NotNull GradlePropertiesDslElement parent,
                     @NotNull String configurationName,
                     @NotNull String file) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    GradleDslMethodCall methodCall = new GradleDslMethodCall(parent, name, FILES);
    GradleDslLiteral fileDslLiteral = new GradleDslLiteral(methodCall, name);
    fileDslLiteral.setElementType(REGULAR);
    fileDslLiteral.setValue(file);
    methodCall.addNewArgument(fileDslLiteral);
    parent.setNewElement(methodCall);
  }

  private FileDependencyModelImpl(@NotNull String configurationName,
                                  @NotNull GradleDslSimpleExpression fileDslExpression) {
    myConfigurationName = configurationName;
    myFileDslExpression = fileDslExpression;
  }

  @Override
  @NotNull
  protected GradleDslElement getDslElement() {
    return myFileDslExpression;
  }

  @Override
  @NotNull
  public String configurationName() {
    return myConfigurationName;
  }

  @Override
  @NotNull
  public ResolvedPropertyModel file() {
    return GradlePropertyModelBuilder.create(myFileDslExpression).asMethod(true).buildResolved();
  }
}
