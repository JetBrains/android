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

import com.android.tools.idea.gradle.dsl.api.android.LintOptionsModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValueImpl;
import com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class LintOptionsModelImpl extends GradleDslBlockModel implements LintOptionsModel {
  @NonNls private static final String ABORT_ON_ERROR = "abortOnError";
  @NonNls private static final String ABSOLUTE_PATHS = "absolutePaths";
  @NonNls private static final String CHECK = "check";
  @NonNls private static final String CHECK_ALL_WARNINGS = "checkAllWarnings";
  @NonNls private static final String CHECK_RELEASE_BUILDS = "checkReleaseBuilds";
  @NonNls private static final String DISABLE = "disable";
  @NonNls private static final String ENABLE = "enable";
  @NonNls private static final String ERROR = "error";
  @NonNls private static final String EXPLAIN_ISSUES = "explainIssues";
  @NonNls private static final String FATAL = "fatal";
  @NonNls private static final String HTML_OUTPUT = "htmlOutput";
  @NonNls private static final String HTML_REPORT = "htmlReport";
  @NonNls private static final String IGNORE = "ignore";
  @NonNls private static final String IGNORE_WARNINGS = "ignoreWarnings";
  @NonNls private static final String LINT_CONFIG = "lintConfig";
  @NonNls private static final String NO_LINES = "noLines";
  @NonNls private static final String QUIET = "quiet";
  @NonNls private static final String SHOW_ALL = "showAll";
  @NonNls private static final String TEXT_OUTPUT = "textOutput";
  @NonNls private static final String TEXT_REPORT = "textReport";
  @NonNls private static final String WARNING = "warning";
  @NonNls private static final String WARNINGS_AS_ERRORS = "warningsAsErrors";
  @NonNls private static final String XML_OUTPUT = "xmlOutput";
  @NonNls private static final String XML_REPORT = "xmlReport";

  public LintOptionsModelImpl(@NotNull LintOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> abortOnError() {
    return myDslElement.getLiteralProperty(ABORT_ON_ERROR, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setAbortOnError(boolean abortOnError) {
    myDslElement.setNewLiteral(ABORT_ON_ERROR, abortOnError);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeAbortOnError() {
    myDslElement.removeProperty(ABORT_ON_ERROR);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> absolutePaths() {
    return myDslElement.getLiteralProperty(ABSOLUTE_PATHS, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setAbsolutePaths(boolean absolutePaths) {
    myDslElement.setNewLiteral(ABSOLUTE_PATHS, absolutePaths);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeAbsolutePaths() {
    myDslElement.removeProperty(ABSOLUTE_PATHS);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> check() {
    return myDslElement.getListProperty(CHECK, String.class);
  }

  @Override
  @NotNull
  public LintOptionsModel addCheck(@NotNull String check) {
    myDslElement.addToNewLiteralList(CHECK, check);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeCheck(@NotNull String check) {
    myDslElement.removeFromExpressionList(CHECK, check);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeAllCheck() {
    myDslElement.removeProperty(CHECK);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel replaceCheck(@NotNull String oldCheck, @NotNull String newCheck) {
    myDslElement.replaceInExpressionList(CHECK, oldCheck, newCheck);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> checkAllWarnings() {
    return myDslElement.getLiteralProperty(CHECK_ALL_WARNINGS, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setCheckAllWarnings(boolean checkAllWarnings) {
    myDslElement.setNewLiteral(CHECK_ALL_WARNINGS, checkAllWarnings);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeCheckAllWarnings() {
    myDslElement.removeProperty(CHECK_ALL_WARNINGS);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> checkReleaseBuilds() {
    return myDslElement.getLiteralProperty(CHECK_RELEASE_BUILDS, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setCheckReleaseBuilds(boolean checkReleaseBuilds) {
    myDslElement.setNewLiteral(CHECK_RELEASE_BUILDS, checkReleaseBuilds);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeCheckReleaseBuilds() {
    myDslElement.removeProperty(CHECK_RELEASE_BUILDS);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> disable() {
    return myDslElement.getListProperty(DISABLE, String.class);
  }

  @Override
  @NotNull
  public LintOptionsModel addDisable(@NotNull String disable) {
    myDslElement.addToNewLiteralList(DISABLE, disable);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeDisable(@NotNull String disable) {
    myDslElement.removeFromExpressionList(DISABLE, disable);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeAllDisable() {
    myDslElement.removeProperty(DISABLE);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel replaceDisable(@NotNull String oldDisable, @NotNull String newDisable) {
    myDslElement.replaceInExpressionList(DISABLE, oldDisable, newDisable);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> enable() {
    return myDslElement.getListProperty(ENABLE, String.class);
  }

  @Override
  @NotNull
  public LintOptionsModel addEnable(@NotNull String enable) {
    myDslElement.addToNewLiteralList(ENABLE, enable);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeEnable(@NotNull String enable) {
    myDslElement.removeFromExpressionList(ENABLE, enable);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeAllEnable() {
    myDslElement.removeProperty(ENABLE);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel replaceEnable(@NotNull String oldEnable, @NotNull String newEnable) {
    myDslElement.replaceInExpressionList(ENABLE, oldEnable, newEnable);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> error() {
    return myDslElement.getListProperty(ERROR, String.class);
  }

  @Override
  @NotNull
  public LintOptionsModel addError(@NotNull String error) {
    myDslElement.addToNewLiteralList(ERROR, error);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeError(@NotNull String error) {
    myDslElement.removeFromExpressionList(ERROR, error);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeAllError() {
    myDslElement.removeProperty(ERROR);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel replaceError(@NotNull String oldError, @NotNull String newError) {
    myDslElement.replaceInExpressionList(ERROR, oldError, newError);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> explainIssues() {
    return myDslElement.getLiteralProperty(EXPLAIN_ISSUES, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setExplainIssues(boolean explainIssues) {
    myDslElement.setNewLiteral(EXPLAIN_ISSUES, explainIssues);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeExplainIssues() {
    myDslElement.removeProperty(EXPLAIN_ISSUES);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> fatal() {
    return myDslElement.getListProperty(FATAL, String.class);
  }

  @Override
  @NotNull
  public LintOptionsModel addFatal(@NotNull String fatal) {
    myDslElement.addToNewLiteralList(FATAL, fatal);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeFatal(@NotNull String fatal) {
    myDslElement.removeFromExpressionList(FATAL, fatal);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeAllFatal() {
    myDslElement.removeProperty(FATAL);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel replaceFatal(@NotNull String oldFatal, @NotNull String newFatal) {
    myDslElement.replaceInExpressionList(FATAL, oldFatal, newFatal);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<File> htmlOutput() {
    return getFileValue(HTML_OUTPUT);
  }

  @Override
  @NotNull
  public LintOptionsModel setHtmlOutput(@NotNull File htmlOutput) {
    setFileValue(HTML_OUTPUT, htmlOutput);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeHtmlOutput() {
    myDslElement.removeProperty(HTML_OUTPUT);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> htmlReport() {
    return myDslElement.getLiteralProperty(HTML_REPORT, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setHtmlReport(boolean htmlReport) {
    myDslElement.setNewLiteral(HTML_REPORT, htmlReport);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeHtmlReport() {
    myDslElement.removeProperty(HTML_REPORT);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> ignore() {
    return myDslElement.getListProperty(IGNORE, String.class);
  }

  @Override
  @NotNull
  public LintOptionsModel addIgnore(@NotNull String ignore) {
    myDslElement.addToNewLiteralList(IGNORE, ignore);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeIgnore(@NotNull String ignore) {
    myDslElement.removeFromExpressionList(IGNORE, ignore);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeAllIgnore() {
    myDslElement.removeProperty(IGNORE);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel replaceIgnore(@NotNull String oldIgnore, @NotNull String newIgnore) {
    myDslElement.replaceInExpressionList(IGNORE, oldIgnore, newIgnore);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> ignoreWarnings() {
    return myDslElement.getLiteralProperty(IGNORE_WARNINGS, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setIgnoreWarnings(boolean ignoreWarnings) {
    myDslElement.setNewLiteral(IGNORE_WARNINGS, ignoreWarnings);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeIgnoreWarnings() {
    myDslElement.removeProperty(IGNORE_WARNINGS);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<File> lintConfig() {
    return getFileValue(LINT_CONFIG);
  }

  @Override
  @NotNull
  public LintOptionsModel setLintConfig(@NotNull File lintConfig) {
    setFileValue(LINT_CONFIG, lintConfig);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeLintConfig() {
    myDslElement.removeProperty(LINT_CONFIG);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> noLines() {
    return myDslElement.getLiteralProperty(NO_LINES, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setNoLines(boolean noLines) {
    myDslElement.setNewLiteral(NO_LINES, noLines);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeNoLines() {
    myDslElement.removeProperty(NO_LINES);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> quiet() {
    return myDslElement.getLiteralProperty(QUIET, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setQuiet(boolean quiet) {
    myDslElement.setNewLiteral(QUIET, quiet);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeQuiet() {
    myDslElement.removeProperty(QUIET);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> showAll() {
    return myDslElement.getLiteralProperty(SHOW_ALL, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setShowAll(boolean showAll) {
    myDslElement.setNewLiteral(SHOW_ALL, showAll);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeShowAll() {
    myDslElement.removeProperty(SHOW_ALL);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<File> textOutput() {
    return getFileValue(TEXT_OUTPUT);
  }

  @Override
  @NotNull
  public LintOptionsModel setTextOutput(@NotNull File textOutput) {
    setFileValue(TEXT_OUTPUT, textOutput);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeTextOutput() {
    myDslElement.removeProperty(TEXT_OUTPUT);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> textReport() {
    return myDslElement.getLiteralProperty(TEXT_REPORT, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setTextReport(boolean textReport) {
    myDslElement.setNewLiteral(TEXT_REPORT, textReport);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeTextReport() {
    myDslElement.removeProperty(TEXT_REPORT);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> warning() {
    return myDslElement.getListProperty(WARNING, String.class);
  }

  @Override
  @NotNull
  public LintOptionsModel addWarning(@NotNull String warning) {
    myDslElement.addToNewLiteralList(WARNING, warning);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeWarning(@NotNull String warning) {
    myDslElement.removeFromExpressionList(WARNING, warning);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeAllWarning() {
    myDslElement.removeProperty(WARNING);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel replaceWarning(@NotNull String oldWarning, @NotNull String newWarning) {
    myDslElement.replaceInExpressionList(WARNING, oldWarning, newWarning);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> warningsAsErrors() {
    return myDslElement.getLiteralProperty(WARNINGS_AS_ERRORS, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setWarningsAsErrors(boolean warningsAsErrors) {
    myDslElement.setNewLiteral(WARNINGS_AS_ERRORS, warningsAsErrors);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeWarningsAsErrors() {
    myDslElement.removeProperty(WARNINGS_AS_ERRORS);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<File> xmlOutput() {
    return getFileValue(XML_OUTPUT);
  }

  @Override
  @NotNull
  public LintOptionsModel setXmlOutput(@NotNull File xmlOutput) {
    setFileValue(XML_OUTPUT, xmlOutput);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeXmlOutput() {
    myDslElement.removeProperty(XML_OUTPUT);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> xmlReport() {
    return myDslElement.getLiteralProperty(XML_REPORT, Boolean.class);
  }

  @Override
  @NotNull
  public LintOptionsModel setXmlReport(boolean xmlReport) {
    myDslElement.setNewLiteral(XML_REPORT, xmlReport);
    return this;
  }

  @Override
  @NotNull
  public LintOptionsModel removeXmlReport() {
    myDslElement.removeProperty(XML_REPORT);
    return this;
  }

  @NotNull
  private GradleNullableValue<File> getFileValue(@NotNull String property) {
    GradleDslMethodCall fileElement = myDslElement.getPropertyElement(property, GradleDslMethodCall.class);
    if (fileElement == null) {
      return new GradleNullableValueImpl<>(myDslElement, null);
    }
    return new GradleNullableValueImpl<>(fileElement, fileElement.getValue(File.class));
  }

  private void setFileValue(@NotNull String property, @NotNull File file) {
    GradleDslMethodCall fileElement = myDslElement.getPropertyElement(property, GradleDslMethodCall.class);
    if (fileElement == null) {
      fileElement = new GradleDslMethodCall(myDslElement, GradleNameElement.create(property), "file");
      myDslElement.setNewElement(property, fileElement);
    }
    fileElement.setValue(file);
  }
}
