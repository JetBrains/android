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

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.*;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.android.tools.idea.lint.LintIdeProject.SUPPORT_CLASS_FILES;

/**
 * Custom version of the {@link BuiltinIssueRegistry}. This
 * variation will filter the default issues and remove
 * any issues that aren't usable inside IDEA (e.g. they
 * rely on class files), and it will also replace the implementation
 * of some issues with IDEA specific ones.
 */
public class LintIdeIssueRegistry extends BuiltinIssueRegistry {
  private static List<Issue> ourFilteredIssues;

  public LintIdeIssueRegistry() {
  }

  @NonNull
  @Override
  public List<Issue> getIssues() {
    if (ourFilteredIssues == null) {
      List<Issue> sIssues = super.getIssues();
      List<Issue> result = new ArrayList<Issue>(sIssues.size());
      for (Issue issue : sIssues) {
        Implementation implementation = issue.getImplementation();
        Class<? extends Detector> detectorClass = implementation.getDetectorClass();
        if (detectorClass == GradleDetector.class) {
          issue.setImplementation(LintIdeGradleDetector.IMPLEMENTATION);
        } else if (detectorClass == ViewTypeDetector.class) {
          issue.setImplementation(LintIdeViewTypeDetector.IMPLEMENTATION);
        } else if (!isRelevant(issue)) {
          // Skip issue: not included inside the IDE
          continue;
        }
        result.add(issue);
      }

      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourFilteredIssues = result;
    }
    return ourFilteredIssues;
  }

  /** Returns true if the given lint check is relevant in the IDE (typically because the check is duplicated by existing IDE inspections) */
  public static boolean isRelevant(@NonNull Issue issue) {

    Implementation implementation = issue.getImplementation();
    EnumSet<Scope> scope = implementation.getScope();
    Class<? extends Detector> detectorClass = implementation.getDetectorClass();
    if (scope.contains(Scope.CLASS_FILE) ||
               scope.contains(Scope.ALL_CLASS_FILES) ||
               scope.contains(Scope.JAVA_LIBRARIES)) {
      if (detectorClass == ApiDetector.class) {
        // We're okay to include the class file check here
        return true;
      }

      //noinspection ConstantConditions
      assert !SUPPORT_CLASS_FILES; // When enabled, adjust this to include class detector based issues

      for (EnumSet<Scope> analysisScope : implementation.getAnalysisScopes()) {
        if (!analysisScope.contains(Scope.CLASS_FILE) &&
            !analysisScope.contains(Scope.ALL_CLASS_FILES) &&
            !analysisScope.contains(Scope.JAVA_LIBRARIES)) {
          return true;
        }
      }
      return false;
    }

    // Supported more directly by other IntelliJ checks(?)
    if (issue == NamespaceDetector.UNUSED ||                // IDEA already does full validation
        issue == ManifestTypoDetector.ISSUE ||              // IDEA already does full validation
        issue == ManifestDetector.WRONG_PARENT) {           // IDEA checks for this in Java code
      return false;
    }

    return true;
  }
}
