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

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildListener.BuildMode
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildServices
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.BuildTargets
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors.directExecutor
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.EverythingGlobalScope
import com.intellij.testFramework.ExtensionTestUtil
import org.jetbrains.annotations.TestOnly

/**
 * An implementation of [BuildSystemFilePreviewServices] for use in tests that allows simulating custom scenarios.
 */
@TestOnly
class FakeBuildSystemFilePreviewServices(
  buildTargets: FakeBuildSystemFilePreviewServices.() -> BuildTargets = { FakeBuildTargets() },
  buildServices: FakeBuildSystemFilePreviewServices.() -> BuildServices<BuildTargetReference> = { FakeBuildServices() },
) : BuildSystemFilePreviewServices<AndroidProjectSystem, BuildTargetReference> {
  private val listeners: MutableList<BuildListener> = mutableListOf()
  private var lastStatus: BuildStatus = BuildStatus.UNKNOWN

  override val buildTargets: BuildTargets = buildTargets()
  override val buildServices: BuildServices<BuildTargetReference> = buildServices()

  override fun subscribeBuildListener(project: Project, parentDisposable: Disposable, listener: BuildListener) {
    listeners.add(listener)
    Disposer.register(parentDisposable) { listeners.remove(listener) }
  }

  /**
   * Simulates a build of artifacts affecting rendering at the level of [BuildSystemFilePreviewServices].
   */
  fun simulateArtifactBuild(
    buildStatus: BuildStatus,
    buildMode: BuildMode = BuildMode.COMPILE,
    completion: ListenableFuture<Unit> = Futures.immediateFuture(Unit)
  ) {
    val buildResult = BuildListener.BuildResult(buildStatus, EverythingGlobalScope())
    val buildResultFuture = SettableFuture.create<BuildListener.BuildResult>()
    listeners.forEach {listener ->
      listener.buildStarted(buildMode, buildResultFuture)
    }
    lastStatus = buildStatus
    completion.addListener({ buildResultFuture.set(buildResult) }, directExecutor())
  }

  override fun isApplicable(projectSystem: AndroidProjectSystem): Boolean = true
  override fun isApplicable(buildTargetReference: BuildTargetReference): Boolean = true

  /**
   * Registers this fake implementation for the lifespan of [parentDisposable] for all project systems.
   */
  fun register(parentDisposable: Disposable) {
    ExtensionTestUtil.maskExtensions(BuildSystemFilePreviewServices.EP_NAME, listOf(this), parentDisposable)
  }

  class FakeBuildTargets : BuildTargets {
    override fun from(module: Module, targetFile: VirtualFile): BuildTargetReference {
      return FakeBuildTargetReference(module)
    }

    override fun fromModuleOnly(module: Module): BuildTargetReference {
      return FakeBuildTargetReference(module)
    }
  }

  inner class FakeBuildServices: BuildServices<BuildTargetReference> {
    override fun getLastCompileStatus(buildTarget: BuildTargetReference): BuildStatus {
      return lastStatus
    }

    override fun buildArtifacts(buildTargets: Collection<BuildTargetReference>) {
      simulateArtifactBuild(BuildStatus.SUCCESS)
    }
  }
}

private data class FakeBuildTargetReference(override val module: Module) : BuildTargetReference