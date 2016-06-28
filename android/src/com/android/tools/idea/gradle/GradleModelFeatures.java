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
package com.android.tools.idea.gradle;

import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.util.GradleUtil.androidModelSupportsDependencyGraph;

public class GradleModelFeatures {
  @Nullable private final GradleVersion myModelVersion;

  private boolean myDependencyGraphSupported;
  private boolean myIssueReportingSupported;
  private boolean myShadersSupported;
  private boolean myTestedTargetVariantsSupported;
  private boolean myProductFlavorVersionSuffixSupported;
  private boolean myExternalBuildSupported;
  private boolean myConstraintLayoutSdkLocationSupported;

  GradleModelFeatures(@Nullable GradleVersion modelVersion) {
    myModelVersion = modelVersion;
    myDependencyGraphSupported = myModelVersion != null && androidModelSupportsDependencyGraph(myModelVersion);
    myIssueReportingSupported = modelVersionIsAtLeast("1.1.0");
    myShadersSupported = modelVersionIsAtLeast("2.1.0");
    myConstraintLayoutSdkLocationSupported = myModelVersion != null && myModelVersion.compareTo("2.1.0") > 0;
    myTestedTargetVariantsSupported = myProductFlavorVersionSuffixSupported = myExternalBuildSupported = modelVersionIsAtLeast("2.2.0");
  }

  private boolean modelVersionIsAtLeast(@NotNull String revision) {
    return myModelVersion != null && myModelVersion.compareIgnoringQualifiers(revision) >= 0;
  }

  public boolean isDependencyGraphSupported() {
    return myDependencyGraphSupported;
  }

  public boolean isIssueReportingSupported() {
    return myIssueReportingSupported;
  }

  public boolean isShadersSupported() {
    return myShadersSupported;
  }

  public boolean isTestedTargetVariantsSupported() {
    return myTestedTargetVariantsSupported;
  }

  public boolean isProductFlavorVersionSuffixSupported() {
    return myProductFlavorVersionSuffixSupported;
  }

  public boolean isExternalBuildSupported() {
    return myExternalBuildSupported;
  }

  public boolean isConstraintLayoutSdkLocationSupported() {
    return myConstraintLayoutSdkLocationSupported;
  }
}
