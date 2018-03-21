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

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public interface LintOptionsModel {
  @NotNull
  GradleNullableValue<Boolean> abortOnError();

  @NotNull
  LintOptionsModel setAbortOnError(boolean abortOnError);

  @NotNull
  LintOptionsModel removeAbortOnError();

  @NotNull
  GradleNullableValue<Boolean> absolutePaths();

  @NotNull
  LintOptionsModel setAbsolutePaths(boolean absolutePaths);

  @NotNull
  LintOptionsModel removeAbsolutePaths();

  @Nullable
  List<GradleNotNullValue<String>> check();

  @NotNull
  LintOptionsModel addCheck(@NotNull String check);

  @NotNull
  LintOptionsModel removeCheck(@NotNull String check);

  @NotNull
  LintOptionsModel removeAllCheck();

  @NotNull
  LintOptionsModel replaceCheck(@NotNull String oldCheck, @NotNull String newCheck);

  @NotNull
  GradleNullableValue<Boolean> checkAllWarnings();

  @NotNull
  LintOptionsModel setCheckAllWarnings(boolean checkAllWarnings);

  @NotNull
  LintOptionsModel removeCheckAllWarnings();

  @NotNull
  GradleNullableValue<Boolean> checkReleaseBuilds();

  @NotNull
  LintOptionsModel setCheckReleaseBuilds(boolean checkReleaseBuilds);

  @NotNull
  LintOptionsModel removeCheckReleaseBuilds();

  @Nullable
  List<GradleNotNullValue<String>> disable();

  @NotNull
  LintOptionsModel addDisable(@NotNull String disable);

  @NotNull
  LintOptionsModel removeDisable(@NotNull String disable);

  @NotNull
  LintOptionsModel removeAllDisable();

  @NotNull
  LintOptionsModel replaceDisable(@NotNull String oldDisable, @NotNull String newDisable);

  @Nullable
  List<GradleNotNullValue<String>> enable();

  @NotNull
  LintOptionsModel addEnable(@NotNull String enable);

  @NotNull
  LintOptionsModel removeEnable(@NotNull String enable);

  @NotNull
  LintOptionsModel removeAllEnable();

  @NotNull
  LintOptionsModel replaceEnable(@NotNull String oldEnable, @NotNull String newEnable);

  @Nullable
  List<GradleNotNullValue<String>> error();

  @NotNull
  LintOptionsModel addError(@NotNull String error);

  @NotNull
  LintOptionsModel removeError(@NotNull String error);

  @NotNull
  LintOptionsModel removeAllError();

  @NotNull
  LintOptionsModel replaceError(@NotNull String oldError, @NotNull String newError);

  @NotNull
  GradleNullableValue<Boolean> explainIssues();

  @NotNull
  LintOptionsModel setExplainIssues(boolean explainIssues);

  @NotNull
  LintOptionsModel removeExplainIssues();

  @Nullable
  List<GradleNotNullValue<String>> fatal();

  @NotNull
  LintOptionsModel addFatal(@NotNull String fatal);

  @NotNull
  LintOptionsModel removeFatal(@NotNull String fatal);

  @NotNull
  LintOptionsModel removeAllFatal();

  @NotNull
  LintOptionsModel replaceFatal(@NotNull String oldFatal, @NotNull String newFatal);

  @NotNull
  GradleNullableValue<File> htmlOutput();

  @NotNull
  LintOptionsModel setHtmlOutput(@NotNull File htmlOutput);

  @NotNull
  LintOptionsModel removeHtmlOutput();

  @NotNull
  GradleNullableValue<Boolean> htmlReport();

  @NotNull
  LintOptionsModel setHtmlReport(boolean htmlReport);

  @NotNull
  LintOptionsModel removeHtmlReport();

  @Nullable
  List<GradleNotNullValue<String>> ignore();

  @NotNull
  LintOptionsModel addIgnore(@NotNull String ignore);

  @NotNull
  LintOptionsModel removeIgnore(@NotNull String ignore);

  @NotNull
  LintOptionsModel removeAllIgnore();

  @NotNull
  LintOptionsModel replaceIgnore(@NotNull String oldIgnore, @NotNull String newIgnore);

  @NotNull
  GradleNullableValue<Boolean> ignoreWarnings();

  @NotNull
  LintOptionsModel setIgnoreWarnings(boolean ignoreWarnings);

  @NotNull
  LintOptionsModel removeIgnoreWarnings();

  @NotNull
  GradleNullableValue<File> lintConfig();

  @NotNull
  LintOptionsModel setLintConfig(@NotNull File lintConfig);

  @NotNull
  LintOptionsModel removeLintConfig();

  @NotNull
  GradleNullableValue<Boolean> noLines();

  @NotNull
  LintOptionsModel setNoLines(boolean noLines);

  @NotNull
  LintOptionsModel removeNoLines();

  @NotNull
  GradleNullableValue<Boolean> quiet();

  @NotNull
  LintOptionsModel setQuiet(boolean quiet);

  @NotNull
  LintOptionsModel removeQuiet();

  @NotNull
  GradleNullableValue<Boolean> showAll();

  @NotNull
  LintOptionsModel setShowAll(boolean showAll);

  @NotNull
  LintOptionsModel removeShowAll();

  @NotNull
  GradleNullableValue<File> textOutput();

  @NotNull
  LintOptionsModel setTextOutput(@NotNull File textOutput);

  @NotNull
  LintOptionsModel removeTextOutput();

  @NotNull
  GradleNullableValue<Boolean> textReport();

  @NotNull
  LintOptionsModel setTextReport(boolean textReport);

  @NotNull
  LintOptionsModel removeTextReport();

  @Nullable
  List<GradleNotNullValue<String>> warning();

  @NotNull
  LintOptionsModel addWarning(@NotNull String warning);

  @NotNull
  LintOptionsModel removeWarning(@NotNull String warning);

  @NotNull
  LintOptionsModel removeAllWarning();

  @NotNull
  LintOptionsModel replaceWarning(@NotNull String oldWarning, @NotNull String newWarning);

  @NotNull
  GradleNullableValue<Boolean> warningsAsErrors();

  @NotNull
  LintOptionsModel setWarningsAsErrors(boolean warningsAsErrors);

  @NotNull
  LintOptionsModel removeWarningsAsErrors();

  @NotNull
  GradleNullableValue<File> xmlOutput();

  @NotNull
  LintOptionsModel setXmlOutput(@NotNull File xmlOutput);

  @NotNull
  LintOptionsModel removeXmlOutput();

  @NotNull
  GradleNullableValue<Boolean> xmlReport();

  @NotNull
  LintOptionsModel setXmlReport(boolean xmlReport);

  @NotNull
  LintOptionsModel removeXmlReport();
}
