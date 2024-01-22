/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.rendering.api

import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.util.PathString
import com.android.tools.analytics.crash.CrashReport
import com.android.tools.analytics.crash.CrashReporter
import com.android.tools.fonts.DownloadableFontCacheService
import com.android.tools.layoutlib.LayoutlibContext
import com.android.tools.rendering.IRenderLogger
import com.android.tools.rendering.RenderProblem
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.parsers.RenderXmlFile
import com.android.tools.rendering.security.RenderSecurityManager
import com.android.tools.sdk.AndroidPlatform
import com.intellij.psi.PsiFile

/**
 * An interface proving access to the general environment specific functionality, primarily related
 * to Intellij IDEA. The interface itself is Intellij/Studio agnostic so that when used outside of
 * studio this can be easily stubbed/nooped or implemented differently.
 *
 * In the future, functionality related to DumbService, read/writeAction etc. can be added here.
 */
interface EnvironmentContext {
  val layoutlibContext: LayoutlibContext

  val actionFixFactory: RenderProblem.ActionFixFactory

  fun reportMissingSdkDependency(logger: IRenderLogger)

  fun createIncludeReference(xmlFile: RenderXmlFile, resolver: RenderResources): IncludeReference

  fun getFileText(fileName: String): String?

  fun getXmlFile(filePath: PathString): RenderXmlFile?

  fun getNavGraphResolver(resourceResolver: ResourceResolver): NavGraphResolver

  /** Returns a [RenderSecurityManager] for the SDK path and project path. */
  fun createRenderSecurityManager(
    projectPath: String?,
    platform: AndroidPlatform?,
  ): RenderSecurityManager

  fun getOriginalFile(psiFile: PsiFile): PsiFile

  fun getModuleClassLoaderManager(): ModuleClassLoaderManager<*>

  fun getCrashReporter(): CrashReporter

  fun createCrashReport(t: Throwable): CrashReport

  fun isInTest(): Boolean

  val downloadableFontCacheService: DownloadableFontCacheService
}
