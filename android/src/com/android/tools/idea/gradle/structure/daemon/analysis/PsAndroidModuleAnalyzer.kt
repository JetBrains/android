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

import com.android.SdkConstants.GRADLE_PATH_SEPARATOR
import com.android.builder.model.SyncIssue
import com.android.builder.model.SyncIssue.SEVERITY_ERROR
import com.android.builder.model.SyncIssue.SEVERITY_WARNING
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.*
import com.android.tools.idea.gradle.structure.model.PsIssueCollection
import com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS
import com.android.tools.idea.gradle.structure.model.PsLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Strings.nullToEmpty
import com.google.common.collect.ArrayListMultimap
import com.intellij.xml.util.XmlStringUtil.escapeString
import java.util.regex.Pattern

class PsAndroidModuleAnalyzer(context: PsContext) : PsModuleAnalyzer<PsAndroidModule>(context) {

  override val supportedModelType: Class<PsAndroidModule> = PsAndroidModule::class.java

  override fun doAnalyze(model: PsAndroidModule, issueCollection: PsIssueCollection) {
    val issuesByData = ArrayListMultimap.create<String, SyncIssue>()
    val gradleModel = model.resolvedModel
    val syncIssues = gradleModel.androidProject.syncIssues
    for (syncIssue in syncIssues) {
      val data = nullToEmpty(syncIssue.data)
      issuesByData.put(data, syncIssue)
    }

    model.dependencies.forEach { dependency ->
      if (dependency is PsLibraryDependency && dependency.isDeclared) {
        val libraryDependency = dependency as PsLibraryDependency
        val path = PsLibraryDependencyNavigationPath(libraryDependency)

        val resolvedSpec = libraryDependency.spec
        val issueKey = resolvedSpec.group + GRADLE_PATH_SEPARATOR + resolvedSpec.name
        val librarySyncIssues = issuesByData.get(issueKey)
        for (syncIssue in librarySyncIssues) {
          val issue = createIssueFrom(syncIssue, path)
          issueCollection.add(issue)
        }
        // TODO(b/77848741): Fix promotion analysis.
        //analyzeDeclaredDependency(libraryDependency, issueCollection);
      }
    }
  }
}

private val URL_PATTERN = Pattern.compile("\\(?http://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]")

@VisibleForTesting
fun createIssueFrom(syncIssue: SyncIssue, path: PsPath): PsIssue {
  var message = escapeString(syncIssue.message)
  val matcher = URL_PATTERN.matcher(message)
  var result = matcher.find()
  // Replace URLs with <a href='url'>url</a>.
  while (result) {
    val url = matcher.group()
    message = message.replace(url, "<a href='$url'>$url</a>")
    result = matcher.find()
  }
  return PsIssue(message, path, PROJECT_ANALYSIS, getSeverity(syncIssue))
}

private fun getSeverity(issue: SyncIssue): PsIssue.Severity {
  val severity = issue.severity
  when (severity) {
    SEVERITY_ERROR -> return ERROR
    SEVERITY_WARNING -> return WARNING
  }
  return INFO
}
