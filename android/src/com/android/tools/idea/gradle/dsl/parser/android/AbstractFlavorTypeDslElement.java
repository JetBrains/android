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
package com.android.tools.idea.gradle.dsl.parser.android;

import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Common base class for {@link BuildTypeDslElement} and {@link ProductFlavorDslElement}.
 */
public abstract class AbstractFlavorTypeDslElement extends GradleDslBlockElement {
  // Stores the method name of the block used in the KTS file. Ex: for the block with the name getByName("release"), methodName will be
  // getByName.
  @Nullable
  private String methodName;

  protected AbstractFlavorTypeDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  protected AbstractFlavorTypeDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name, @NotNull String methodName) {
    super(parent, name);
    this.methodName = methodName;
  }

  public void setMethodName(String methodName) {
    this.methodName = methodName;
  }

  @Nullable
  public String getMethodName() {
    return  methodName;
  }

  @Override
  public void addParsedElement(@NotNull GradleDslElement element) {
    String property = element.getName();
    if (property.equals("consumerProguardFiles") && element instanceof GradleDslSimpleExpression) {
      addAsParsedDslExpressionList((GradleDslSimpleExpression)element);
      return;
    }

    if (property.equals("setProguardFiles")) {
      // Clear the property since setProguardFiles overwrites these.
      removeProperty("proguardFiles");
      addToParsedExpressionList("proguardFiles", element);
      return;
    }

    if (property.equals("proguardFiles") || property.equals("proguardFile")) {
      addToParsedExpressionList("proguardFiles", element);
      return;
    }

    super.addParsedElement(element);
  }

  @Override
  public boolean isInsignificantIfEmpty() {
    // defaultConfig is special in that is can be deleted if it is empty.
    return myName.name().equals(AndroidModelImpl.DEFAULT_CONFIG);
  }
}
