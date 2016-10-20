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

import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FileDependencyModel extends DependencyModel {
  @NonNls private static final String FILES = "files";

  @NotNull private String myConfigurationName;
  @NotNull private final GradleDslExpression myFileDslExpression;

  static Collection<? extends FileDependencyModel> create(@NotNull String configurationName, @NotNull GradleDslMethodCall methodCall) {
    List<FileDependencyModel> result = new ArrayList<>();
    if (FILES.equals(methodCall.getName())) {
      List<GradleDslElement> arguments = methodCall.getArguments();
      for (GradleDslElement argument : arguments) {
        if (argument instanceof GradleDslExpression) {
          result.add(new FileDependencyModel(configurationName, (GradleDslExpression)argument));
        }
      }
    }
    return result;
  }

  static void createAndAddToList(@NotNull GradleDslElementList list,
                                 @NotNull String configurationName,
                                 @NotNull String file) {
    String methodName = FILES;
    GradleDslMethodCall methodCall = new GradleDslMethodCall(list, methodName, configurationName);
    GradleDslLiteral fileDslLiteral = new GradleDslLiteral(methodCall, methodName);
    fileDslLiteral.setValue(file);
    methodCall.addNewArgument(fileDslLiteral);
    list.addNewElement(methodCall);
  }

  private FileDependencyModel(@NotNull String configurationName,
                              @NotNull GradleDslExpression fileDslExpression) {
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

  @NotNull
  public GradleNotNullValue<String> file() {
    String file = myFileDslExpression.getValue(String.class);
    assert file != null;
    return new GradleNotNullValue<>(myFileDslExpression, file);
  }

  public void setFile(@NotNull String file) {
    myFileDslExpression.setValue(file);
  }
}
