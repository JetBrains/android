/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.python.sdk;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.PyIdeInfo.PythonVersion;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.python.sync.PySdkSuggester;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.sdk.PyDetectedSdk;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/** A PySdkSuggester that returns the most recent system interpreter for a given Python version. */
public final class FallbackPythonSdkSuggester extends PySdkSuggester {

  /** Initializes SDK information on first blaze project open */
  static class SdkInitializer implements StartupActivity, DumbAware {
    @Override
    public void runActivity(Project project) {
      if (!Blaze.isBlazeProject(project)) {
        return;
      }
      @SuppressWarnings("unused")
      ImmutableMap<PythonVersion, String> ignored = sdks.get();
    }
  }

  private static final Supplier<ImmutableMap<PythonVersion, String>> sdks =
      Suppliers.memoize(FallbackPythonSdkSuggester::findSystemSdks);

  /** Finds system interpreters, and parses version information. Must be run off the EDT. */
  private static ImmutableMap<PythonVersion, String> findSystemSdks() {
    ImmutableMap.Builder<PythonVersion, String> builder = ImmutableMap.builder();
    List<PyDetectedSdk> detectedSdks = PySdkExtKt.detectSystemWideSdks(null, ImmutableList.of());
    detectedSdks.stream()
        .filter(sdk -> sdk.getHomePath() != null && getSdkLanguageLevel(sdk).isPython2())
        .max(
            Comparator.comparing(
                FallbackPythonSdkSuggester::getSdkLanguageLevel,
                Comparator.comparingInt(LanguageLevel::getMajorVersion)
                    .thenComparingInt(LanguageLevel::getMinorVersion)))
        .ifPresent((sdk) -> builder.put(PythonVersion.PY2, sdk.getHomePath()));
    detectedSdks.stream()
        .filter(sdk -> sdk.getHomePath() != null && getSdkLanguageLevel(sdk).isPy3K())
        .max(
            Comparator.comparing(
                FallbackPythonSdkSuggester::getSdkLanguageLevel,
                Comparator.comparingInt(LanguageLevel::getMajorVersion)
                    .thenComparingInt(LanguageLevel::getMinorVersion)))
        .ifPresent((sdk) -> builder.put(PythonVersion.PY3, sdk.getHomePath()));
    return builder.build();
  }

  // PyDetectedSdk does not have a proper version/language level, so go via PythonSdkFlavor
  private static LanguageLevel getSdkLanguageLevel(PyDetectedSdk sdk) {
    String sdkHomepath = sdk.getHomePath();
    PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHomepath);
    if (flavor == null) {
      return LanguageLevel.getDefault();
    }
    return flavor.getLanguageLevel(sdkHomepath);
  }

  @Nullable
  @Override
  protected String suggestPythonHomePath(Project project, PythonVersion version) {
    if (!Blaze.isBlazeProject(project)) {
      return null;
    }

    ImmutableMap<PythonVersion, String> sdkMap = sdks.get();

    String homePath = sdkMap.get(version);
    if (!FileUtil.exists(homePath)) {
      return null;
    }

    return homePath;
  }

  @Override
  public boolean isDeprecatedSdk(Sdk sdk) {
    return false;
  }
}
