/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.run.deployment.liveedit.tokens

import com.android.tools.idea.projectsystem.ClassContent
import com.android.tools.idea.run.classes.BuildOutcome
import com.android.tools.idea.run.deployment.liveedit.setOptions
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.qsync.deps.TargetBuildInfo
import com.google.idea.blaze.qsync.project.TargetsToBuild
import com.google.idea.blaze.qsync.project.pathToLabel
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.arguments.toLanguageVersionSettings
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtFile

/** Bazel implementation of [ApplicationLiveEditServices] for Live Edit and Compose previews. */
class BazelApplicationLiveEditServices(
  private val project: Project,
  private val buildOutcomeProvider: BuildOutcomeProvider,
  val desugarConfigsProvider: () -> List<Path>,
) : ApplicationLiveEditServices {

  /**
   * An interface to the most recent build result relevant to this [ApplicationLiveEditServices].
   *
   * (1) In the case of Live Edit build it is the results of an `.apk` build used to deploy the app.
   *
   * (2) In the case of Compose previews it is the results of a build initiated by Compose previews.
   */
  fun interface BuildOutcomeProvider {
    fun lastBuildOutcome(): BuildOutcome?
  }

  private data class CompilationDependenciesImpl(
    private val externalLibraries: List<Path>,
    private val bootClasspath: List<Path> = emptyList(),
  ) : ApplicationLiveEditServices.CompilationDependencies {
    override fun getExternalLibraries() = externalLibraries

    override fun getBootClasspath() = bootClasspath
  }

  override fun getCompilationDependencies(file: PsiFile): ApplicationLiveEditServices.CompilationDependencies? {
    val outcome = buildOutcomeProvider.lastBuildOutcome() ?: return null
    return CompilationDependenciesImpl(outcome.externalJars.toList())
  }

  override fun getClassContent(file: VirtualFile, className: String): ClassContent? {
    val outcome = buildOutcomeProvider.lastBuildOutcome() ?: return null
    return outcome.classFileFinder?.findClassFile(className)
  }

  override fun getKotlinCompilerConfiguration(ktFile: KtFile): CompilerConfiguration {
    val qSyncManager = QuerySyncManager.getInstance(project)
    val snapshot = qSyncManager.currentSnapshot.getOrNull() ?: return CompilerConfiguration.create()

    val workspaceRoot = WorkspaceRoot.fromProject(project)
    val path = workspaceRoot.relativize(ktFile.virtualFile.toNioPath())
    val sourceFileLabel = snapshot.projectStructureData.pathToLabel(path) ?: return CompilerConfiguration.create()
    val labels = snapshot.staleGraph.getSourceFileOwners(sourceFileLabel)
    if (labels.isEmpty()) return CompilerConfiguration.create()

    // Choose the target that would normally be selected for previews.
    val label =
      listOf(snapshot.staleGraph.getProjectTargetsForSourceFile(sourceFileLabel))
        .toPreferredLabel(isPreferredTarget = { buildOutcomeProvider.lastBuildOutcome()?.builtJavaTargetPredicate(it) ?: false })
        ?: labels.first()

    val targetBuildInfo = snapshot.artifactIndex.builtDepsMap()[label] ?: return CompilerConfiguration.create()
    val javaInfo = (targetBuildInfo as? TargetBuildInfo.Java)?.javaInfo ?: return CompilerConfiguration.create()
    val flags = javaInfo.kotlinCompilerFlags

    return CompilerConfiguration.create().apply {
      setOptions(parseCommandLineArguments<K2JVMCompilerArguments>(flags).toLanguageVersionSettings(MessageCollector.NONE))
      put(CommonConfigurationKeys.MODULE_NAME, label.toString())

      // Add a TODO for improvement on target selection if needed.
      // TODO: Refine target selection to match the actual dependency of the main build target if ambiguous.
    }
  }

  override fun getDesugarConfigs(): DesugarConfigs {
    return DesugarConfigs.Known(desugarConfigsProvider())
  }

  override fun getRuntimeVersionString(): String {
    return ApplicationLiveEditServices.DEFAULT_RUNTIME_VERSION
  }
}

fun Collection<TargetsToBuild>.toPreferredLabel(isPreferredTarget: (Label) -> Boolean): Label? {
  val candidates = flatMap { it.targets }.toSet()
  if (candidates.size <= 1) return candidates.singleOrNull()

  return candidates.singleOrNull { isPreferredTarget(it) }
}
