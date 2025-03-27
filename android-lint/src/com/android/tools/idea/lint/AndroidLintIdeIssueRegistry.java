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
package com.android.tools.idea.lint;

import com.android.tools.idea.lint.common.LintIdeIssueRegistry;
import com.android.tools.lint.checks.ApiDetector;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.checks.ManifestDetector;
import com.android.tools.lint.checks.ManifestTypoDetector;
import com.android.tools.lint.checks.NamespaceDetector;
import com.android.tools.lint.checks.ViewTypeDetector;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Platform;
import java.util.EnumSet;
import org.jetbrains.annotations.NotNull;

/**
 * Custom version of the {@link BuiltinIssueRegistry}. This
 * variation will filter the default issues and remove
 * any issues that aren't usable inside IDEA (e.g. they
 * rely on class files), and it will also replace the implementation
 * of some issues with IDEA specific ones.
 */
public class AndroidLintIdeIssueRegistry extends LintIdeIssueRegistry {
  public AndroidLintIdeIssueRegistry() {
  }

  @Override
  public boolean isRelevant(@NotNull Issue issue) {
    EnumSet<Platform> platforms = issue.getPlatforms();
    if (platforms.contains(Platform.JDK) && !platforms.contains(Platform.ANDROID) ) {
      return false;
    }

    Implementation implementation = issue.getImplementation();
    Class<? extends Detector> detectorClass = implementation.getDetectorClass();
    if (detectorClass == ViewTypeDetector.class) {
      issue.setImplementation(LintIdeViewTypeDetector.IMPLEMENTATION);
      return true;
    }
    else if (detectorClass == ApiDetector.class) {
      // Would otherwise be disabled since it handles class files
      return true;
    }

    // Supported more directly by other IntelliJ checks(?)
    if (issue == NamespaceDetector.UNUSED ||                // IDEA already does full validation
        issue == ManifestTypoDetector.ISSUE ||              // IDEA already does full validation
        issue == ManifestDetector.WRONG_PARENT) {           // IDEA checks for this in Java code
      return false;
    }

    return super.isRelevant(issue);
  }
}
