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
import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.issues.SyncIssues
import com.android.tools.idea.gradle.structure.configurables.PsPathRenderer
import com.android.tools.idea.gradle.structure.daemon.getSdkIndexIssueFor
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsGeneralIssue
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.ERROR
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.INFO
import com.android.tools.idea.gradle.structure.model.PsIssue.Severity.WARNING
import com.android.tools.idea.gradle.structure.model.PsIssueType.PROJECT_ANALYSIS
import com.android.tools.idea.gradle.structure.model.PsPath
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsResolvedLibraryAndroidDependency
import com.android.tools.idea.gradle.structure.model.android.ReverseDependency
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.xml.util.XmlStringUtil.escapeString
import java.io.File
import java.util.regex.Pattern

class PsAndroidModuleAnalyzer(
  val parentDisposable: Disposable,
  private val pathRenderer: PsPathRenderer
) : PsModelAnalyzer<PsAndroidModule>(parentDisposable) {

  override val supportedModelType: Class<PsAndroidModule> = PsAndroidModule::class.java

  override fun analyze(model: PsAndroidModule): Sequence<PsIssue> {
    return analyzeModuleVariants(model) +
           analyzeDeclaredDependencies(model) +
           analyzeLibraryVersionPromotions(model) +
           analyzeDependencyScopes(model) +
           analyzeSdkIndexLibraries(model)
  }

  private fun analyzeModuleVariants(model: PsAndroidModule) : Sequence<PsIssue> =
    analyzeModuleDependencies(model, pathRenderer) +
    analyzeProductFlavors(model, pathRenderer)

  private fun analyzeDeclaredDependencies(model: PsAndroidModule): Sequence<PsIssue> {
    val issuesByData = transferSyncIssues(model.resolvedSyncIssues)
    return model.dependencies.libraries.asSequence().flatMap { dependency ->
      val issueKey = dependency.spec.group + GRADLE_PATH_SEPARATOR + dependency.spec.name
      analyzeDeclaredDependency(dependency) +
      (issuesByData[issueKey]?.asSequence()?.map { issue -> createIssueFrom(issue, dependency.path) } ?: sequenceOf())
    }
  }

  private fun analyzeLibraryVersionPromotions(model: PsAndroidModule): Sequence<PsIssue> {
    val promotedLibraries =
      model
        .resolvedVariants
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

    return promotedLibraries.asSequence().map { (promotion, resolvedDependencies) ->
      val (path, spec, promotedTo) = promotion
      val scopes = scopeAggregator.aggregate(
        resolvedDependencies
          .map { PsMessageScope(it.artifact.parent.buildTypeName, it.artifact.parent.productFlavorNames, it.artifact.name) }
          .toSet())
      val declaredVersion = spec.version
      // TODO(b/110690694): Provide a detailed message showing all known places which request different versions of the same library.
      PsGeneralIssue(
        "Gradle promoted library version from $declaredVersion to ${promotedTo.version}",
        "in: ${scopes.joinToString("\n") { it.toString() }}",
        path,
        PROJECT_ANALYSIS,
        INFO)
    }
  }

  private fun analyzeDependencyScopes(model: PsAndroidModule): Sequence<PsIssue> {
    return ((model.dependencies.libraries as List<PsDeclaredDependency>) +
            (model.dependencies.jars as List<PsDeclaredDependency>) +
            (model.dependencies.modules as List<PsDeclaredDependency>))
      .asSequence()
      .flatMap { dependency -> analyzeDependencyScope(dependency) }
  }

  private fun analyzeSdkIndexLibraries(model: PsAndroidModule): Sequence<PsIssue> {
    return model
      .resolvedVariants
      .asSequence()
      .flatMap { it.artifacts }
      .flatMap { it.dependencies.libraries }
      .flatMap { library ->
        library
          .getReverseDependencies()
          .filterIsInstance<ReverseDependency.Transitive>()
          .map { PathSpecAndRoot(library) }
      }
      .filter { it.path != null && it.spec.group != null }
      .distinct()
      .mapNotNull { getSdkIndexIssueFor(it.spec, it.path!!, it.rootDir) }
  }

  private data class PathSpecAndRoot(val path: PsPath?, val spec: PsArtifactDependencySpec, val rootDir: File?) {
    constructor(library: PsResolvedLibraryAndroidDependency) : this(library.path, library.spec, library.parent.rootDir)
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
        model.productFlavors.filter { it.effectiveDimension == dimension.name }.map { it.name }.toSet()
      })
  }
}

private val URL_PATTERN = Pattern.compile("\\(?http://[-A-Za-z0-9+&@#/%?=~_()|!:,.;]*[-A-Za-z0-9+&@#/%=~_()|]")

@VisibleForTesting
fun createIssueFrom(syncIssue: IdeSyncIssue, path: PsPath): PsIssue {
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

private fun getSeverity(issue: IdeSyncIssue): PsIssue.Severity {
  when (issue.severity) {
    IdeSyncIssue.SEVERITY_ERROR -> return ERROR
    IdeSyncIssue.SEVERITY_WARNING -> return WARNING
  }
  return INFO
}

private fun transferSyncIssues(syncIssues: SyncIssues?) =
  syncIssues?.filter { !it.data.isNullOrEmpty() }?.groupBy { it.data!! }.orEmpty()
