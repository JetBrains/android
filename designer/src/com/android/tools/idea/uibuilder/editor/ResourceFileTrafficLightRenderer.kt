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
package com.android.tools.idea.uibuilder.editor

import com.android.tools.idea.common.error.IssuePanelService
import com.android.tools.idea.common.error.IssueProviderListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.res.isInResourceSubdirectory
import com.intellij.codeInsight.daemon.impl.ErrorStripeUpdateManager
import com.intellij.codeInsight.daemon.impl.TrafficLightRenderer
import com.intellij.codeInsight.daemon.impl.TrafficLightRendererContributor
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorMarkupModelImpl
import com.intellij.openapi.editor.markup.AnalyzerStatus
import com.intellij.openapi.editor.markup.StatusItem
import com.intellij.openapi.editor.markup.UIController
import com.intellij.psi.PsiFile
import com.intellij.spellchecker.SpellCheckerSeveritiesProvider
import icons.StudioIcons

private val SEVERITY_TO_ICON = mapOf(
  Pair(SpellCheckerSeveritiesProvider.TYPO, StudioIcons.Common.TYPO_STACK),
  Pair(HighlightSeverity.WEAK_WARNING, StudioIcons.Common.WEAK_WARNING_STACK),
  Pair(HighlightSeverity.WARNING, StudioIcons.Common.WARNING_STACK),
  Pair(HighlightSeverity.ERROR, StudioIcons.Common.ERROR_STACK)
)

/**
 * Custom [TrafficLightRenderer] to be used by resource files.
 * It shows the number of errors, warnings... displayed in the Design Tools tab of the error panel if there are Visual Lint issues.
 */
class ResourceFileTrafficLightRender(file: PsiFile, editor: Editor) : TrafficLightRenderer(file.project, editor.document) {
  private val severities = severityRegistrar.allSeverities
  private val errorCountArray = IntArray(severities.size)

  init {
    val messageBusConnection = project.messageBus.connect(this)
    messageBusConnection.subscribe(IssueProviderListener.TOPIC, IssueProviderListener { _, _ ->
      ApplicationManager.getApplication().invokeLater {
        if (!project.isDisposed) {
          ErrorStripeUpdateManager.getInstance(project).repaintErrorStripePanel(editor, file)
        }
      }
    })
  }

  override fun refresh(editorMarkupModel: EditorMarkupModelImpl?) {
    super.refresh(editorMarkupModel)
    if (editorMarkupModel == null) {
      return
    }
    errorCountArray.fill(0)
    val issues = IssuePanelService.getInstance(project).getSharedPanelIssues() ?: return
    issues.forEach {
      val index = severities.indexOf(it.severity)
      if (index > -1) {
        errorCountArray[index]++
      }
    }
  }

  override fun getErrorCounts(): IntArray {
    return errorCountArray
  }

  override fun getStatus(): AnalyzerStatus {
    val status = super.getStatus()
    val nonZeroSeverities = errorCountArray.indices.reversed().filterNot { errorCountArray[it] == 0 }.map {
      severityRegistrar.getSeverityByIndex(it)
    }
    val items = mutableListOf<StatusItem>()
    val currentItems = status.expandedStatus
    if (currentItems.size != nonZeroSeverities.size) {
      return status
    }
    for (index in currentItems.indices) {
      val item = currentItems[index]
      val icon = SEVERITY_TO_ICON[nonZeroSeverities[index]] ?: item.icon
      items.add(StatusItem(item.text, icon, item.detailsText))
    }
    status.withExpandedStatus(items)
    return status
  }

  override fun createUIController(): UIController {
    return ResourceFileUIController()
  }

  inner class ResourceFileUIController : DefaultUIController() {
    override fun toggleProblemsView() {
      val issuePanelService = IssuePanelService.getInstance(project)
      issuePanelService.setSharedIssuePanelVisibility(!issuePanelService.isShowingIssuePanel(null))
    }
  }
}

class ResourceFileTrafficLightRendererContributor : TrafficLightRendererContributor {
  override fun createRenderer(editor: Editor, file: PsiFile?): TrafficLightRenderer? {
    if (!StudioFlags.NELE_USE_CUSTOM_TRAFFIC_LIGHTS_FOR_RESOURCES.get()) {
      return null
    }
    // Use this customized renderer only for resource files, returning null means that the default renderer will be used.
    return ReadAction.compute<TrafficLightRenderer?, RuntimeException> {
      file?.let {
        if (isInResourceSubdirectory(it)) {
          ResourceFileTrafficLightRender(it, editor)
        }
        else {
          null
        }
      }
    }
  }
}
