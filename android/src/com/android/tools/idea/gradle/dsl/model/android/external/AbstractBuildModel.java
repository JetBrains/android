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
package com.android.tools.idea.gradle.dsl.model.android.external;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;


/**
 * Base class for the external native build models like {@link CMakeModel} and {@link NdkBuildModel}.
 */
public abstract class AbstractBuildModel extends GradleDslBlockModel {
  @NonNls private static final String PATH = "path";

  protected AbstractBuildModel(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @Nullable
  public File path() {
    GradleDslElement pathElement = myDslElement.getPropertyElement(PATH);
    if (pathElement instanceof GradleDslMethodCall || pathElement instanceof GradleDslNewExpression) {
      return ((GradleDslExpression)pathElement).getValue(File.class);
    }
    else if (pathElement instanceof GradleDslExpression) {
      String path = ((GradleDslExpression)pathElement).getValue(String.class);
      if (path != null) {
        return new File(path);
      }
    }
    return null;
  }

  @NotNull
  public AbstractBuildModel setPath(File path) {
    GradleDslElement pathElement = myDslElement.getPropertyElement(PATH);
    if (pathElement == null) {
      // Only adding new path element is supported. Updating an existing path entry is not supported as there is no use case right now.
      GradleDslLiteral pathLiteral = new GradleDslLiteral(myDslElement, PATH);
      pathLiteral.setValue(toSystemIndependentName(path.getPath()));
      myDslElement.setNewElement(PATH, pathLiteral);
    }
    return this;
  }

  @NotNull
  public AbstractBuildModel removePath() {
    myDslElement.removeProperty(PATH);
    return this;
  }
}
