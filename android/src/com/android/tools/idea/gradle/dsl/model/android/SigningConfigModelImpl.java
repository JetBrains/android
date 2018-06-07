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
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel.SigningConfigPassword.Type.*;

public class SigningConfigModelImpl extends GradleDslBlockModel implements SigningConfigModel {
  @NonNls private static final String SYSTEM_GETENV = "System.getenv";
  @NonNls private static final String SYSTEM_CONSOLE_READ_LINE = "System.console().readLine";

  @NonNls private static final String STORE_FILE = "storeFile";
  @NonNls private static final String STORE_PASSWORD = "storePassword";
  @NonNls private static final String STORE_TYPE = "storeType";
  @NonNls private static final String KEY_ALIAS = "keyAlias";
  @NonNls private static final String KEY_PASSWORD = "keyPassword";

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
  public GradleNullableValue<String> storeFile() {
    GradleDslExpression expression = getStoreFileExpression();
    if (expression == null) {
      return new GradleNullableValueImpl<>(myDslElement, null);
    }
    return new GradleNullableValueImpl<>(expression, expression.getValue(String.class));
  }

  @Override
  @NotNull
  public SigningConfigModel setStoreFile(@NotNull String storeFile) {
    if (myDslElement.getPropertyElement(STORE_FILE) == null) {
      GradleDslMethodCall methodCall = new GradleDslMethodCall(myDslElement, "file", STORE_FILE);
      GradleDslLiteral literal = new GradleDslLiteral(methodCall, "");
      literal.setValue(storeFile);
      methodCall.addNewArgument(literal);
      myDslElement.setNewElement(STORE_FILE, methodCall);
    }
    else {
      GradleDslExpression expression = getStoreFileExpression();
      if (expression != null) {
        expression.setValue(storeFile);
      }
    }
    return this;
  }

  @Override
  @NotNull
  public SigningConfigModel removeStoreFile() {
    myDslElement.removeProperty(STORE_FILE);
    return this;
  }

  @Nullable
  private GradleDslExpression getStoreFileExpression() {
    GradleDslMethodCall methodCall = myDslElement.getPropertyElement(STORE_FILE, GradleDslMethodCall.class);
    if (methodCall == null || methodCall.getArguments().isEmpty()) {
      return null;
    }
    GradleDslElement argument = methodCall.getArguments().get(0);
    if (argument instanceof GradleDslExpression) {
      return (GradleDslExpression)argument;
    }
    return null;
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
  public GradleNullableValue<String> storeType() {
    return myDslElement.getLiteralProperty(STORE_TYPE, String.class);
  }

  @Override
  @NotNull
  public SigningConfigModel setStoreType(@NotNull String storeType) {
    myDslElement.setNewLiteral(STORE_TYPE, storeType);
    return this;
  }

  @Override
  @NotNull
  public SigningConfigModel removeStoreType() {
    myDslElement.removeProperty(STORE_TYPE);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<String> keyAlias() {
    return myDslElement.getLiteralProperty(KEY_ALIAS, String.class);
  }

  @Override
  @NotNull
  public SigningConfigModel setKeyAlias(@NotNull String keyAlias) {
    myDslElement.setNewLiteral(KEY_ALIAS, keyAlias);
    return this;
  }

  @Override
  @NotNull
  public SigningConfigModel removeKeyAlias() {
    myDslElement.removeProperty(KEY_ALIAS);
    return this;
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
    switch (passwordElement.getName()) {
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
    if (type == ENVIRONMENT_VARIABLE) {
      methodCall = new GradleDslMethodCall(myDslElement, SYSTEM_GETENV, property);
    }
    else if (type == CONSOLE_READ) {
      methodCall = new GradleDslMethodCall(myDslElement, SYSTEM_CONSOLE_READ_LINE, property);
    }

    if (methodCall != null) {
      GradleDslLiteral argumentElement = new GradleDslLiteral(methodCall, methodCall.getName());
      argumentElement.setValue(text);
      methodCall.addNewArgument(argumentElement);
      myDslElement.setNewElement(property, methodCall);
    }
  }
}
