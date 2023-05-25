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
package com.android.tools.idea.rendering

import com.android.ide.common.rendering.api.RenderResources
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.util.PathString
import com.android.tools.analytics.crash.CrashReport
import com.android.tools.analytics.crash.CrashReporter
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter
import com.android.tools.idea.diagnostics.crash.StudioExceptionReport
import com.android.tools.idea.log.LogWrapper
import com.android.tools.idea.projectsystem.AndroidProjectSettingsService
import com.android.tools.idea.projectsystem.requiresAndroidModel
import com.android.tools.idea.rendering.parsers.PsiXmlFile
import com.android.tools.idea.ui.GuiTestingService
import com.android.tools.idea.util.toVirtualFile
import com.android.tools.layoutlib.LayoutlibContext
import com.android.tools.rendering.IRenderLogger
import com.android.tools.rendering.ProblemSeverity
import com.android.tools.rendering.RenderProblem
import com.android.tools.rendering.api.EnvironmentContext
import com.android.tools.rendering.api.IncludeReference
import com.android.tools.rendering.api.NavGraphResolver
import com.android.tools.rendering.classloading.ModuleClassLoaderManager
import com.android.tools.rendering.parsers.RenderXmlFile
import com.android.tools.rendering.security.RenderSecurityManager
import com.android.tools.sdk.AndroidPlatform
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.dom.navigation.getStartDestLayoutId
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.jetbrains.android.uipreview.StudioModuleClassLoaderManager

/** Studio-specific implementation of [EnvironmentContext]. */
class StudioEnvironmentContext(private val module: Module) : EnvironmentContext {
  override val layoutlibContext: LayoutlibContext = StudioLayoutlibContext(module.project)

  override val runnableFixFactory: RenderProblem.RunnableFixFactory = ShowFixFactory

  override fun reportMissingSdkDependency(logger: IRenderLogger) {
    val message = RenderProblem.create(ProblemSeverity.ERROR)
    logger.addMessage(message)
    message.htmlBuilder.addLink("No Android SDK found. Please ", "configure", " an Android SDK.",
                                logger.linkManager.createRunnableLink {
                                  val project = module.project
                                  val service = ProjectSettingsService.getInstance(project)
                                  if (project.requiresAndroidModel() && service is AndroidProjectSettingsService) {
                                    (service as AndroidProjectSettingsService).openSdkSettings()
                                    return@createRunnableLink
                                  }
                                  AndroidSdkUtils.openModuleDependenciesConfigurable(module)
                                })
  }

  override fun createIncludeReference(xmlFile: RenderXmlFile, resolver: RenderResources): IncludeReference =
    PsiIncludeReference.get(xmlFile, resolver)

  override fun getFileText(fileName: String): String? {
    val virtualFile = LocalFileSystem.getInstance().findFileByPath(fileName)
    if (virtualFile != null) {
      val psiFile = AndroidPsiUtils.getPsiFileSafely(module.project, virtualFile)
      if (psiFile != null) {
        return if (ApplicationManager.getApplication().isReadAccessAllowed) psiFile.text
        else ApplicationManager.getApplication().runReadAction(
          Computable { psiFile.text } as Computable<String>)
      }
    }
    return null
  }

  override fun getXmlFile(filePath: PathString): RenderXmlFile? {
    val file = filePath.toVirtualFile()
    return file?.let { AndroidPsiUtils.getPsiFileSafely(module.project, it) as? XmlFile }?.let { PsiXmlFile(it) }
  }

  override fun getNavGraphResolver(resourceResolver: ResourceResolver): NavGraphResolver {
    return NavGraphResolver { navGraph -> getStartDestLayoutId(navGraph, module.project, resourceResolver) }
  }

  override fun createRenderSecurityManager(projectPath: String?, platform: AndroidPlatform?): RenderSecurityManager {
    val sdkPath = platform?.sdkData?.location?.toString()

    val securityManager = StudioRenderSecurityManager(sdkPath, projectPath, false)
    securityManager.setLogger(LogWrapper(
      Logger.getInstance(StudioRenderSecurityManager::class.java)).alwaysLogAsDebug(true).allowVerbose(false))
    securityManager.setAppTempDir(PathManager.getTempPath())

    return securityManager
  }

  override fun getOriginalFile(psiFile: PsiFile): PsiFile {
    val renderedVirtualFile: VirtualFile? = psiFile.virtualFile
    if (renderedVirtualFile?.isInLocalFileSystem == false && renderedVirtualFile is BackedVirtualFile) {
      val sourcePsiFile = AndroidPsiUtils.getPsiFileSafely(psiFile.project, renderedVirtualFile.originFile)
      if (sourcePsiFile != null) return sourcePsiFile
    }

    return psiFile
  }

  override fun getModuleClassLoaderManager(): ModuleClassLoaderManager<*> = ModuleClassLoaderManager.get()

  override fun getCrashReporter(): CrashReporter = StudioCrashReporter.getInstance()

  override fun createCrashReport(t: Throwable): CrashReport {
    return StudioExceptionReport.Builder().setThrowable(t, false, true).build()
  }

  // We only track allocations in testing mode
  override fun isInTest(): Boolean = GuiTestingService.getInstance()?.isGuiTestingMode == true ||
                                         ApplicationManager.getApplication()?.isUnitTestMode == true
}