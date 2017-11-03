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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.LintOptions;
import com.android.ide.common.repository.GradleVersion;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Creates a deep copy of a {@link LintOptions}.
 */
public final class IdeLintOptions extends IdeModel implements LintOptions {
  // Increase the value when adding/removing fields or when changing the serialization/deserialization mechanism.
  private static final long serialVersionUID = 1L;

  @Nullable private final File myBaselineFile;
  @Nullable private final Map<String, Integer> mySeverityOverrides;
  private final boolean myCheckTestSources;
  private final int myHashCode;

  public IdeLintOptions(@NotNull LintOptions options, @NotNull ModelCache modelCache, @Nullable GradleVersion modelVersion) {
    super(options, modelCache);
    myBaselineFile = modelVersion != null && modelVersion.isAtLeast(2, 3, 0, "beta", 2, true) ? options.getBaselineFile() : null;
    mySeverityOverrides = copy(options.getSeverityOverrides());
    myCheckTestSources = modelVersion != null && modelVersion.isAtLeast(2, 4, 0) && options.isCheckTestSources();

    myHashCode = calculateHashCode();
  }

  @Nullable
  private static Map<String, Integer> copy(@Nullable Map<String, Integer> original) {
    return original != null ? ImmutableMap.copyOf(original) : null;
  }

  @Override
  @NotNull
  public Set<String> getDisable() {
    throw new UnusedModelMethodException("getDisable");
  }

  @Override
  @NotNull
  public Set<String> getEnable() {
    throw new UnusedModelMethodException("getEnable");
  }

  @Override
  @Nullable
  public Set<String> getCheck() {
    throw new UnusedModelMethodException("getCheck");
  }

  @Override
  @Nullable
  public File getLintConfig() {
    throw new UnusedModelMethodException("getLintConfig");
  }

  @Override
  @Nullable
  public File getTextOutput() {
    throw new UnusedModelMethodException("getTextOutput");
  }

  @Override
  @Nullable
  public File getHtmlOutput() {
    throw new UnusedModelMethodException("getHtmlOutput");
  }

  @Override
  @Nullable
  public File getXmlOutput() {
    throw new UnusedModelMethodException("getXmlOutput");
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
    throw new UnusedModelMethodException("isAbortOnError");
  }

  @Override
  public boolean isAbsolutePaths() {
    throw new UnusedModelMethodException("isAbsolutePaths");
  }

  @Override
  public boolean isNoLines() {
    throw new UnusedModelMethodException("isNoLines");
  }

  @Override
  public boolean isQuiet() {
    throw new UnusedModelMethodException("isQuiet");
  }

  @Override
  public boolean isCheckAllWarnings() {
    throw new UnusedModelMethodException("isCheckAllWarnings");
  }

  @Override
  public boolean isIgnoreWarnings() {
    throw new UnusedModelMethodException("isIgnoreWarnings");
  }

  @Override
  public boolean isWarningsAsErrors() {
    throw new UnusedModelMethodException("isWarningsAsErrors");
  }

  @Override
  public boolean isCheckTestSources() {
    return myCheckTestSources;
  }

  @Override
  public boolean isCheckGeneratedSources() {
    throw new UnusedModelMethodException("isCheckGeneratedSources");
  }

  @Override
  public boolean isExplainIssues() {
    throw new UnusedModelMethodException("isExplainIssues");
  }

  @Override
  public boolean isShowAll() {
    throw new UnusedModelMethodException("isShowAll");
  }

  @Override
  public boolean getTextReport() {
    throw new UnusedModelMethodException("getTextReport");
  }

  @Override
  public boolean getHtmlReport() {
    throw new UnusedModelMethodException("getHtmlReport");
  }

  @Override
  public boolean getXmlReport() {
    throw new UnusedModelMethodException("getXmlReport");
  }

  @Override
  public boolean isCheckReleaseBuilds() {
    throw new UnusedModelMethodException("isCheckReleaseBuilds");
  }

  @Override
  public boolean isCheckDependencies() {
    throw new UnusedModelMethodException("isCheckDependencies");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IdeLintOptions)) {
      return false;
    }
    IdeLintOptions options = (IdeLintOptions)o;
    return myCheckTestSources == options.myCheckTestSources &&
           Objects.equals(myBaselineFile, options.myBaselineFile) &&
           Objects.equals(mySeverityOverrides, options.mySeverityOverrides);
  }

  @Override
  public int hashCode() {
    return myHashCode;
  }

  private int calculateHashCode() {
    return Objects.hash(myBaselineFile, mySeverityOverrides, myCheckTestSources);
  }

  @Override
  public String toString() {
    return "IdeLintOptions{" +
           "myBaselineFile=" + myBaselineFile +
           ", mySeverityOverrides=" + mySeverityOverrides +
           ", myCheckTestSources=" + myCheckTestSources +
           "}";
  }
}
