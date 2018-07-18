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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.model.*
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.*
import com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.ReverseDependency
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.model.meta.maybeValue
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
    transferSyncIssues(gradleModel, issuesByData)
    analyzeDeclaredDependencies(model, issuesByData, issueCollection)
    analyzeLibraryVersionPromotions(model, issueCollection)
  }

  private fun transferSyncIssues(gradleModel: AndroidModuleModel?,
                                 issuesByData: ArrayListMultimap<String, SyncIssue>) {
    val syncIssues = gradleModel?.androidProject?.syncIssues
    syncIssues?.forEach { syncIssue ->
      val data = nullToEmpty(syncIssue.data)
      issuesByData.put(data, syncIssue)
    }
  }

  private fun analyzeDeclaredDependencies(model: PsAndroidModule,
                                          issuesByData: ArrayListMultimap<String, SyncIssue>,
                                          issueCollection: PsIssueCollection) {
    model.dependencies.forEachLibraryDependency { dependency ->
      val path = dependency.path

      val issueKey = dependency.spec.group + GRADLE_PATH_SEPARATOR + dependency.spec.name
      val librarySyncIssues = issuesByData.get(issueKey)
      for (syncIssue in librarySyncIssues) {
        val issue = createIssueFrom(syncIssue, path)
        issueCollection.add(issue)
      }
      analyzeDeclaredDependency(dependency, issueCollection)
    }
  }

  private fun analyzeLibraryVersionPromotions(model: PsAndroidModule,
                                              issueCollection: PsIssueCollection) {
    val promotedLibraries =
      model
        .variants
        .flatMap { it.artifacts }
        .flatMap { it.dependencies.libraries }
        .flatMap { resolved ->
          resolved
            .getReverseDependencies()
            .filterIsInstance<ReverseDependency.Declared>()  // TODO(b/74948244): Implement POM dependency promotion analysis.
            .filter { it.spec < resolved.spec }.map {
              PathSpaceAndPromotedTo(it, resolved.spec) to resolved
            }
        }
        .groupBy({ it.first }, { it.second })

    val scopeAggregator = createScopeAggregator(model)

    promotedLibraries.forEach { (promotion, resolvedDependencies) ->
      val (path, spec, promotedTo) = promotion
      val scopes = scopeAggregator.aggregate(
        resolvedDependencies
          .map { PsMessageScope(it.artifact.parent.buildTypeName, it.artifact.parent.productFlavors, it.artifact.name) }
          .toSet())
      val declaredVersion = spec.version
      // TODO(b/110690694): Provide a detailed message showing all known places which request different versions of the same library.
      issueCollection.add(PsGeneralIssue(
        "Gradle promoted library version from $declaredVersion to ${promotedTo.version}",
        "in: ${scopes.joinToString("\n") { it.toString() }}",
        path,
        PROJECT_ANALYSIS,
        INFO))
    }
  }

  private data class PathSpaceAndPromotedTo(val path: PsPath, val spec: PsArtifactDependencySpec, val promotedTo: PsArtifactDependencySpec) {
    constructor (declaration: ReverseDependency.Declared, promotedTo: PsArtifactDependencySpec) : this(
      declaration.dependency.path,
      declaration.spec, promotedTo)
  }

  private fun createScopeAggregator(model: PsAndroidModule): PsMessageScopeAggregator {
    return PsMessageScopeAggregator(
      model.buildTypes.map { it.name }.toSet(),
      model.flavorDimensions.map { dimension ->
        model.productFlavors.filter { it.dimension.maybeValue == dimension }.map { it.name }.toSet()
      })
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
  return PsGeneralIssue(message, path, PROJECT_ANALYSIS, getSeverity(syncIssue))
}

private fun getSeverity(issue: SyncIssue): PsIssue.Severity {
  val severity = issue.severity
  when (severity) {
    SEVERITY_ERROR -> return ERROR
    SEVERITY_WARNING -> return WARNING
  }
  return INFO
}
