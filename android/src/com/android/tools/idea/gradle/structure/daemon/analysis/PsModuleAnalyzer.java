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
package com.android.tools.idea.gradle.structure.daemon.analysis;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.*;
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath;
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyVersionQuickFixPath;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.structure.model.PsIssue.Severity.INFO;
import static com.android.tools.idea.gradle.structure.model.PsIssue.Severity.WARNING;
import static com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS;

public abstract class PsModuleAnalyzer<T extends PsModule> extends PsModelAnalyzer<T> {
  private static final Pattern URL_PATTERN = Pattern.compile("\\(?http://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]");

  @NotNull private final PsContext myContext;

  protected PsModuleAnalyzer(@NotNull PsContext context) {
    myContext = context;
  }

  protected void analyzeDeclaredDependency(@NotNull PsLibraryDependency dependency,
                                           @NotNull PsModulePath modulePath,
                                           @NotNull PsIssueCollection issueCollection) {
    PsArtifactDependencySpec resolvedSpec = dependency.getResolvedSpec();
    PsPath path = new PsLibraryDependencyNavigationPath(myContext, dependency);

    PsArtifactDependencySpec declaredSpec = dependency.getDeclaredSpec();
    assert declaredSpec != null;
    String declaredVersion = declaredSpec.version;
    if (declaredVersion != null && declaredVersion.endsWith("+")) {
      String message = "Avoid using '+' in version numbers; can lead to unpredictable and unrepeatable builds.";
      PsIssue issue = new PsIssue(message, "", path, PROJECT_ANALYSIS, WARNING);
      issue.setExtraPath(modulePath);

      PsPath quickFix = new PsLibraryDependencyVersionQuickFixPath(dependency);
      issue.setQuickFixPath(quickFix);

      issueCollection.add(issue);
    }

    if (dependency.hasPromotedVersion()) {
      String message = "Gradle promoted library version from " + declaredVersion + " to " + resolvedSpec.version;
      String description = "To resolve version conflicts, Gradle by default uses the newest version of a dependency. " +
                           "<a href='https://docs.gradle.org/current/userguide/dependency_management.html'>Open Gradle " +
                           "documentation</a>";
      PsIssue issue = new PsIssue(message, description, path, PROJECT_ANALYSIS, INFO);
      issue.setExtraPath(modulePath);
      issueCollection.add(issue);
    }
  }

  @NotNull
  protected PsContext getContext() {
    return myContext;
  }
}
