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
package com.android.tools.idea.testartifacts.junit;

import com.intellij.conversion.ConversionContext;
import com.intellij.conversion.ConverterProvider;
import com.intellij.conversion.ProjectConverter;
import org.jetbrains.annotations.NotNull;

/**
 * {@link ConverterProvider} providing {@link AndroidJUnitConfigurationConverter}.
 */
public class AndroidJUnitConfigurationConverterProvider extends ConverterProvider {
  protected AndroidJUnitConfigurationConverterProvider() {
    super("junit-to-android-junit-configurations");
  }

  @NotNull
  @Override
  public String getConversionDescription() {
    return "RunConfigurations of type JUnitConfiguration will be converted to AndroidJUnitConfiguration. "
      + "JUnit tests and Android Instrumented tests will work properly together.";
  }

  @NotNull
  @Override
  public ProjectConverter createConverter(@NotNull ConversionContext context) {
    return new AndroidJUnitConfigurationConverter();
  }


}
