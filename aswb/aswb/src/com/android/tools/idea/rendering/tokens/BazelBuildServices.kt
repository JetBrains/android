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
package com.android.tools.idea.rendering.tokens

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.projectsystem.ClassFileFinder
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.RenderingServices
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.SettableFuture
import com.google.idea.blaze.base.command.buildresult.BuildResult
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.DependencyTracker
import com.google.idea.blaze.base.qsync.DependencyTracker.DependencyBuildRequest.RequestType
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelper
import com.google.idea.blaze.base.qsync.action.BuildDependenciesHelperSelectTargetPopup
import com.google.idea.blaze.base.qsync.action.TargetDisambiguationAnchors
import com.google.idea.blaze.base.run.RuntimeArtifactCache
import com.google.idea.blaze.base.run.RuntimeArtifactKind
import com.google.idea.blaze.base.scope.BlazeContext
import com.google.idea.blaze.common.Label
import com.google.idea.blaze.exception.BuildException
import com.google.idea.blaze.qsync.deps.OutputInfo
import com.google.idea.blaze.qsync.project.QuerySyncLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
internal class BazelBuildServicesCoroutineScope(val scope: CoroutineScope)

// TODO: b/418844903 - Update the artifact manager
internal class BazelBuildServices : BuildSystemFilePreviewServices.BuildServices<BazelBuildTargetReference> {
  private val listeners: MutableCollection<BuildSystemFilePreviewServices.BuildListener> = CopyOnWriteArrayList()
  private val keyToBuildOutcomeMap: MutableMap<Label, BuildOutcome> = ConcurrentHashMap()

  internal data class BuildOutcome(
    val status: ProjectSystemBuildManager.BuildStatus,
    val timestamp: Instant,
    val classFileFinder: ClassFileFinder? = null,
    val externalJars: Collection<Path> = emptyList()
  )

  internal fun getBuildOutcome(label: Label): BuildOutcome? = keyToBuildOutcomeMap[label]

  /**
   * Executed by an application pool thread and the EDT
   */
  fun add(listener: BuildSystemFilePreviewServices.BuildListener) {
    listeners.add(listener)
  }

  /**
   * Executed by the EDT
   */
  @UiThread
  fun remove(listener: BuildSystemFilePreviewServices.BuildListener) {
    listeners.remove(listener)
  }

  fun getRenderingServices(target: BazelBuildTargetReference): RenderingServices {
    return BazelRenderingServices(this, target)
  }

  override fun getLastCompileStatus(buildTarget: BazelBuildTargetReference): ProjectSystemBuildManager.BuildStatus {
    return buildTarget.toAllLabels()
      .mapNotNull { keyToBuildOutcomeMap[it] }
      .maxByOrNull { it.timestamp }
      ?.status
      ?: ProjectSystemBuildManager.BuildStatus.UNKNOWN
  }

  /**
   * Executed by an application pool thread
   */
  override fun buildArtifacts(targets: Collection<BazelBuildTargetReference>) {
    val unused = buildArtifactsAsync(targets)
  }

