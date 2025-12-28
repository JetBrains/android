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
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.RenderingServices
import com.google.common.annotations.VisibleForTesting
import com.google.common.base.Function
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.idea.blaze.base.logging.utils.querysync.QuerySyncActionStatsScope
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot
import com.google.idea.blaze.base.qsync.DependencyTracker
import com.google.idea.blaze.base.qsync.DependencyTracker.DependencyBuildRequest.RequestType
import com.google.idea.blaze.base.qsync.QuerySyncManager
import com.google.idea.blaze.base.qsync.QuerySyncManager.Companion.createOperation
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
import com.google.idea.common.experiments.FeatureRolloutExperiment
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.guava.asDeferred

// TODO: b/418844903 - Update the artifact manager
internal class BazelBuildServices : BuildSystemFilePreviewServices.BuildServices<BazelBuildTargetReference> {
  private val listeners: MutableCollection<BuildSystemFilePreviewServices.BuildListener> = CopyOnWriteArrayList()
  private val keyToRenderingServicesMap: MutableMap<Key, BazelRenderingServices> = ConcurrentHashMap()

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
    return if (aswbComposablePreviews.isEnabled())
      keyToRenderingServicesMap.computeIfAbsent(Key(target)) { BazelRenderingServices() }
    else
      BazelRenderingServices()
  }

  override fun getLastCompileStatus(buildTarget: BazelBuildTargetReference): ProjectSystemBuildManager.BuildStatus {
    // TODO: b/409383880 - Implement this
    return ProjectSystemBuildManager.BuildStatus.UNKNOWN
  }

  /**
   * Executed by an application pool thread
   */
  override fun buildArtifacts(buildTargets: Collection<BazelBuildTargetReference>) {
    val unused = buildArtifactsAsync(buildTargets.single())
  }

  /**
   * Executed by an application pool thread
   */
  @VisibleForTesting
  fun buildArtifactsAsync(target: BazelBuildTargetReference): Deferred<Boolean> {
    val project = target.project
    val file = target.file
    val scope = QuerySyncActionStatsScope.createForFile(project, BazelBuildServices::class.java, null, file)

    return BuildDependenciesHelper(project).determineTargetsAndRun(
      WorkspaceRoot.virtualFilesToWorkspaceRelativePaths(project, listOf(file)),
      BuildDependenciesHelperSelectTargetPopup.createDisambiguateTargetPrompt { it.showCenteredInCurrentWindow(project) },
      TargetDisambiguationAnchors.NONE,
      scope
    ) { labels ->
      buildAndRefresh(target, labels.single(), scope)
    }
  }

  /**
   * Executed by the EDT
   */
  @UiThread
  private fun buildAndRefresh(
    target: BazelBuildTargetReference,
    label: Label,
    scope: QuerySyncActionStatsScope
  ): @UiThread Deferred<Boolean> {
    val project = target.project

    val buildAndRefresh = createOperation(
      "Build & Refresh",
      "Building and refreshing",
      QuerySyncManager.OperationType.BUILD_DEPS
    ) { context ->
      buildAndRefresh(target, context, label)
    }

    val buildAndRefreshFuture: ListenableFuture<Boolean> =
      QuerySyncManager.getInstance(project).runOperation(
        scope,
        QuerySyncManager.TaskOrigin.USER_ACTION,
        buildAndRefresh
      )

    val newBuildResultFuture =
      Futures.transform<Boolean, BuildSystemFilePreviewServices.BuildListener.BuildResult>(
        buildAndRefreshFuture,
        Function { succeeded: Boolean -> newBuildResult(succeeded, project) },
        MoreExecutors.directExecutor()
      )

    listeners.forEach{ listener ->
      listener.buildStarted(
        BuildSystemFilePreviewServices.BuildListener.BuildMode.COMPILE, newBuildResultFuture
      )
    }

    return buildAndRefreshFuture.asDeferred()
  }

  /**
   * Executed by the Blaze executor
   */
  @Throws(BuildException::class)
  private fun buildAndRefresh(
    target: BazelBuildTargetReference,
    context: BlazeContext,
    label: Label
  ) {
    val tracker: DependencyTracker =
      QuerySyncManager.getInstance(target.project).getDependencyTracker()!!
    val builder = tracker.getBuilder()
    val groups = DependencyTracker.DependencyBuildRequest.getOutputGroups(listOf(QuerySyncLanguage.JVM), RequestType.FILE_PREVIEWS)

    try {
      cacheOutput(target, builder.build(context, setOf(label), groups), context)
    } catch (exception: IOException) {
      throw BuildException(exception)
    }
  }

  private fun cacheOutput(
    target: BazelBuildTargetReference,
    output: OutputInfo,
    context: BlazeContext
  ) {
    val project = target.project
    val path = target.getFileWorkspaceRelativePath()

    val cache = RuntimeArtifactCache.getInstance(project)
    val label = QuerySyncManager.getInstance(project).currentSnapshot.orElseThrow().graph.sourceFileToLabel(path)

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

    keyToRenderingServicesMap[Key(target)] = BazelRenderingServices(jars, externalJars)
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

  companion object {
    private val aswbComposablePreviews = FeatureRolloutExperiment("aswb.composable.previews")
  }
}
