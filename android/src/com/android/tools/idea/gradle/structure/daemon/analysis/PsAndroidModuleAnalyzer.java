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

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsIssueCollection;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsLibraryDependency;
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyPath;
import com.android.tools.idea.gradle.structure.navigation.PsNavigationPath;
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.idea.gradle.structure.model.PsIssue.Type.WARNING;

public class PsAndroidModuleAnalyzer extends PsModelAnalyzer<PsAndroidModule> {
  @Override
  protected void doAnalyze(@NotNull PsAndroidModule module, @NotNull final PsIssueCollection issueCollection) {
    module.forEachDependency(new Predicate<PsAndroidDependency>() {
      @Override
      public boolean apply(@Nullable PsAndroidDependency dependency) {
        if (dependency == null) {
          return false;
        }
        if (dependency instanceof PsLibraryDependency && dependency.isDeclared()) {
          PsLibraryDependency libraryDependency = (PsLibraryDependency)dependency;
          PsArtifactDependencySpec declaredSpec = libraryDependency.getDeclaredSpec();
          assert declaredSpec != null;
          String version = declaredSpec.version;
          if (version != null && version.endsWith("+")) {
            String message = "Avoid using '+' in version numbers; can lead to unpredictable and unrepeatable builds";
            String description = "Using '+' in dependencies lets you automatically pick up the latest available " +
                                 "version rather than a specific, named version. However, this is not recommended; " +
                                 "your builds are not repeatable; you may have tested with a slightly different " +
                                 "version than what the build server used. (Using a dynamic version as the major " +
                                 "version number is more problematic than using it in the minor version position.)";
            PsNavigationPath path = new PsLibraryDependencyPath(libraryDependency);
            PsIssue issue = new PsIssue(message, description, path, WARNING);
            issueCollection.add(issue);
          }
        }
        return true;
      }
    });
  }

  @Override
  @NotNull
  public Class<PsAndroidModule> getSupportedModelType() {
    return PsAndroidModule.class;
  }
}
