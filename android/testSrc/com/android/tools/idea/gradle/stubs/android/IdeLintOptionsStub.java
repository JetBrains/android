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
package com.android.tools.idea.gradle.stubs.android;

import com.android.annotations.Nullable;
import com.android.builder.model.LintOptions;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Creates a version of {@link LintOptions} that does not cause unsupported exceptions, used for testing {@link IdeAndroidProject}.
 *
 */
public class IdeLintOptionsStub implements LintOptions {
  @NotNull private final static Set<String> myDisable = Collections.emptySet();
  @NotNull private final static Set<String> myEnable = Collections.emptySet();
  @Nullable private final static Set<String> myCheck = Collections.emptySet();
  @Nullable private final static File myLintConfig = null;
  @Nullable private final static File myTextOutput = null;
  @Nullable private final static File myHtmlOutput = null;
  @Nullable private final static File myXmlOutput = null ;
  @Nullable private final static File myBaselineFile = null ;
  @Nullable private final static Map<String,Integer> mySeverityOverrides = Collections.emptyMap();
  private final static boolean myAbortOnError = true;
  private final static boolean myAbsolutePaths = true;
  private final static boolean myNoLines = true;
  private final static boolean myQuiet = true;
  private final static boolean myCheckAllWarnings = true;
  private final static boolean myIgnoreWarnings = true;
  private final static boolean myWarningsAsErrors = true;
  private final static boolean myCheckTestSources = false;
  private final static boolean myExplainIssues = true;
  private final static boolean myShowAll = true;
  private final static boolean myTextReport = false;
  private final static boolean myHtmlReport = false;
  private final static boolean myXmlReport = false;
  private final static boolean myCheckReleaseBuilds = true;

  public IdeLintOptionsStub() {
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
}
