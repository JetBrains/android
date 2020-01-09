/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import static com.android.tools.idea.lint.common.LintIdeProject.SUPPORT_CLASS_FILES;

import com.android.annotations.NonNull;
import com.android.tools.lint.checks.BuiltinIssueRegistry;
import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Implementation;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Scope;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Custom version of the {@link BuiltinIssueRegistry}. This
 * variation will filter the default issues and remove
 * any issues that aren't usable inside IDEA (e.g. they
 * rely on class files)
 */
public class LintIdeIssueRegistry extends BuiltinIssueRegistry {
  private static List<Issue> ourFilteredIssues;

  public LintIdeIssueRegistry() {
  }

  public static IssueRegistry get() {
    return LintIdeSupport.get().getIssueRegistry();
  }

  @NonNull
  @Override
  public List<Issue> getIssues() {
    if (ourFilteredIssues == null) {
      List<Issue> issues = super.getIssues();
      List<Issue> result = new ArrayList<>(issues.size());
      for (Issue issue : issues) {
        if (isRelevant(issue)) {
          result.add(issue);
        }
      }

      //noinspection AssignmentToStaticFieldFromInstanceMethod
      ourFilteredIssues = result;
    }
    return ourFilteredIssues;
  }

  /**
   * Returns true if the given lint check is relevant in the IDE (typically because the check is duplicated by existing IDE inspections)
   */
  public boolean isRelevant(@NonNull Issue issue) {
    Implementation implementation = issue.getImplementation();
    EnumSet<Scope> scope = implementation.getScope();
    if (scope.contains(Scope.CLASS_FILE) ||
        scope.contains(Scope.ALL_CLASS_FILES) ||
        scope.contains(Scope.JAVA_LIBRARIES)) {

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

    return true;
  }
}
