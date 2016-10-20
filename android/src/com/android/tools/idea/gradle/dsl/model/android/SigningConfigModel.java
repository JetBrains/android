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
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.android.SigningConfigDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SigningConfigModel extends GradleDslBlockModel {
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
    if (methodCall == null || methodCall.getArguments().size() == 0) {
      return null;
    }
    GradleDslElement argument = methodCall.getArguments().get(0);
    if (argument instanceof GradleDslExpression) {
      return (GradleDslExpression)argument;
    }
    return null;
  }

  @NotNull
  public GradleNullableValue<String> storePassword() {
    return myDslElement.getLiteralProperty(STORE_PASSWORD, String.class);
  }

  public SigningConfigModel setStorePassword(@NotNull String storePassword) {
    myDslElement.setNewLiteral(STORE_PASSWORD, storePassword);
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
  public GradleNullableValue<String> keyPassword() {
    return myDslElement.getLiteralProperty(KEY_PASSWORD, String.class);
  }

  public SigningConfigModel setKeyPassword(@NotNull String keyPassword) {
    myDslElement.setNewLiteral(KEY_PASSWORD, keyPassword);
    return this;
  }

  public SigningConfigModel removeKeyPassword() {
    myDslElement.removeProperty(KEY_PASSWORD);
    return this;
  }

  /* TODO Add support for notations like
   * storePassword System.getenv("KSTOREPWD")
   * keyPassword System.getenv("KEYPWD")
   * storePassword System.console().readLine("\nKeystore password: ")
   * keyPassword System.console().readLine("\nKey password: ")
   */
}
