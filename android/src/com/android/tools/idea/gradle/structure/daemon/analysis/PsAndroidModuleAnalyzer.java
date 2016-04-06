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
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsIssueCollection;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyPath;
import com.android.tools.idea.gradle.structure.navigation.PsModulePath;
import com.android.tools.idea.gradle.structure.navigation.PsNavigationPath;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.structure.model.PsIssue.Type.INFO;
import static com.android.tools.idea.gradle.structure.model.PsIssue.Type.WARNING;

public class PsAndroidModuleAnalyzer extends PsModelAnalyzer<PsAndroidModule> {
  @NotNull private final PsContext myContext;

  public PsAndroidModuleAnalyzer(@NotNull PsContext context) {
    myContext = context;
  }

  @Override
  protected void doAnalyze(@NotNull PsAndroidModule module, @NotNull PsIssueCollection issueCollection) {
    PsModulePath modulePath = new PsModulePath(module);
    module.forEachDependency(dependency -> {
      if (dependency == null) {
        return false;
      }
      if (dependency instanceof PsLibraryDependency && dependency.isDeclared()) {
        PsLibraryDependency libraryDependency = (PsLibraryDependency)dependency;
        PsNavigationPath path = new PsLibraryDependencyPath(myContext, libraryDependency);

        PsArtifactDependencySpec declaredSpec = libraryDependency.getDeclaredSpec();
        assert declaredSpec != null;
        String declaredVersion = declaredSpec.version;
        if (declaredVersion != null && declaredVersion.endsWith("+")) {
          String message = "Avoid using '+' in version numbers; can lead to unpredictable and unrepeatable builds.";
          String description = "Using '+' in dependencies lets you automatically pick up the latest available " +
                               "version rather than a specific, named version. However, this is not recommended; " +
                               "your builds are not repeatable; you may have tested with a slightly different " +
                               "version than what the build server used. (Using a dynamic version as the major " +
                               "version number is more problematic than using it in the minor version position.)";
          PsIssue issue = new PsIssue(message, description, path, WARNING);
          issue.setExtraPath(modulePath);
          issueCollection.add(issue);
        }

        if (libraryDependency.hasPromotedVersion()) {
          PsArtifactDependencySpec resolvedSpec = libraryDependency.getResolvedSpec();
          String message = "Gradle promoted library version from " + declaredVersion + " to " + resolvedSpec.version;
          String description = "To resolve version conflicts, Gradle by default uses the newest version of a dependency. " +
                               "<a href='https://docs.gradle.org/current/userguide/dependency_management.html'>Open Gradle " +
                               "documentation</a>";
          PsIssue issue = new PsIssue(message, description, path, INFO);
          issue.setExtraPath(modulePath);
          issueCollection.add(issue);
        }
      }
      return true;
    });
  }

  @Override
  @NotNull
  public Class<PsAndroidModule> getSupportedModelType() {
    return PsAndroidModule.class;
  }
}
