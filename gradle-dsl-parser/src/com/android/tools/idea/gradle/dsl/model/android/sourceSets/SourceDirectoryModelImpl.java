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
package com.android.tools.idea.gradle.dsl.model.android.sourceSets;

import com.android.tools.idea.gradle.dsl.api.android.sourceSets.SourceDirectoryModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SourceDirectoryModelImpl extends GradleDslBlockModel implements SourceDirectoryModel {
  @NonNls private static final String EXCLUDE = "exclude";
  @NonNls private static final String INCLUDE = "include";
  @NonNls private static final String SRC_DIRS = "srcDirs";

  public SourceDirectoryModelImpl(@NotNull SourceDirectoryDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public String name() {
    return myDslElement.getName();
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> excludes() {
    return myDslElement.getListProperty(EXCLUDE, String.class);
  }

  @Override
  @NotNull
  public SourceDirectoryModel addExclude(@NotNull String exclude) {
    myDslElement.addToNewLiteralList(EXCLUDE, exclude);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel removeExclude(@NotNull String exclude) {
    myDslElement.removeFromExpressionList(EXCLUDE, exclude);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel removeAllExcludes() {
    myDslElement.removeProperty(EXCLUDE);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel replaceExclude(@NotNull String oldExclude, @NotNull String newExclude) {
    myDslElement.replaceInExpressionList(EXCLUDE, oldExclude, newExclude);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> includes() {
    return myDslElement.getListProperty(INCLUDE, String.class);
  }

  @Override
  @NotNull
  public SourceDirectoryModel addInclude(@NotNull String include) {
    myDslElement.addToNewLiteralList(INCLUDE, include);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel removeInclude(@NotNull String include) {
    myDslElement.removeFromExpressionList(INCLUDE, include);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel removeAllIncludes() {
    myDslElement.removeProperty(INCLUDE);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel replaceInclude(@NotNull String oldInclude, @NotNull String newInclude) {
    myDslElement.replaceInExpressionList(INCLUDE, oldInclude, newInclude);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> srcDirs() {
    return myDslElement.getListProperty(SRC_DIRS, String.class);
  }

  @Override
  @NotNull
  public SourceDirectoryModel addSrcDir(@NotNull String srcDir) {
    myDslElement.addToNewLiteralList(SRC_DIRS, srcDir);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel removeSrcDir(@NotNull String srcDir) {
    myDslElement.removeFromExpressionList(SRC_DIRS, srcDir);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel removeAllSrcDirs() {
    myDslElement.removeProperty(SRC_DIRS);
    return this;
  }

  @Override
  @NotNull
  public SourceDirectoryModel replaceSrcDir(@NotNull String oldSrcDir, @NotNull String newSrcDir) {
    myDslElement.replaceInExpressionList(SRC_DIRS, oldSrcDir, newSrcDir);
    return this;
  }
}
