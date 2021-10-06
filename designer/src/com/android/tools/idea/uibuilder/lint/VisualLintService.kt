/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.lint

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.common.error.Issue
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.configurations.AdditionalDeviceService
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.uibuilder.model.NlComponentHelper.registerComponent
import com.android.tools.idea.uibuilder.scene.NlModelHierarchyUpdater.updateHierarchy
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintBaseConfigIssues
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssues
import com.android.tools.idea.uibuilder.visual.visuallint.analyzeAfterModelUpdate
import com.google.common.collect.ImmutableCollection
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.CompletableFuture
import com.intellij.openapi.components.Service
import java.lang.IllegalArgumentException

/**
 * Service that runs visual lints
 */
@Service
class VisualLintService {

  companion object {
    @JvmStatic
    fun getInstance(): VisualLintService? {
      return ApplicationManager.getApplication().getService(VisualLintService::class.java)
    }
  }

  /** Default issue provider for Visual Lint Service. */
  val issueProvider = VisualLintIssueProvider()

  /**
   * Run visual lint analysis and return the list of issues.
   */
  fun runVisualLintAnalysis(models: List<NlModel>, issueModel: IssueModel) {
    issueModel.removeIssueProvider(issueProvider)
    issueProvider.clear()
    if (models.isEmpty()) {
      return
    }

    issueModel.addIssueProvider(issueProvider, false)
    val displayingModel = models[0]
    val devices = AdditionalDeviceService.getInstance()?.getWindowSizeDevices() ?: return

    for (device in devices) {
      val config = Configuration.copy(displayingModel.configuration)
      config.setDevice(device, false)

      val param = InflationParam(
        config,
        displayingModel.project,
        displayingModel.file,
        displayingModel.facet, true)
      inflate(param).thenCompose { result ->
        if (result == null) {
          // already logged error above
          return@thenCompose CompletableFuture.completedFuture(null)
        }

        val inflatedModel = NlModel.builder(param.facet, param.file.virtualFile, param.config)
          .withModelDisplayName("${param.config.device?.displayName}")
          .withModelUpdater(DefaultModelUpdater())
          .withComponentRegistrar { component: NlComponent? -> registerComponent(component!!) }
          .build()

        updateHierarchy(result, inflatedModel)
        analyzeAfterModelUpdate(result, inflatedModel, issueProvider, VisualLintBaseConfigIssues())

        return@thenCompose CompletableFuture.completedFuture(null)
      }.thenAccept {
        // TODO: This might be triggered too frequently (4 times).
        issueModel.updateErrorsList()
      }
    }
  }
}

/**
 * Data needed for inflating a layout
 */
data class InflationParam(val config: Configuration,
                          val project: Project,
                          val file: XmlFile,
                          val facet: AndroidFacet,
                          val logRenderErrors: Boolean = false)

/**
 * Inflate a view, then return the completable future with render result.
 */
fun inflate(param: InflationParam): CompletableFuture<RenderResult> {
  val renderService = RenderService.getInstance(param.project)
  val logger = if (param.logRenderErrors) renderService.createLogger(param.facet) else renderService.nopLogger;

  return renderService.taskBuilder(param.facet, param.config)
    .withPsiFile(param.file)
    .withLayoutScanner(false)
    .withLogger(logger)
    .build().thenCompose { newTask ->
      if (newTask == null) {
        logger.error("INFLATE", "Error inflating view for visual lint on background. No RenderTask Created.",
        null, null, null)
        return@thenCompose CompletableFuture.failedFuture(IllegalArgumentException())
      }

      // TODO: Potentially save this task for future?
      return@thenCompose newTask.inflate().whenComplete { result, inflateException ->
        val exception: Throwable? = inflateException ?: result.renderResult.exception
        if (exception != null || result == null) {
          logger.error("INFLATE", "Error inflating views for visual lint on background", exception, null, null)
        }
      }
    }
}

/** Helper function useful for debugging. */
private fun printBounds(root: ViewInfo, builder: StringBuilder = StringBuilder()): String {
  builder.append("  ${root.className}::wxh = (${root.right - root.left} x ${root.bottom - root.top}})\n")
  root.children.forEach {
    printBounds(it, builder)
  }
  return builder.toString()
}