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
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildMode
import com.android.tools.idea.projectsystem.ProjectSystemBuildManager.BuildStatus
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getToken
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.tokens.BuildSystemFilePreviewServices.Companion.getBuildSystemFilePreviewServices
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.GlobalSearchScope

/**
 * A project system specific set of services required by UI tools to manage builds and fetch build artifacts needed for rendering.
 */
interface BuildSystemFilePreviewServices<P : AndroidProjectSystem, R : BuildTargetReference> : Token {
  fun isApplicable(buildTargetReference: BuildTargetReference): Boolean

  /**
   * A collection of services used by [BuildTargetReference]'s companion object to obtain build target references from references to
   * source code in the IDE.
   */
  interface BuildTargets {
    /**
     * Constructs a [BuildTargetReference] referring to a build target containing [targetFile] that needs to be built to preview the
     * [targetFile].
     *
     * [module] is an ide module containing the [targetFile]. It is the responsibility of the caller (`BuildTargetReference`'s companion
     * object) to ensure that the [module], indeed, contains the [targetFile].
     */
    fun from(module: Module, targetFile: VirtualFile): BuildTargetReference

    /**
     * Constructs a best effort [BuildTargetReference] referring to all source code contained in the [module]. It is not expected to be
     * correctly supported by all build systems and its usage is limited to legacy callers only.
     */
    fun fromModuleOnly(module: Module): BuildTargetReference
  }

  /**
   * A collection of services used by UI tools to query the status and build artifacts required for rendering.
   */
  interface BuildServices<R : BuildTargetReference> {
    fun getLastCompileStatus(buildTarget: R): ProjectSystemBuildManager.BuildStatus
  }

  /**
   * An instance of [BuildTargets] services.
   */
  val buildTargets: BuildTargets

  /**
   * An instance of [BuildServices].
   */
  val buildServices: BuildServices<R>

  /**
   * A listener that can be subscribed to receive events related to builds that might affect rendering related build artifacts.
   */
  interface BuildListener {
    enum class BuildMode { CLEAN, COMPILE }
    /**
     * The result of a build.
     */
    data class BuildResult(
      /**
       * The final status of the build.
       */
      val status: BuildStatus,
      /**
       * A predicate (a global search scope) that can be used to test whether a virtual file was included in the scope of the build.
       */
      val scope: GlobalSearchScope
    )

    /**
     * Notifies the listener about the start of a new build that might affect rendering related artifacts
     *
     * [buildResult] future will complete **on the EDT** when the build completes.
     */
    @UiThread
    fun buildStarted(buildMode: BuildMode, buildResult: ListenableFuture<BuildResult>)
  }

  /**
   * Subscribes a [listener] to events that describe builds that affect artifacts that are used for rendering.
   *
   * Note that any currently running builds are not reported.
   */
  fun subscribeBuildListener(project: Project, parentDisposable: Disposable, listener: BuildListener)

  companion object {
    val EP_NAME =
      ExtensionPointName<BuildSystemFilePreviewServices<AndroidProjectSystem, BuildTargetReference>>(
        "com.android.tools.idea.rendering.tokens.buildSystemFilePreviewServices"
      )

    /**
     * Returns an instance of [BuildSystemFilePreviewServices] applicable to [this] project system.
     *
     * Note, that the method returns an interface projection that does not accept [BuildTargetReference]s.
     * Use [BuildTargetReference.getBuildSystemFilePreviewServices] to get an instance suitable for handling build target references.
     */
    fun AndroidProjectSystem.getBuildSystemFilePreviewServices(): BuildSystemFilePreviewServices<*, *> {
      return getToken(EP_NAME)
    }

    fun <R: BuildTargetReference> R.getBuildSystemFilePreviewServices(): BuildSystemFilePreviewServices<*, R> {
      @Suppress("UNCHECKED_CAST")
      return EP_NAME.extensionList.singleOrNull { it.isApplicable(this) } as? BuildSystemFilePreviewServices<*, R>
        ?: error("${BuildSystemFilePreviewServices::class.java} token is not available for $this")
    }
  }
}
