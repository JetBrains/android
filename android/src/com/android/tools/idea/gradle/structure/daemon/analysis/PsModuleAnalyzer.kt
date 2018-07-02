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
package com.android.tools.idea.gradle.structure.daemon.analysis

import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsGeneralIssue
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.WARNING
import com.android.tools.idea.gradle.structure.model.PsIssueCollection
import com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath
import com.android.tools.idea.gradle.structure.quickfix.PsLibraryDependencyVersionQuickFixPath

abstract class PsModuleAnalyzer<T : PsModule> protected constructor(protected val context: PsContext) : PsModelAnalyzer<T>() {

  protected fun analyzeDeclaredDependency(dependency: PsDeclaredLibraryDependency,
                                          issueCollection: PsIssueCollection) {
    val path = PsLibraryDependencyNavigationPath(dependency)

    val declaredSpec = dependency.spec
    val declaredVersion = declaredSpec.version
    if (declaredVersion != null && declaredVersion.endsWith("+")) {
      val message = "Avoid using '+' in version numbers; can lead to unpredictable and unrepeatable builds."
      // TODO(b/111058962): Replace "+" with the most recent version of the library.
      val issue = PsGeneralIssue(message, "", path, PROJECT_ANALYSIS, WARNING, PsLibraryDependencyVersionQuickFixPath(dependency, "+"))

      issueCollection.add(issue)
    }
  }
}
