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
package com.android.tools.idea.gradle.dsl.model.android.productFlavors.externalNativeBuild;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractBuildOptionsModel extends GradleDslBlockModel {
  @NonNls private static final String ABI_FILTERS = "abiFilters";
  @NonNls private static final String ARGUMENTS = "arguments";
  @NonNls private static final String C_FLAGS = "cFlags";
  @NonNls private static final String CPP_FLAGS = "cppFlags";
  @NonNls private static final String TARGETS = "targets";

  protected AbstractBuildOptionsModel(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @Nullable
  public List<GradleNotNullValue<String>> abiFilters() {
    return myDslElement.getListProperty(ABI_FILTERS, String.class);
  }

  @NotNull
  public AbstractBuildOptionsModel addAbiFilter(@NotNull String abiFilter) {
    myDslElement.addToNewLiteralList(ABI_FILTERS, abiFilter);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeAbiFilter(@NotNull String abiFilter) {
    myDslElement.removeFromExpressionList(ABI_FILTERS, abiFilter);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeAllAbiFilters() {
    myDslElement.removeProperty(ABI_FILTERS);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel replaceAbiFilter(@NotNull String oldAbiFilter, @NotNull String newAbiFilter) {
    myDslElement.replaceInExpressionList(ABI_FILTERS, oldAbiFilter, newAbiFilter);
    return this;
  }

  @Nullable
  public List<GradleNotNullValue<String>> arguments() {
    return myDslElement.getListProperty(ARGUMENTS, String.class);
  }

  @NotNull
  public AbstractBuildOptionsModel addArgument(@NotNull String argument) {
    myDslElement.addToNewLiteralList(ARGUMENTS, argument);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeArgument(@NotNull String argument) {
    myDslElement.removeFromExpressionList(ARGUMENTS, argument);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeAllArguments() {
    myDslElement.removeProperty(ARGUMENTS);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel replaceArgument(@NotNull String oldArgument, @NotNull String newArgument) {
    myDslElement.replaceInExpressionList(ARGUMENTS, oldArgument, newArgument);
    return this;
  }

  @Nullable
  public List<GradleNotNullValue<String>> cFlags() {
    return myDslElement.getListProperty(C_FLAGS, String.class);
  }

  @NotNull
  public AbstractBuildOptionsModel addCFlag(@NotNull String cFlag) {
    myDslElement.addToNewLiteralList(C_FLAGS, cFlag);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeCFlag(@NotNull String cFlag) {
    myDslElement.removeFromExpressionList(C_FLAGS, cFlag);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeAllCFlags() {
    myDslElement.removeProperty(C_FLAGS);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel replaceCFlag(@NotNull String oldCFlag, @NotNull String newCFlag) {
    myDslElement.replaceInExpressionList(C_FLAGS, oldCFlag, newCFlag);
    return this;
  }

  @Nullable
  public List<GradleNotNullValue<String>> cppFlags() {
    return myDslElement.getListProperty(CPP_FLAGS, String.class);
  }

  @NotNull
  public AbstractBuildOptionsModel addCppFlag(@NotNull String cppFlag) {
    myDslElement.addToNewLiteralList(CPP_FLAGS, cppFlag);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeCppFlag(@NotNull String cppFlag) {
    myDslElement.removeFromExpressionList(CPP_FLAGS, cppFlag);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeAllCppFlags() {
    myDslElement.removeProperty(CPP_FLAGS);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel replaceCppFlag(@NotNull String oldCppFlag, @NotNull String newCppFlag) {
    myDslElement.replaceInExpressionList(CPP_FLAGS, oldCppFlag, newCppFlag);
    return this;
  }

  @Nullable
  public List<GradleNotNullValue<String>> targets() {
    return myDslElement.getListProperty(TARGETS, String.class);
  }

  @NotNull
  public AbstractBuildOptionsModel addTarget(@NotNull String target) {
    myDslElement.addToNewLiteralList(TARGETS, target);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeTarget(@NotNull String target) {
    myDslElement.removeFromExpressionList(TARGETS, target);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel removeAllTargets() {
    myDslElement.removeProperty(TARGETS);
    return this;
  }

  @NotNull
  public AbstractBuildOptionsModel replaceTarget(@NotNull String oldTarget, @NotNull String newTarget) {
    myDslElement.replaceInExpressionList(TARGETS, oldTarget, newTarget);
    return this;
  }
}
