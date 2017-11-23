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
package com.android.tools.idea.gradle.dsl.model.android.externalNativeBuild;

import com.android.tools.idea.gradle.dsl.api.android.externalNativeBuild.AbstractBuildModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;


/**
 * Base class for the external native build models like {@link CMakeModelImpl} and {@link NdkBuildModelImpl}.
 */
public abstract class AbstractBuildModelImpl extends GradleDslBlockModel implements AbstractBuildModel {
  @NonNls private static final String PATH = "path";

  protected AbstractBuildModelImpl(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public GradleNullableValue<File> path() {
    GradleDslElement pathElement = myDslElement.getPropertyElement(PATH);
    if (pathElement == null) {
      return new GradleNullableValueImpl<>(myDslElement, null);
    }

    File value = null;
    if (pathElement instanceof GradleDslMethodCall || pathElement instanceof GradleDslNewExpression) {
      value = ((GradleDslExpression)pathElement).getValue(File.class);
    }
    else if (pathElement instanceof GradleDslExpression) {
      String path = ((GradleDslExpression)pathElement).getValue(String.class);
      if (path != null) {
        value = new File(path);
      }
    }

    return new GradleNullableValueImpl<>(pathElement, value);
  }

  @Override
  @NotNull
  public AbstractBuildModel setPath(@NotNull File path) {
    GradleDslElement pathElement = myDslElement.getPropertyElement(PATH);
    if (pathElement == null) {
      // Only adding new path element is supported. Updating an existing path entry is not supported as there is no use case right now.
      GradleDslLiteral pathLiteral = new GradleDslLiteral(myDslElement, PATH);
      pathLiteral.setValue(toSystemIndependentName(path.getPath()));
      myDslElement.setNewElement(PATH, pathLiteral);
    }
    return this;
  }

  @Override
  @NotNull
  public AbstractBuildModel removePath() {
    myDslElement.removeProperty(PATH);
    return this;
  }
}
