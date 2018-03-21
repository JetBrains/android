/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.android;

import com.android.tools.idea.gradle.dsl.api.android.SigningConfigModel.SigningConfigPassword.Type;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;

public interface SigningConfigModel {
  @NotNull
  String name();

  @NotNull
  GradleNullableValue<String> storeFile();

  @NotNull
  SigningConfigModel setStoreFile(@NotNull String storeFile);

  @NotNull
  SigningConfigModel removeStoreFile();

  @NotNull
  GradleNullableValue<SigningConfigPassword> storePassword();

  @NotNull
  SigningConfigModel setStorePassword(@NotNull Type type, @NotNull String storePassword);

  @NotNull
  SigningConfigModel removeStorePassword();

  @NotNull
  GradleNullableValue<String> storeType();

  @NotNull
  SigningConfigModel setStoreType(@NotNull String storeType);

  @NotNull
  SigningConfigModel removeStoreType();

  @NotNull
  GradleNullableValue<String> keyAlias();

  @NotNull
  SigningConfigModel setKeyAlias(@NotNull String keyAlias);

  @NotNull
  SigningConfigModel removeKeyAlias();

  @NotNull
  GradleNullableValue<SigningConfigPassword> keyPassword();

  @NotNull
  SigningConfigModel setKeyPassword(@NotNull Type type, @NotNull String keyPassword);

  @NotNull
  SigningConfigModel removeKeyPassword();

  final class SigningConfigPassword {
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
