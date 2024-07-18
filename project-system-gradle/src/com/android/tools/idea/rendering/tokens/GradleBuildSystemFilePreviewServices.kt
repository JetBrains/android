/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.rendering.tokens

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.gradle.project.build.BuildContext
import com.android.tools.idea.gradle.project.build.BuildStatus
import com.android.tools.idea.gradle.project.build.GradleBuildListener
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.gradle.toProjectSystemBuildMode
import com.android.tools.idea.projectsystem.gradle.toProjectSystemBuildStatus
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildServices
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope
import java.util.WeakHashMap
import kotlin.reflect.jvm.jvmName

/**
 * An implementation of [BuildSystemFilePreviewServices] for [GradleProjectSystem].
 *
 * It provides mapping from internal Gradle project system message bus topics and states and like [GradleBuildListener] and
 * [GradleBuildState] to values and events defined in [BuildSystemFilePreviewServices] in a way that is needed for the UI tools to track
 * the status and consume the content of build artifacts required for rendering.
 */
class GradleBuildSystemFilePreviewServices : BuildSystemFilePreviewServices<GradleProjectSystem, GradleBuildTargetReference>, GradleToken {
  override fun isApplicable(buildTargetReference: BuildTargetReference): Boolean = buildTargetReference is GradleBuildTargetReference

  private val runningBuilds: MutableMap<BuildContext, SettableFuture<BuildListener.BuildResult>> =
    WeakHashMap()

  override val buildTargets: BuildSystemFilePreviewServices.BuildTargets = object: BuildSystemFilePreviewServices.BuildTargets {
    override fun from(module: Module, targetFile: VirtualFile): BuildTargetReference {
      return fromModuleOnly(module)
    }

    override fun fromModuleOnly(module: Module): BuildTargetReference {
      return GradleBuildTargetReference(module)
    }
  }

  override val buildServices: BuildServices<GradleBuildTargetReference> = object: BuildServices<GradleBuildTargetReference> {
    override fun getLastCompileStatus(buildTarget: GradleBuildTargetReference): ProjectSystemBuildManager.BuildStatus {
      return getBuildServicesStatus(buildTarget).lastCompileStatus
    }

    override fun buildArtifacts(buildTargets: Collection<GradleBuildTargetReference>) {
      if (buildTargets.isEmpty()) return
      val modules = buildTargets.map { (it as GradleBuildTargetReference).module }.distinct()
      val project = modules.map { it.project }.single()
      GradleBuildInvoker.getInstance(project).compileJava(modules.toTypedArray())
    }
  }

  private fun getBuildServicesStatus(buildTarget: GradleBuildTargetReference): GradleBuildServicesStatus {
    val module = buildTarget.module
    return module.getOrCreateUserData(GradleBuildServicesStatus.KEY) { GradleBuildServicesStatus(module) }
  }

  /**
   * Maps Gradle project system build events published under `GRADLE_BUILD_TOPIC` to build listeners for UI tools.
   *
   * Mapping is not precise, and it is not conservative in regard to cleans, failures and cancellations. For example, an interrupted rebuild
   * might be closer to a completed clean than to an interrupted build.
   */
  override fun subscribeBuildListener(
    project: Project,
    parentDisposable: Disposable,
    listener: BuildListener
  ) {
    GradleBuildState.subscribe(project, object : GradleBuildListener {
      @UiThread
      override fun buildStarted(context: BuildContext) {
        // TODO: solodkyy - Review mode and status mapping to handle failures and cancellations with more caution.
        val buildMode = context.buildMode?.toProjectSystemBuildMode() ?: ProjectSystemBuildManager.BuildMode.UNKNOWN
        val translatedBuildMode = when (buildMode) {
          ProjectSystemBuildManager.BuildMode.UNKNOWN -> return
          ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE -> BuildListener.BuildMode.COMPILE
          ProjectSystemBuildManager.BuildMode.CLEAN -> BuildListener.BuildMode.CLEAN
        }
        val resultFuture =
          runningBuilds.getOrPut(context) { SettableFuture.create<BuildListener.BuildResult>() }
        listener.buildStarted(translatedBuildMode, resultFuture)
      }

      @UiThread
      override fun buildFinished(status: BuildStatus, context: BuildContext) {
        val resultFuture = runningBuilds.remove(context) ?: return
        resultFuture.set(
          BuildListener.BuildResult(
            status.toProjectSystemBuildStatus(),
            getBuildScope(project, context)
          )
        )
      }
    }, parentDisposable)
  }

  private fun getBuildScope(project: Project, unusedContext: BuildContext): GlobalSearchScope {
    return GlobalSearchScope.allScope(project) // TODO: b/304471897 - Return the correct scope.
  }
}

/**
 * An implementation of [BuildSystemFilePreviewServices.BuildServices] for a [GradleBuildTargetReference].
 *
 * In Gradle a build target (at least for the purpose of building artifacts for rendering) is represented in the IDE as an IDE module. For
 * production code it is the `mainModule` of the module group that represents a Gradle project (i.e. `:app` etc.)
 *
 * [GradleBuildServices] holds a reference to the [module] that represents the build target the services are supposed to handle.
 */
private class GradleBuildServicesStatus(private val module: Module) {
  private val projectSystem = module.project.getProjectSystem()
  val lastCompileStatus: ProjectSystemBuildManager.BuildStatus
    get() {
      val last = projectSystem.getBuildManager().getLastBuildResult()
      return when (last.mode) {
        ProjectSystemBuildManager.BuildMode.UNKNOWN -> ProjectSystemBuildManager.BuildStatus.UNKNOWN
        ProjectSystemBuildManager.BuildMode.COMPILE_OR_ASSEMBLE -> last.status
        ProjectSystemBuildManager.BuildMode.CLEAN -> ProjectSystemBuildManager.BuildStatus.UNKNOWN
      }
    }

  companion object {
    val KEY = Key.create<GradleBuildServicesStatus>(GradleBuildServicesStatus::class.jvmName)
  }
}

data class GradleBuildTargetReference internal constructor(override val module: Module) : BuildTargetReference
