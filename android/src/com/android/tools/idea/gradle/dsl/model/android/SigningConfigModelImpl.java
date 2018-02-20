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

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel;
import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel.SigningConfigPassword.Type;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform.ElementBindingFunction;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel.SigningConfigPassword.Type.*;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform.ElementTransform;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyTransform.TransformCondition;
import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.*;

public class SigningConfigModelImpl extends GradleDslBlockModel implements SigningConfigModel {
  @NonNls private static final String SYSTEM_GETENV = "System.getenv";
  @NonNls private static final String SYSTEM_CONSOLE_READ_LINE = "System.console().readLine";

  @NonNls private static final String STORE_FILE = "storeFile";
  @NonNls private static final String STORE_FILE_METHOD = "file";
  @NonNls private static final String STORE_PASSWORD = "storePassword";
  @NonNls private static final String STORE_TYPE = "storeType";
  @NonNls private static final String KEY_ALIAS = "keyAlias";
  @NonNls private static final String KEY_PASSWORD = "keyPassword";

  @NotNull
  private static final TransformCondition STORE_FILE_COND = e -> e == null || e instanceof GradleDslMethodCall;

  @NotNull
  private static final ElementTransform STORE_FILE_ELEMENT_TRANSFORM = e -> {
    if (STORE_FILE_COND.test(e)) {
      GradleDslMethodCall methodCall = (GradleDslMethodCall)e;
      if (methodCall.getArguments().isEmpty()) {
        return null;
      }
      GradleDslElement arg = methodCall.getArguments().get(0);
      if (arg instanceof GradleDslExpression) {
        return (GradleDslExpression)arg;
      }
      return null;
    }
    else {
      return defaultElementTransform.transform(e);
    }
  };

  @NotNull
  private static final ElementBindingFunction STORE_FILE_BINDING = (holder, oldElement, value, name) -> {
    if (oldElement == null) {
      // No element currently exists.
      GradleDslMethodCall methodCall = new GradleDslMethodCall(holder, GradleNameElement.create(STORE_FILE_METHOD), name);
      GradleDslExpression literal = createOrReplaceBasicExpression(methodCall, null, value, "");
      methodCall.addNewArgument(literal);
      return methodCall;
    }
    else {
      // Replace the argument.
      GradleDslMethodCall methodCall = (GradleDslMethodCall)oldElement;
      GradleDslElement element = STORE_FILE_ELEMENT_TRANSFORM.transform(methodCall);
      GradleDslExpression expression = createOrReplaceBasicExpression(methodCall, element, value, "");
      if (element != expression) {
        // We needed to create a new element, replace the old one.
        methodCall.remove(element);
        methodCall.addNewArgument(expression);
      }
      return methodCall;
    }
  };

  @NotNull private static final PropertyTransform STORE_FILE_TRANSFORM =
    new PropertyTransform(STORE_FILE_COND, STORE_FILE_ELEMENT_TRANSFORM, STORE_FILE_BINDING);


  public SigningConfigModelImpl(@NotNull SigningConfigDslElement dslElement) {
    super(dslElement);
    myDslElement = dslElement;
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  @NotNull
  public ResolvedPropertyModel storeFile() {
    return GradlePropertyModelBuilder.create(myDslElement, STORE_FILE).asMethod(true)
      .addTransform(STORE_FILE_TRANSFORM).buildResolved();
  }

  @Override
  @NotNull
  public GradleNullableValue<SigningConfigPassword> storePassword() {
    return getPasswordValue(STORE_PASSWORD);
  }

  @Override
  @NotNull
  public SigningConfigModel setStorePassword(@NotNull Type type, @NotNull String storePassword) {
    setPasswordValue(STORE_PASSWORD, type, storePassword);
    return this;
  }

  @Override
  @NotNull
  public SigningConfigModel removeStorePassword() {
    myDslElement.removeProperty(STORE_PASSWORD);
    return this;
  }

  @Override
  @NotNull
  public ResolvedPropertyModel storeType() {
    return getModelForProperty(STORE_TYPE);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel keyAlias() {
    return getModelForProperty(KEY_ALIAS);
  }

  @Override
  @NotNull
  public GradleNullableValue<SigningConfigPassword> keyPassword() {
    return getPasswordValue(KEY_PASSWORD);
  }

  @Override
  @NotNull
  public SigningConfigModel setKeyPassword(@NotNull Type type, @NotNull String keyPassword) {
    setPasswordValue(KEY_PASSWORD, type, keyPassword);
    return this;
  }

  @Override
  @NotNull
  public SigningConfigModel removeKeyPassword() {
    myDslElement.removeProperty(KEY_PASSWORD);
    return this;
  }

  @NotNull
  private GradleNullableValue<SigningConfigPassword> getPasswordValue(@NotNull String property) {
    GradleDslExpression passwordElement = getPasswordElement(property);
    if (passwordElement == null) {
      return new GradleNullableValueImpl<>(myDslElement, null);
    }

    Type passwordType;
    switch (passwordElement.getFullName()) {
      case SYSTEM_GETENV:
        passwordType = ENVIRONMENT_VARIABLE;
        break;
      case SYSTEM_CONSOLE_READ_LINE:
        passwordType = CONSOLE_READ;
        break;
      default:
        passwordType = PLAIN_TEXT;
        break;
    }

    String passwordText = passwordElement.getValue(String.class);
    if (passwordText != null) {
      return new GradleNullableValueImpl<>(passwordElement, new SigningConfigPassword(passwordType, passwordText));
    }

    return new GradleNullableValueImpl<>(passwordElement, null);
  }

  @Nullable
  private GradleDslExpression getPasswordElement(@NotNull String property) {
    GradleDslExpression passwordElement = myDslElement.getPropertyElement(property, GradleDslExpression.class);
    if (passwordElement == null) {
      return null;
    }

    if (passwordElement instanceof GradleDslMethodCall) {
      List<GradleDslElement> arguments = ((GradleDslMethodCall)passwordElement).getArguments();
      if (!arguments.isEmpty()) {
        GradleDslElement argumentElement = arguments.get(0);
        if (argumentElement instanceof GradleDslExpression) {
          return (GradleDslExpression)argumentElement;
        }
      }
    }
    else {
      return passwordElement;
    }

    return null;
  }

  private void setPasswordValue(@NotNull String property, @NotNull Type type, @NotNull String text) {
    if (type == PLAIN_TEXT) {
      myDslElement.setNewLiteral(property, text);
      return;
    }

    GradleNullableValue<SigningConfigPassword> passwordValue = getPasswordValue(property);
    SigningConfigPassword password = passwordValue.value();
    if (password != null && password.getType() == type) {
      GradleDslExpression element = getPasswordElement(property);
      if (element != null) {
        element.setValue(text);
        return;
      }
    }

    GradleDslMethodCall methodCall = null;
    GradleNameElement name = null;
    if (type == ENVIRONMENT_VARIABLE) {
      name = GradleNameElement.create(SYSTEM_GETENV);
      methodCall = new GradleDslMethodCall(myDslElement, name, property);
    }
    else if (type == CONSOLE_READ) {
      name = GradleNameElement.create(SYSTEM_CONSOLE_READ_LINE);
      methodCall = new GradleDslMethodCall(myDslElement, name, property);
    }

    if (methodCall != null) {
      GradleDslLiteral argumentElement = new GradleDslLiteral(methodCall, name);
      argumentElement.setValue(text);
      methodCall.addNewArgument(argumentElement);
      myDslElement.setNewElement(property, methodCall);
    }
  }
}
