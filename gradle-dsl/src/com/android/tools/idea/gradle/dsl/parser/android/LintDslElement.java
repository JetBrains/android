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

import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.ABORT_ON_ERROR;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.ABSOLUTE_PATHS;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.BASELINE;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.CHECK_ALL_WARNINGS;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.CHECK_DEPENDENCIES;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.CHECK_GENERATED_SOURCES;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.CHECK_ONLY;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.CHECK_RELEASE_BUILDS;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.CHECK_TEST_SOURCES;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.DISABLE;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.ENABLE;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.ERROR;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.EXPLAIN_ISSUES;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.FATAL;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.HTML_OUTPUT;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.HTML_REPORT;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.IGNORE;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.IGNORE_TEST_SOURCES;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.IGNORE_WARNINGS;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.INFORMATIONAL;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.LINT_CONFIG;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.NO_LINES;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.QUIET;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.SARIF_OUTPUT;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.SARIF_REPORT;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.SHOW_ALL;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.TEXT_OUTPUT;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.TEXT_REPORT;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.WARNING;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.WARNINGS_AS_ERRORS;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.XML_OUTPUT;
import static com.android.tools.idea.gradle.dsl.model.android.LintModelImpl.XML_REPORT;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.atLeast;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.exactly;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ArityHelper.property;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.AUGMENT_LIST;
import static com.android.tools.idea.gradle.dsl.parser.semantics.MethodSemanticsDescription.SET;
import static com.android.tools.idea.gradle.dsl.parser.semantics.ModelMapCollector.toModelMap;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAL;
import static com.android.tools.idea.gradle.dsl.parser.semantics.PropertySemanticsDescription.VAR;

