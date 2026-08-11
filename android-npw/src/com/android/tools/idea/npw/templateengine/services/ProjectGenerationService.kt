/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.npw.templateengine.services

import com.android.template.engine.DependencyInstaller
import com.android.template.engine.TemplateDefinition
import com.android.template.engine.TemplateEngine
import com.android.template.engine.TemplateEngineFactory
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.templateengine.WizardConstants
import com.android.tools.idea.npw.templateengine.api.TemplatePostProcessor
import com.android.tools.idea.npw.templateengine.importer.ProjectImporter
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.APP)
class ProjectGenerationService(private val coroutineScope: CoroutineScope) {

  private val logger = logger<ProjectGenerationService>()

  fun generateProject(
    project: Project?,
    parameters: TemplateEngineProjectParameters,
    template: TemplateDefinition,
    importer: ProjectImporter,
    onComplete: (Project?) -> Unit = {},
  ) {
    coroutineScope.launch {
      val destinationDir = File(parameters.location)
      val predefinedArgs = buildPredefinedArguments()
      val explicitArgs = buildExplicitArguments(parameters)

      // 1. Run file generation in background
      val generationSuccess =
        try {
          withContext(Dispatchers.IO) {
            destinationDir.mkdirs()
            val engine = createTemplateEngine(destinationDir, project)
            engine.processTemplate(template, predefinedArgs, explicitArgs)
          }
          true
        } catch (e: Exception) {
          logger.warn("Failed to generate project files", e)
          false
        }

      if (!generationSuccess) {
        withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) { onComplete(null) }
        return@launch
      }

      // 2. Execute Post-Processors registered via TemplatePostProcessor EP
      withContext(Dispatchers.IO) {
        val registeredProcessors = TemplatePostProcessor.EP_NAME.extensionList.associateBy { it.id }
        val paramMap = explicitArgs + predefinedArgs
        for (processorId in template.metadata.tags) {
          registeredProcessors[processorId]?.let { processor ->
            try {
              processor.onProjectCreated(project, destinationDir, paramMap)
            } catch (e: Exception) {
              logger.warn("PostProcessor $processorId failed", e)
            }
          }
        }
      }

      // 3. Hand off project import and completion callback to EDT asynchronously in nonModal context
      withContext(Dispatchers.EDT + ModalityState.nonModal().asContextElement()) {
        val createdProject =
          try {
            importer.importProject(parameters.name, destinationDir)
          } catch (e: Exception) {
            logger.warn("Failed to import project", e)
            null
          }
        onComplete(createdProject)
      }
    }
  }

  private fun createTemplateEngine(destinationDir: File, project: Project? = null): TemplateEngine {
    val factory = TemplateEngineFactory.createDefault()
    return factory.createDefaultEngine(
      messageSink = TemplateRegistryService.messageSink,
      dependencyInstaller =
        object : DependencyInstaller {
          override fun installAndroidSdkPackage(packagePath: String) {
            logger.info("Checking Android SDK package: $packagePath")
            val normalizedPath = packagePath.replace('/', ';')
            val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
            val progress = StudioLoggerProgressIndicator(ProjectGenerationService::class.java)
            val repoPackages = sdkHandler.getRepoManagerAndLoadSynchronously(progress).packages
            if (repoPackages.localPackages.containsKey(normalizedPath)) {
              return
            }
            if (!repoPackages.remotePackages.containsKey(normalizedPath)) {
              logger.info("SDK package '$normalizedPath' is not available on remote repository; skipping quickfix.")
              return
            }
            if (ApplicationManager.getApplication().isUnitTestMode) {
              return
            }
            var installed = false
            ApplicationManager.getApplication().invokeAndWait {
              val dialog = SdkQuickfixUtils.createDialogForPaths(project, listOf(normalizedPath))
              installed = dialog?.showAndGet() == true
            }
            if (!installed) {
              logger.warn("Required SDK package '$packagePath' was not installed.")
            }
          }
        },
      destinationPathProvider = { destinationDir.toPath() },
    )
  }

  companion object {
    fun getInstance(): ProjectGenerationService = service()
  }
}

data class TemplateEngineProjectParameters(val name: String, val packageName: String, val location: String, val minSdk: String)

fun buildPredefinedArguments(): Map<String, String> {
  val sdkPath = IdeSdks.getInstance().androidSdkPath?.absolutePath ?: ""
  return mapOf(WizardConstants.KEY_SDK_PATH to sdkPath)
}

fun buildExplicitArguments(userParams: TemplateEngineProjectParameters): Map<String, String> {
  val apiLevel = StudioFlags.NPW_COMPILE_SDK_VERSION.get()

  return mapOf(
    WizardConstants.KEY_NAME to userParams.name,
    WizardConstants.KEY_APPLICATION_ID to userParams.packageName,
    WizardConstants.KEY_NAMESPACE to userParams.packageName,
    WizardConstants.KEY_MIN_SDK to userParams.minSdk,
    WizardConstants.KEY_COMPILE_SDK to apiLevel.majorVersion.toString(),
    WizardConstants.KEY_COMPILE_SDK_MINOR to if (apiLevel.minorVersion > 0) apiLevel.minorVersion.toString() else "",
  )
}