  /**
   * Executed by an application pool thread
   */
  @VisibleForTesting
  fun buildArtifactsAsync(targets: Collection<BazelBuildTargetReference>): Deferred<Boolean> {
    val project = targets.project
    val files = targets.map { it.file }
    val scope =
      QuerySyncActionStatsScope.createForFiles(project, BazelBuildServices::class.java, null, ImmutableList.copyOf(files))

    return BuildDependenciesHelper(project).determineTargetsAndRun(
      WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, files),
      BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt { it.showCenteredInCurrentWindow(project) },
      TargetDisambiguationAnchors.NONE,
      scope
    ) { labels ->
      buildAndRefresh(targets, labels.single(), scope)
    }
  }

  /**
   * Executed by the EDT
   */
  @UiThread
  private fun buildAndRefresh(
    targets: Collection<BazelBuildTargetReference>,
    label: Label,
    scope: QuerySyncActionStatsScope
  ): @UiThread Deferred<Boolean> {
    val project = targets.project
    val coroutineScope = project.service<BazelBuildServicesCoroutineScope>().scope

    return coroutineScope.async {
      val buildResultSettableFuture = SettableFuture.create<BuildSystemFilePreviewServices.BuildListener.BuildResult>()
      try {
        withContext(Dispatchers.EDT) {
          listeners.forEach { listener ->
            listener.buildStarted(BuildSystemFilePreviewServices.BuildListener.BuildMode.COMPILE, buildResultSettableFuture)
          }
        }

        val qSyncManager = QuerySyncManager.getInstance(project)
        val operation = QuerySyncManager.createOperation("Build & Refresh", "Building and refreshing", QuerySyncManager.OperationType.BUILD_DEPS) { context ->
          context.push(scope)
          buildAndRefresh(project, context, label)
        }

        val succeeded = qSyncManager.runOperationWithToolWindow(
          this@async,
          scope,
          QuerySyncManager.TaskOrigin.USER_ACTION,
          operation
        )
        buildResultSettableFuture.set(newBuildResult(succeeded, project))
        succeeded
      } catch (e: CancellationException) {
        keyToBuildOutcomeMap[label] = BuildOutcome(ProjectSystemBuildManager.BuildStatus.CANCELLED, Instant.now())
        buildResultSettableFuture.cancel(true)
        throw e
      } catch (e: Exception) {
        buildResultSettableFuture.setException(e)
        throw e
      }
    }
  }

  /**
   * Executed by the Blaze executor
   */
  @Throws(BuildException::class)
  private fun buildAndRefresh(
    project: Project,
    context: BlazeContext,
    label: Label
  ) {
    val tracker: DependencyTracker =
      QuerySyncManager.getInstance(project).getDependencyTracker()!!
    val builder = tracker.getBuilder()
    val groups = DependencyTracker.DependencyBuildRequest.getOutputGroups(listOf(QuerySyncLanguage.JVM), RequestType.FILE_PREVIEWS)

    val toolingLabel = BazelComposeToolingProjectLabelProvider.getComposeToolingLabel(project)
    val targets = setOfNotNull(label, toolingLabel)

    try {
      val output = builder.build(context, targets, groups)
      keyToBuildOutcomeMap[label] =
        if (BuildResult.fromExitCode(output.exitCode).status == BuildResult.Status.SUCCESS) {
          val artifacts = cacheOutput(project, label, output, context)
          BuildOutcome(
            ProjectSystemBuildManager.BuildStatus.SUCCESS,
            Instant.now(),
            BazelClassFileFinder(artifacts.jars),
            artifacts.externalJars
          )
        }
        else {
          BuildOutcome(ProjectSystemBuildManager.BuildStatus.FAILED, Instant.now())
        }
    } catch (exception: Exception) {
      val status = when (exception) {
        is ProcessCanceledException, is CancellationException, is InterruptedException -> ProjectSystemBuildManager.BuildStatus.CANCELLED
        else -> ProjectSystemBuildManager.BuildStatus.FAILED
      }
      keyToBuildOutcomeMap[label] = BuildOutcome(status, Instant.now())
      throw exception
    }
  }

  private data class CachedArtifacts(val jars: Collection<Path>, val externalJars: Collection<Path>)

  private fun cacheOutput(
    project: Project,
    label: Label,
    output: OutputInfo,
    context: BlazeContext
  ): CachedArtifacts {
    val cache = RuntimeArtifactCache.getInstance(project)
    val jars =
      cache.fetchArtifacts(
        label,
        output.transitiveRuntimeJars,
        context,
        RuntimeArtifactKind.TRANSITIVE_RUNTIME_JAR
      )

    val externalJars = cache.fetchArtifacts(
      label,
      output.externalTransitiveRuntimeJars,
      context,
      RuntimeArtifactKind.EXTERNAL_TRANSITIVE_RUNTIME_JAR
    )

    return CachedArtifacts(jars, externalJars)
  }

  /**
   * Executed by the Blaze executor
   */
  private fun newBuildResult(
    succeeded: Boolean,
    project: Project
  ): BuildSystemFilePreviewServices.BuildListener.BuildResult {
    return BuildSystemFilePreviewServices.BuildListener.BuildResult(
      if (succeeded) ProjectSystemBuildManager.BuildStatus.SUCCESS else ProjectSystemBuildManager.BuildStatus.FAILED,
      GlobalSearchScope.projectScope(project)
    )
  }
}

private val Collection<BazelBuildTargetReference>.project: Project get() = map { it.project }.single()
