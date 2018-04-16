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
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  public ResolvedPropertyModel abortOnError() {
    return getModelForProperty(ABORT_ON_ERROR, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel absolutePaths() {
    return getModelForProperty(ABSOLUTE_PATHS, true);
  }

  @Override
  @Nullable
  public ResolvedPropertyModel check() {
    return getModelForProperty(CHECK, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel checkAllWarnings() {
    return getModelForProperty(CHECK_ALL_WARNINGS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel checkReleaseBuilds() {
    return getModelForProperty(CHECK_RELEASE_BUILDS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel disable() {
    return getModelForProperty(DISABLE, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel enable() {
    return getModelForProperty(ENABLE, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel error() {
    return getModelForProperty(ERROR, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel explainIssues() {
    return getModelForProperty(EXPLAIN_ISSUES, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel fatal() {
    return getModelForProperty(FATAL, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel htmlOutput() {
    return getFileModelForProperty(HTML_OUTPUT, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel htmlReport() {
    return getModelForProperty(HTML_REPORT, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel ignore() {
    return getModelForProperty(IGNORE, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel ignoreWarnings() {
    return getModelForProperty(IGNORE_WARNINGS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel lintConfig() {
    return getFileModelForProperty(LINT_CONFIG, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel noLines() {
    return getModelForProperty(NO_LINES, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel quiet() {
    return getModelForProperty(QUIET, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel showAll() {
    return getModelForProperty(SHOW_ALL, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel textOutput() {
    return getFileModelForProperty(TEXT_OUTPUT, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel textReport() {
    return getModelForProperty(TEXT_REPORT, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel warning() {
    return getModelForProperty(WARNING, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel warningsAsErrors() {
    return getModelForProperty(WARNINGS_AS_ERRORS, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel xmlOutput() {
    return getFileModelForProperty(XML_OUTPUT, true);
  }

  @Override
  @NotNull
  public ResolvedPropertyModel xmlReport() {
    return getModelForProperty(XML_REPORT, true);
  }
}
