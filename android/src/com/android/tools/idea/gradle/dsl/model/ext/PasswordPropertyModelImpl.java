/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.ext;

import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel;
import com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType;
import com.android.tools.idea.gradle.dsl.api.ext.PropertyType;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.ext.PasswordPropertyModel.PasswordType.*;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform.*;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createOrReplaceBasicExpression;

public class PasswordPropertyModelImpl extends MultiTypePropertyModelImpl<PasswordType> implements PasswordPropertyModel {
  @NonNls private static final String SYSTEM_GETENV = "System.getenv";
  @NonNls private static final String SYSTEM_CONSOLE_READ_LINE = "System.console().readLine";

  @NotNull
  private static TransformCondition getTransformCondition(@NotNull String str) {
    return ((element) -> element != null &&
                         element instanceof GradleDslMethodCall &&
                         ((GradleDslMethodCall)element).getMethodName().equals(str));
  }

  @NotNull
  private static final ElementTransform TRANSFORM_FUNCTION = e -> {
    if (e instanceof GradleDslMethodCall) {
      List<GradleDslElement> arguments = ((GradleDslMethodCall)e).getArguments();
      if (!arguments.isEmpty()) {
        GradleDslElement argumentElement = arguments.get(0);
        if (argumentElement instanceof GradleDslExpression) {
          return (GradleDslExpression)argumentElement;
        }
      }
    }
    return null;
  };

  @NotNull
  private static ElementBindingFunction getBindingFunction(@NotNull String str) {
    return ((holder, old, value, name) -> {
      GradleNameElement nameElement = GradleNameElement.create(name);
      if (old != null && old instanceof GradleDslMethodCall) {
        GradleDslMethodCall methodCall = (GradleDslMethodCall)old;

        if (methodCall.getMethodName().equals(str)) {
          GradleDslElement baseElement = TRANSFORM_FUNCTION.transform(old);
          if (baseElement != null) {
            GradleDslExpression newBaseElement = createOrReplaceBasicExpression(holder, baseElement, value, nameElement);
            if (baseElement != newBaseElement) {
              methodCall.remove(baseElement);
              methodCall.addNewArgument(newBaseElement);
            }
            return old;
          }
        }
      }

      GradleDslMethodCall methodCall = new GradleDslMethodCall(holder, nameElement, str);
      GradleDslExpression argument = createOrReplaceBasicExpression(methodCall, null, value, nameElement);
      methodCall.addNewArgument(argument);
      return methodCall;
    });
  }

  @NotNull private static PropertyTransform ENV_VAR_TRANSFORM =
    new PropertyTransform(getTransformCondition(SYSTEM_GETENV), TRANSFORM_FUNCTION, getBindingFunction(SYSTEM_GETENV));
  @NotNull private static PropertyTransform SYS_CON_TRANSFORM =
    new PropertyTransform(getTransformCondition(SYSTEM_CONSOLE_READ_LINE), TRANSFORM_FUNCTION,
                          getBindingFunction(SYSTEM_CONSOLE_READ_LINE));

  public PasswordPropertyModelImpl(@NotNull GradleDslElement element) {
    super(PLAIN_TEXT, element, createMap());
  }

  public PasswordPropertyModelImpl(@NotNull GradleDslElement holder,
                                   @NotNull PropertyType type,
                                   @NotNull String name) {
    super(PLAIN_TEXT, holder, type, name, createMap());
  }

  private static LinkedHashMap<PasswordType, PropertyTransform> createMap() {
    LinkedHashMap<PasswordType, PropertyTransform> transforms = new LinkedHashMap<>();
    transforms.put(ENVIRONMENT_VARIABLE, ENV_VAR_TRANSFORM);
    transforms.put(CONSOLE_READ, SYS_CON_TRANSFORM);
    // PLAIN_TEXT uses the defaultTransform so doesn't need to be added.
    return transforms;
  }
}