import com.android.tools.idea.gradle.dsl.parser.GradleDslNameConverter;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslBlockElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElementSchema;
import com.android.tools.idea.gradle.dsl.parser.semantics.ExternalToModelMap;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LintDslElement extends GradleDslBlockElement {
  public static final ExternalToModelMap ktsToModelNameMap = Stream.of(new Object[][]{
    {"abortOnError", property, ABORT_ON_ERROR, VAR},
    {"absolutePaths", property, ABSOLUTE_PATHS, VAR},
    {"checkAllWarnings", property, CHECK_ALL_WARNINGS, VAR},
    {"checkDependencies", property, CHECK_DEPENDENCIES, VAR},
    {"checkGeneratedSources", property, CHECK_GENERATED_SOURCES, VAR},
    {"checkReleaseBuilds", property, CHECK_RELEASE_BUILDS, VAR},
    {"checkTestSources", property, CHECK_TEST_SOURCES, VAR},
    {"explainIssues", property, EXPLAIN_ISSUES, VAR},
    {"ignoreTestSources", property, IGNORE_TEST_SOURCES, VAR},
    {"ignoreWarnings", property, IGNORE_WARNINGS, VAR},
    {"noLines", property, NO_LINES, VAR},
    {"quiet", property, QUIET, VAR},
    {"showAll", property, SHOW_ALL, VAR},
    {"warningsAsErrors", property, WARNINGS_AS_ERRORS, VAR},

    {"baselineFile", property, BASELINE, VAR},
    {"lintConfig", property, LINT_CONFIG, VAR},
    {"htmlOutput", property, HTML_OUTPUT, VAR},
    {"htmlReport", property, HTML_REPORT, VAR},
    {"sarifOutput", property, SARIF_OUTPUT, VAR},
    {"sarifReport", property, SARIF_REPORT, VAR},
    {"textOutput", property, TEXT_OUTPUT, VAR},
    {"textOutput", exactly(1), TEXT_OUTPUT, SET}, // special-case String method as well as File
    {"textReport", property, TEXT_REPORT, VAR},
    {"xmlOutput", property, XML_OUTPUT, VAR},
    {"xmlReport", property, XML_REPORT, VAR},

    {"checkOnly", property, CHECK_ONLY, VAL},
    {"disable", property, DISABLE, VAL},
    {"enable", property, ENABLE, VAL},
    {"error", property, ERROR, VAL},
    {"fatal", property, FATAL, VAL},
    {"ignore", property, IGNORE, VAL},
    {"informational", property, INFORMATIONAL, VAL},
    {"warning", property, WARNING, VAL},
  }).collect(toModelMap());

  public static final ExternalToModelMap groovyToModelNameMap = Stream.of(new Object[][]{
    {"abortOnError", property, ABORT_ON_ERROR, VAR},
    {"abortOnError", exactly(1), ABORT_ON_ERROR, SET},
    {"absolutePaths", property, ABSOLUTE_PATHS, VAR},
    {"absolutePaths", exactly(1), ABSOLUTE_PATHS, SET},
    {"checkAllWarnings", property, CHECK_ALL_WARNINGS, VAR},
    {"checkAllWarnings", exactly(1), CHECK_ALL_WARNINGS, SET},
    {"checkDependencies", property, CHECK_DEPENDENCIES, VAR},
    {"checkDependencies", exactly(1), CHECK_DEPENDENCIES, SET},
    {"checkGeneratedSources", property, CHECK_GENERATED_SOURCES, VAR},
    {"checkGeneratedSources", exactly(1), CHECK_GENERATED_SOURCES, SET},
    {"checkReleaseBuilds", property, CHECK_RELEASE_BUILDS, VAR},
    {"checkReleaseBuilds", exactly(1), CHECK_RELEASE_BUILDS, SET},
    {"checkTestSources", property, CHECK_TEST_SOURCES, VAR},
    {"checkTestSources", exactly(1), CHECK_TEST_SOURCES, SET},
    {"explainIssues", property, EXPLAIN_ISSUES, VAR},
    {"explainIssues", exactly(1), EXPLAIN_ISSUES, SET},
    {"ignoreTestSources", property, IGNORE_TEST_SOURCES, VAR},
    {"ignoreTestSources", exactly(1), IGNORE_TEST_SOURCES, SET},
    {"ignoreWarnings", property, IGNORE_WARNINGS, VAR},
    {"ignoreWarnings", exactly(1), IGNORE_WARNINGS, SET},
    {"noLines", property, NO_LINES, VAR},
    {"noLines", exactly(1), NO_LINES, SET},
    {"quiet", property, QUIET, VAR},
    {"quiet", exactly(1), QUIET, SET},
    {"showAll", property, SHOW_ALL, VAR},
    {"showAll", exactly(1), SHOW_ALL, SET},
    {"warningsAsErrors", property, WARNINGS_AS_ERRORS, VAR},
    {"warningsAsErrors", exactly(1), WARNINGS_AS_ERRORS, SET},

    {"baseline", exactly(1), BASELINE, SET},
    {"lintConfig", exactly(1), LINT_CONFIG, SET},
    {"htmlOutput", exactly(1), HTML_OUTPUT, SET},
    {"htmlReport", exactly(1), HTML_REPORT, SET},
    {"sarifOutput", exactly(1), SARIF_OUTPUT, SET},
    {"sarifReport", exactly(1), SARIF_REPORT, SET},
    {"textOutput", exactly(1), TEXT_OUTPUT, SET},
    {"textReport", exactly(1), TEXT_REPORT, SET},
    {"xmlOutput", exactly(1), XML_OUTPUT, SET},
    {"xmlReport", exactly(1), XML_REPORT, SET},

    // There are also exactly(1) variants of these functions with the same name, but they are redundant for our purposes.
    // Properties only exist in the interface for checkOnly, disable and enable.
    {"checkOnly", atLeast(0), CHECK_ONLY, AUGMENT_LIST},
    {"checkOnly", property, CHECK_ONLY, VAL},
    {"disable", atLeast(0), DISABLE, AUGMENT_LIST},
    {"disable", property, DISABLE, VAL},
    {"enable", atLeast(0), ENABLE, AUGMENT_LIST},
    {"enable", property, ENABLE, VAL},
    {"error", atLeast(0), ERROR, AUGMENT_LIST},
    {"error", property, ERROR, VAL},
    {"fatal", atLeast(0), FATAL, AUGMENT_LIST},
    {"fatal", property, FATAL, VAL},
    {"ignore", atLeast(0), IGNORE, AUGMENT_LIST},
    {"ignore", property, IGNORE, VAL},
    {"informational", atLeast(0), INFORMATIONAL, AUGMENT_LIST},
    {"informational", property, INFORMATIONAL, VAL},
    {"warning", atLeast(0), WARNING, AUGMENT_LIST},
    {"warning", property, WARNING, VAL},
  }).collect(toModelMap());

  public static final ExternalToModelMap declarativeToModelNameMap = Stream.of(new Object[][]{
    {"abortOnError", property, ABORT_ON_ERROR, VAR},
    {"absolutePaths", property, ABSOLUTE_PATHS, VAR},
    {"checkAllWarnings", property, CHECK_ALL_WARNINGS, VAR},
    {"checkDependencies", property, CHECK_DEPENDENCIES, VAR},
    {"checkGeneratedSources", property, CHECK_GENERATED_SOURCES, VAR},
    {"checkReleaseBuilds", property, CHECK_RELEASE_BUILDS, VAR},
    {"checkTestSources", property, CHECK_TEST_SOURCES, VAR},
    {"explainIssues", property, EXPLAIN_ISSUES, VAR},
    {"ignoreTestSources", property, IGNORE_TEST_SOURCES, VAR},
    {"ignoreWarnings", property, IGNORE_WARNINGS, VAR},
    {"noLines", property, NO_LINES, VAR},
    {"quiet", property, QUIET, VAR},
    {"showAll", property, SHOW_ALL, VAR},
    {"warningsAsErrors", property, WARNINGS_AS_ERRORS, VAR},

    {"baseline", property, BASELINE, VAR},
    {"lintConfig", property, LINT_CONFIG, VAR},
    {"htmlOutput", property, HTML_OUTPUT, VAR},
    {"htmlReport", property, HTML_REPORT, VAR},
    {"sarifOutput", property, SARIF_OUTPUT, VAR},
    {"sarifReport", property, SARIF_REPORT, VAR},
    {"textOutput", property, TEXT_OUTPUT, VAR},
    {"textReport", property, TEXT_REPORT, VAR},
    {"xmlOutput", property, XML_OUTPUT, VAR},
    {"xmlReport", property, XML_REPORT, VAR},

    {"checkOnly", atLeast(0), CHECK_ONLY, AUGMENT_LIST},
    {"disable", atLeast(0), DISABLE, AUGMENT_LIST},
    {"enable", atLeast(0), ENABLE, AUGMENT_LIST},
    {"error", atLeast(0), ERROR, AUGMENT_LIST},
    {"fatal", atLeast(0), FATAL, AUGMENT_LIST},
    {"ignore", atLeast(0), IGNORE, AUGMENT_LIST},
    {"informational", atLeast(0), INFORMATIONAL, AUGMENT_LIST},
    {"warning", atLeast(0), WARNING, AUGMENT_LIST},
  }).collect(toModelMap());

  public static final PropertiesElementDescription<LintDslElement> LINT =
    new PropertiesElementDescription<>("lint",
                                       LintDslElement.class,
                                       LintDslElement::new,
                                       LintDslElementSchema::new);

  @Override
  public @NotNull ExternalToModelMap getExternalToModelMap(@NotNull GradleDslNameConverter converter) {
    return getExternalToModelMap(converter, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
  }

  public LintDslElement(@NotNull GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, name);
  }

  public static final class LintDslElementSchema extends GradlePropertiesDslElementSchema {
    @Override
    @NotNull
    public ExternalToModelMap getPropertiesInfo(GradleDslNameConverter.Kind kind) {
      return getExternalProperties(kind, groovyToModelNameMap, ktsToModelNameMap, declarativeToModelNameMap);
    }

    @Nullable
    @Override
    public String getAgpDocClass() {
      return "com.android.build.api.dsl.Lint";
    }
  }
}
