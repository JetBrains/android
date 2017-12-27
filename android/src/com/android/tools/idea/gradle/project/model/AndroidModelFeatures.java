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
package com.android.tools.idea.gradle.project.model;

import com.android.annotations.VisibleForTesting;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidModelFeatures {
  @Nullable private final GradleVersion myModelVersion;

  private final boolean myIssueReportingSupported;
  private final boolean myShadersSupported;
  private final boolean myTestedTargetVariantsSupported;
  private final boolean myProductFlavorVersionSuffixSupported;
  private final boolean myExternalBuildSupported;
  private final boolean myConstraintLayoutSdkLocationSupported;
  private final boolean myLayoutRenderingIssuePresent;
  private final boolean myPostBuildSyncSupported;
  // Should current module export its module/library dependencies.
  private final boolean myExportDependencies;

  @VisibleForTesting
  public AndroidModelFeatures(@Nullable GradleVersion modelVersion) {
    myModelVersion = modelVersion;
    myIssueReportingSupported = modelVersionIsAtLeast("1.1.0");
    myShadersSupported = modelVersionIsAtLeast("2.1.0");
    myConstraintLayoutSdkLocationSupported = myModelVersion != null && myModelVersion.compareTo("2.1.0") > 0;
    myTestedTargetVariantsSupported = myProductFlavorVersionSuffixSupported = myExternalBuildSupported = modelVersionIsAtLeast("2.2.0");

    // https://code.google.com/p/android/issues/detail?id=170841
    if (modelVersion != null) {
      myLayoutRenderingIssuePresent = modelVersion.getMajor() == 1 && modelVersion.getMinor() == 2 && modelVersion.getMicro() <= 2;
      myPostBuildSyncSupported = modelVersion.isAtLeast(2, 4, 0, "alpha", 8, false);
    }
    else {
      myLayoutRenderingIssuePresent = false;
      myPostBuildSyncSupported = false;
    }
    // Dependencies exported should be true if android plugin version is less than 3.0.
    // Although with IdeDependencies, each module manages their own list of dependencies, this list is not
    // complete for pre-3.0 plugins, where java library dependencies of dependent module are always empty.
    // See b/64521930.
    myExportDependencies = !modelVersionIsAtLeast("3.0.0");
  }

  private boolean modelVersionIsAtLeast(@NotNull String revision) {
    return myModelVersion != null && myModelVersion.compareIgnoringQualifiers(revision) >= 0;
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

  public boolean isLayoutRenderingIssuePresent() {
    return myLayoutRenderingIssuePresent;
  }

  public boolean isPostBuildSyncSupported() {
    return myPostBuildSyncSupported;
  }

  public boolean shouldExportDependencies() {
    return myExportDependencies;
  }
}
