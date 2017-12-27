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
import com.android.tools.idea.gradle.dsl.model.android.SigningConfigModel.SigningConfigPassword.Type;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.android.SigningConfigModel.SigningConfigPassword.Type.*;

public class SigningConfigModel extends GradleDslBlockModel {
  @NonNls private static final String SYSTEM_GETENV = "System.getenv";
  @NonNls private static final String SYSTEM_CONSOLE_READ_LINE = "System.console().readLine";

  @NonNls private static final String STORE_FILE = "storeFile";
  @NonNls private static final String STORE_PASSWORD = "storePassword";
  @NonNls private static final String STORE_TYPE = "storeType";
  @NonNls private static final String KEY_ALIAS = "keyAlias";
  @NonNls private static final String KEY_PASSWORD = "keyPassword";

  public SigningConfigModel(@NotNull SigningConfigDslElement dslElement) {
    super(dslElement);
    myDslElement = dslElement;
  }

  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @NotNull
  public GradleNullableValue<String> storeFile() {
    GradleDslExpression expression = getStoreFileExpression();
    if (expression == null) {
      return new GradleNullableValue<>(myDslElement, null);
    }
    return new GradleNullableValue<>(expression, expression.getValue(String.class));
  }

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

  @NotNull
  public GradleNullableValue<SigningConfigPassword> storePassword() {
    return getPasswordValue(STORE_PASSWORD);
  }

  public SigningConfigModel setStorePassword(@NotNull Type type, @NotNull String storePassword) {
    setPasswordValue(STORE_PASSWORD, type, storePassword);
    return this;
  }

  public SigningConfigModel removeStorePassword() {
    myDslElement.removeProperty(STORE_PASSWORD);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> storeType() {
    return myDslElement.getLiteralProperty(STORE_TYPE, String.class);
  }

  public SigningConfigModel setStoreType(@NotNull String storeType) {
    myDslElement.setNewLiteral(STORE_TYPE, storeType);
    return this;
  }

  public SigningConfigModel removeStoreType() {
    myDslElement.removeProperty(STORE_TYPE);
    return this;
  }

  @NotNull
  public GradleNullableValue<String> keyAlias() {
    return myDslElement.getLiteralProperty(KEY_ALIAS, String.class);
  }

  public SigningConfigModel setKeyAlias(@NotNull String keyAlias) {
    myDslElement.setNewLiteral(KEY_ALIAS, keyAlias);
    return this;
  }

  public SigningConfigModel removeKeyAlias() {
    myDslElement.removeProperty(KEY_ALIAS);
    return this;
  }

  @NotNull
  public GradleNullableValue<SigningConfigPassword> keyPassword() {
    return getPasswordValue(KEY_PASSWORD);
  }

  public SigningConfigModel setKeyPassword(@NotNull Type type, @NotNull String keyPassword) {
    setPasswordValue(KEY_PASSWORD, type, keyPassword);
    return this;
  }

  public SigningConfigModel removeKeyPassword() {
    myDslElement.removeProperty(KEY_PASSWORD);
    return this;
  }

  @NotNull
  private GradleNullableValue<SigningConfigPassword> getPasswordValue(@NotNull String property) {
    GradleDslExpression passwordElement = getPasswordElement(property);
    if (passwordElement == null) {
      return new GradleNullableValue<>(myDslElement, null);
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
      return new GradleNullableValue<>(passwordElement, new SigningConfigPassword(passwordType, passwordText));
    }

    return new GradleNullableValue<>(passwordElement, null);
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

  public static final class SigningConfigPassword {
    public enum Type {
      PLAIN_TEXT, // Password specified in the gradle file as plain text.
      ENVIRONMENT_VARIABLE, // Password read from an environment variable. Ex: System.getenv("KSTOREPWD")
      CONSOLE_READ // Password read from Console. Ex: System.console().readLine("\nKeystore password: ")
    }

    @NotNull private final Type myType;
    @NotNull private final String myPasswordText;

    public SigningConfigPassword(@NotNull Type type, @NotNull String passwordText) {
      myType = type;
      myPasswordText = passwordText;
    }

    @NotNull
    public Type getType() {
      return myType;
    }

    @NotNull
    public String getPasswordText() {
      return myPasswordText;
    }

    @Override
    public String toString() {
      return String.format("Type: %1$s, Text: %2$s", myType, myPasswordText);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(myType, myPasswordText);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (!(o instanceof SigningConfigPassword)) {
        return false;
      }

      SigningConfigPassword other = (SigningConfigPassword)o;
      return myType.equals(other.myType)
             && myPasswordText.equals(other.myPasswordText);
    }
  }
}
