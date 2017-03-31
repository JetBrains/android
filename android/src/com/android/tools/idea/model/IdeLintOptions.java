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
package com.android.tools.idea.model;

import com.android.annotations.Nullable;
import com.android.builder.model.LintOptions;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * Creates a deep copy of {@link LintOptions}.
 *
 * @see IdeAndroidProject
 */
public class IdeLintOptions implements LintOptions, Serializable {
  @NotNull private final GradleVersion myGradleVersion;
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
  private final boolean myExplainIssues;
  private final boolean myShowAll;
  private final boolean myTextReport;
  private final boolean myHtmlReport;
  private final boolean myXmlReport;
  private final boolean myCheckReleaseBuilds;

  public IdeLintOptions(@NotNull LintOptions options, @NotNull GradleVersion gradleVersion) {
    myGradleVersion = gradleVersion;

    myDisable = new HashSet<>(options.getDisable());
    myEnable = new HashSet<>(options.getEnable());

    Set<String> opCheck = options.getCheck();
    myCheck = opCheck == null ? null : new HashSet<>(opCheck);

    myLintConfig = options.getLintConfig();
    myTextOutput = options.getTextOutput();
    myHtmlOutput = options.getHtmlOutput();
    myXmlOutput = options.getXmlOutput();

    if (myGradleVersion.isAtLeast(2, 3, 0, "beta", 2, true)) {
      myBaselineFile = options.getBaselineFile();
    }
    else {
      myBaselineFile = null;
    }

    Map<String, Integer> opSeverityOverrides = options.getSeverityOverrides();
    mySeverityOverrides = opSeverityOverrides == null ? null : new HashMap<>(opSeverityOverrides);

    myAbortOnError = options.isAbortOnError();
    myAbsolutePaths = options.isAbsolutePaths();
    myNoLines = options.isNoLines();
    myQuiet = options.isQuiet();
    myCheckAllWarnings = options.isCheckAllWarnings();
    myIgnoreWarnings = options.isIgnoreWarnings();
    myWarningsAsErrors = options.isWarningsAsErrors();

    if (myGradleVersion.isAtLeast(2,4,0)) {
      myCheckTestSources = options.isCheckTestSources();
    }
    else {
      myCheckTestSources = false;
    }

    myExplainIssues = options.isExplainIssues();
    myShowAll = options.isShowAll();
    myTextReport = options.getTextReport();
    myHtmlReport = options.getHtmlReport();
    myXmlReport = options.getXmlReport();
    myCheckReleaseBuilds = options.isCheckReleaseBuilds();
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LintOptions)) return false;
    LintOptions options = (LintOptions)o;
    return isAbortOnError() == options.isAbortOnError() &&
           isAbsolutePaths() == options.isAbsolutePaths() &&
           isNoLines() == options.isNoLines() &&
           isQuiet() == options.isQuiet() &&
           isCheckAllWarnings() == options.isCheckAllWarnings() &&
           isIgnoreWarnings() == options.isIgnoreWarnings() &&
           isWarningsAsErrors() == options.isWarningsAsErrors() &&
           isCheckTestSources() == options.isCheckTestSources() &&
           isExplainIssues() == options.isExplainIssues() &&
           isShowAll() == options.isShowAll() &&
           getTextReport() == options.getTextReport() &&
           getHtmlReport() == options.getHtmlReport() &&
           getXmlReport() == options.getXmlReport() &&
           isCheckReleaseBuilds() == options.isCheckReleaseBuilds() &&
           Objects.equals(getDisable(), options.getDisable()) &&
           Objects.equals(getEnable(), options.getEnable()) &&
           Objects.equals(getCheck(), options.getCheck()) &&
           Objects.equals(getLintConfig(), options.getLintConfig()) &&
           Objects.equals(getTextOutput(), options.getTextOutput()) &&
           Objects.equals(getHtmlOutput(), options.getHtmlOutput()) &&
           Objects.equals(getXmlOutput(), options.getXmlOutput()) &&
           Objects.equals(getBaselineFile(), options.getBaselineFile()) &&
           Objects.equals(getSeverityOverrides(), options.getSeverityOverrides());
  }

}
