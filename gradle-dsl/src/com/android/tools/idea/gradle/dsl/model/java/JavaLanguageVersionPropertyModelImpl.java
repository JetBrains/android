/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.java;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.createBasicExpression;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.replaceElement;

import com.android.tools.idea.gradle.dsl.api.java.JavaLanguageVersionPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil;
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import kotlin.text.StringsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaLanguageVersionPropertyModelImpl extends ResolvedPropertyModelImpl implements JavaLanguageVersionPropertyModel {

  private static final String JAVA_LANGUAGE_VERSION_OF_NAME = "JavaLanguageVersion.of";

  public JavaLanguageVersionPropertyModelImpl(@NotNull GradlePropertyModelImpl realModel) {
    super(realModel);
  }

  @Override
  @Nullable
  public Integer version() {
    GradleDslSimpleExpression element = findArgumentElement(myRealModel.getRawElement());
    if (element != null) {
      Object value = element.getValue();
      if (value instanceof Integer) {
        return (Integer)value;
      }
      if (value instanceof String) {
        return StringsKt.toIntOrNull((String)value);
      }
    }
    return null;
  }

  @Override
  public void setVersion(int version) {
    GradleDslSimpleExpression element = findArgumentElement(myRealModel.getRawElement());
    if (element != null) {
      element.setValue(version);
    }
    else {
      GradleDslElement holder = myRealModel.getHolder();
      GradleNameElement nameElement = GradleNameElement.create(getName());
      GradleDslMethodCall methodCall = new GradleDslMethodCall(holder, nameElement, JAVA_LANGUAGE_VERSION_OF_NAME);
      GradleDslSimpleExpression newElement = createBasicExpression(methodCall, version, GradleNameElement.empty());
      methodCall.addNewArgument(newElement);
      replaceElement(holder, myRealModel.getElement(), methodCall);
    }
  }

  @Nullable
  private GradleDslSimpleExpression findArgumentElement(GradleDslElement element) {
    if (element == null) {
      return null;
    }

    element = PropertyUtil.followElement(element);

    if (element instanceof GradleDslMethodCall) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)element;
      if (JAVA_LANGUAGE_VERSION_OF_NAME.equals(methodCall.getMethodName()) && methodCall.getArguments().size() == 1) {
        GradleDslExpression argument = methodCall.getArguments().get(0);
        if (argument instanceof GradleDslSimpleExpression) {
          return PropertyUtil.resolveElement((GradleDslSimpleExpression)argument);
        }
      }
    }
    return null;
  }
}
