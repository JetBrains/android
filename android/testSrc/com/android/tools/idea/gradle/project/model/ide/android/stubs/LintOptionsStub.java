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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.LintOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class LintOptionsStub extends BaseStub implements LintOptions {
  @NotNull private final Set<String> myDisable;
  @NotNull private final Set<String> myEnable;
  @Nullable private final Set<String> myCheck;
  @Nullable private final File myLintConfig;
  @Nullable private final File myTextOutput;
  @Nullable private final File myHtmlOutput;
  @Nullable private final File myXmlOutput;
  @Nullable private final File myBaselineFile;
  @Nullable private final Map<String,Integer> mySeverityOverrides;
  private final boolean myAbortOnError;
  private final boolean myAbsolutePaths;
  private final boolean myNoLines;
  private final boolean myQuiet;
  private final boolean myCheckAllWarnings;
  private final boolean myIgnoreWarnings;
  private final boolean myWarningsAsErrors;
  private final boolean myCheckTestSources;
  private final boolean myCheckGeneratedSources;
  private final boolean myExplainIssues;
  private final boolean myShowAll;
  private final boolean myTextReport;
  private final boolean myHtmlReport;
  private final boolean myXmlReport;
  private final boolean myCheckReleaseBuilds;
  private final boolean myCheckDependencies;

  public LintOptionsStub() {
    this(Sets.newHashSet("disable"), Sets.newHashSet("enable"), Sets.newHashSet("check"), new File("lintConfig"), new File("textOutput"),
         new File("htmlOutput"), new File("xmlOutput"), new File("baselineFile"), ImmutableMap.of("key", 1), true, true, true, true, true,
         true, true, true, true, true, true, true, true, true, true, false);
  }

  public LintOptionsStub(@NotNull Set<String> disable,
                         @NotNull Set<String> enable,
                         @Nullable Set<String> check,
                         @Nullable File lintConfig,
                         @Nullable File textOutput,
                         @Nullable File htmlOutput,
                         @Nullable File xmlOutput,
                         @Nullable File baselineFile,
                         @Nullable Map<String, Integer> overrides,
                         boolean abortOnError,
                         boolean absolutePaths,
                         boolean noLines,
                         boolean quiet,
                         boolean checkAllWarnings,
                         boolean ignoreWarnings,
                         boolean warningsAsErrors,
                         boolean checkTestSources,
                         boolean checkGeneratedSources,
                         boolean explainIssues,
                         boolean showAll,
                         boolean textReport,
                         boolean htmlReport,
                         boolean xmlReport,
                         boolean checkReleaseBuilds,
                         boolean checkDependencies) {
    myDisable = disable;
    myEnable = enable;
    myCheck = check;
    myLintConfig = lintConfig;
    myTextOutput = textOutput;
    myHtmlOutput = htmlOutput;
    myXmlOutput = xmlOutput;
    myBaselineFile = baselineFile;
    mySeverityOverrides = overrides;
    myAbortOnError = abortOnError;

    myAbsolutePaths = absolutePaths;
    myNoLines = noLines;
    myQuiet = quiet;
    myCheckAllWarnings = checkAllWarnings;
    myIgnoreWarnings = ignoreWarnings;
    myWarningsAsErrors = warningsAsErrors;
    myCheckTestSources = checkTestSources;
    myCheckGeneratedSources = checkGeneratedSources;
    myExplainIssues = explainIssues;
    myShowAll = showAll;
    myTextReport = textReport;
    myHtmlReport = htmlReport;
    myXmlReport = xmlReport;
    myCheckReleaseBuilds = checkReleaseBuilds;
    myCheckDependencies = checkDependencies;
  }


  @Override
  @NotNull
  public Set<String> getDisable() {
    return myDisable;
  }

  @Override
  @NotNull
  public Set<String> getEnable() {
    return myEnable;
  }

  @Override
  @Nullable
  public Set<String> getCheck() {
    return myCheck;
  }

  @Override
  @Nullable
  public File getLintConfig() {
    return myLintConfig;
  }

  @Override
  @Nullable
  public File getTextOutput() {
    return myTextOutput;
  }

  @Override
  @Nullable
  public File getHtmlOutput() {
    return myHtmlOutput;
  }

  @Override
  @Nullable
  public File getXmlOutput() {
    return myXmlOutput;
  }

  @Override
  @Nullable
  public File getBaselineFile() {
    return myBaselineFile;
  }

  @Override
  @Nullable
  public Map<String, Integer> getSeverityOverrides() {
    return mySeverityOverrides;
  }

  @Override
  public boolean isAbortOnError() {
    return myAbortOnError;
  }

  @Override
  public boolean isAbsolutePaths() {
    return myAbsolutePaths;
  }

  @Override
  public boolean isNoLines() {
    return myNoLines;
  }

  @Override
  public boolean isQuiet() {
    return myQuiet;
  }

  @Override
  public boolean isCheckAllWarnings() {
    return myCheckAllWarnings;
  }

  @Override
  public boolean isIgnoreWarnings() {
    return myIgnoreWarnings;
  }

  @Override
  public boolean isWarningsAsErrors() {
    return myWarningsAsErrors;
  }

  @Override
  public boolean isCheckTestSources() {
    return myCheckTestSources;
  }

  @Override
  public boolean isCheckGeneratedSources() {
    return myCheckGeneratedSources;
  }

  @Override
  public boolean isExplainIssues() {
    return myExplainIssues;
  }

  @Override
  public boolean isShowAll() {
    return myShowAll;
  }

  @Override
  public boolean getTextReport() {
    return myTextReport;
  }

  @Override
  public boolean getHtmlReport() {
    return myHtmlReport;
  }

  @Override
  public boolean getXmlReport() {
    return myXmlReport;
  }

  @Override
  public boolean isCheckReleaseBuilds() {
    return myCheckReleaseBuilds;
  }

  @Override
  public boolean isCheckDependencies() {
    return myCheckDependencies;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LintOptions)) {
      return false;
    }
    LintOptions options = (LintOptions)o;
    return equals(options, LintOptions::isAbortOnError) &&
           equals(options, LintOptions::isAbsolutePaths) &&
           equals(options, LintOptions::isNoLines) &&
           equals(options, LintOptions::isQuiet) &&
           equals(options, LintOptions::isCheckAllWarnings) &&
           equals(options, LintOptions::isIgnoreWarnings) &&
           equals(options, LintOptions::isWarningsAsErrors) &&
           equals(options, LintOptions::isCheckGeneratedSources) &&
           equals(options, LintOptions::isExplainIssues) &&
           equals(options, LintOptions::isShowAll) &&
           equals(options, LintOptions::getTextReport) &&
           equals(options, LintOptions::getHtmlReport) &&
           equals(options, LintOptions::getXmlReport) &&
           equals(options, LintOptions::isCheckReleaseBuilds) &&
           equals(options, LintOptions::isCheckDependencies) &&
           equals(options, LintOptions::getDisable) &&
           equals(options, LintOptions::getEnable) &&
           equals(options, LintOptions::getCheck) &&
           equals(options, LintOptions::getLintConfig) &&
           equals(options, LintOptions::getTextOutput) &&
           equals(options, LintOptions::getHtmlOutput) &&
           equals(options, LintOptions::getXmlOutput) &&
           equals(options, LintOptions::getBaselineFile) &&
           equals(options, LintOptions::getSeverityOverrides);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getDisable(), getEnable(), getCheck(), getLintConfig(), getTextOutput(), getHtmlOutput(), getXmlOutput(),
                        getBaselineFile(), getSeverityOverrides(), isAbortOnError(), isAbsolutePaths(), isNoLines(), isQuiet(),
                        isCheckAllWarnings(), isIgnoreWarnings(), isWarningsAsErrors(), isCheckTestSources(), isCheckGeneratedSources(),
                        isExplainIssues(), isShowAll(), getTextReport(), getHtmlReport(), getXmlReport(), isCheckReleaseBuilds(),
                        isCheckDependencies());
  }

  @Override
  public String toString() {
    return "LintOptionsStub{" +
           "myDisable=" + myDisable +
           ", myEnable=" + myEnable +
           ", myCheck=" + myCheck +
           ", myLintConfig=" + myLintConfig +
           ", myTextOutput=" + myTextOutput +
           ", myHtmlOutput=" + myHtmlOutput +
           ", myXmlOutput=" + myXmlOutput +
           ", myBaselineFile=" + myBaselineFile +
           ", mySeverityOverrides=" + mySeverityOverrides +
           ", myAbortOnError=" + myAbortOnError +
           ", myAbsolutePaths=" + myAbsolutePaths +
           ", myNoLines=" + myNoLines +
           ", myQuiet=" + myQuiet +
           ", myCheckAllWarnings=" + myCheckAllWarnings +
           ", myIgnoreWarnings=" + myIgnoreWarnings +
           ", myWarningsAsErrors=" + myWarningsAsErrors +
           ", myCheckTestSources=" + myCheckTestSources +
           ", myCheckGeneratedSources=" + myCheckGeneratedSources +
           ", myExplainIssues=" + myExplainIssues +
           ", myShowAll=" + myShowAll +
           ", myTextReport=" + myTextReport +
           ", myHtmlReport=" + myHtmlReport +
           ", myXmlReport=" + myXmlReport +
           ", myCheckReleaseBuilds=" + myCheckReleaseBuilds +
           ", myCheckDependencies=" + myCheckDependencies +
           '}';
  }
}
