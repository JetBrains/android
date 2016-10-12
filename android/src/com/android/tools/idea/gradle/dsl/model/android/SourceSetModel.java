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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.SourceSetDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.List;

public class SourceSetModel extends GradleDslBlockModel {
  @NonNls private static final String ROOT = "root";

  public SourceSetModel(@NotNull SourceSetDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Nullable
  public String root() {
    GradleDslExpression rootElement = myDslElement.getProperty(ROOT, GradleDslExpression.class);
    if (rootElement == null) {
      return null;
    }

    if (rootElement instanceof GradleDslMethodCall) {
      List<GradleDslElement> arguments = ((GradleDslMethodCall)rootElement).getArguments();
      if (arguments.isEmpty()) {
        return null;
      }
      GradleDslElement pathArgument = arguments.get(0);
      if (pathArgument instanceof GradleDslExpression) {
        return ((GradleDslExpression)pathArgument).getValue(String.class);
      }
      return null;
    }
    else {
      return rootElement.getValue(String.class);
    }
  }

  @NotNull
  public SourceSetModel setRoot(@NotNull String root) {
    GradleDslExpression rootElement = myDslElement.getProperty(ROOT, GradleDslExpression.class);
    if (rootElement == null) {
      myDslElement.setNewLiteral(ROOT, root);
      return this;
    }

    if (rootElement instanceof GradleDslMethodCall) {
      List<GradleDslElement> arguments = ((GradleDslMethodCall)rootElement).getArguments();
      if (!arguments.isEmpty()) {
        GradleDslElement pathArgument = arguments.get(0);
        if (pathArgument instanceof GradleDslExpression) {
          ((GradleDslExpression)pathArgument).setValue(root);
          return this;
        }
      }
    }

    rootElement.setValue(root);
    return this;
  }

  @NotNull
  public SourceSetModel removeRoot() {
    myDslElement.removeProperty(ROOT);
    return this;
  }
}
