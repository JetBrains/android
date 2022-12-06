/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlDependencyManager
import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.uibuilder.model.NlComponentHelper
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.util.androidFacet
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.android.facet.AndroidFacet

/**
 * Action that shows a dialog to change the tag name of a component
 */
class MorphComponentAction @JvmOverloads constructor(
  component: NlComponent,
  private val getMorphSuggestions: (NlComponent) -> List<String> = MorphManager::getMorphSuggestion)
  : AnAction("Convert view...") {

  private val myNlComponent = component
  private var myNewName = component.tagName

  /**
   * Apply the provided tag name to the component in the model
   */
  @UiThread
  private fun applyTagEdit(newTagName: String) {
    val project = myNlComponent.model.project
    val facet = myNlComponent.model.facet
    val dependencyManager = NlDependencyManager.getInstance()
    val component = NlComponent(myNlComponent.model, XmlTagUtil.createTag(myNlComponent.model.project, "<$newTagName/>"))
    NlComponentRegistrar.accept(component)
    dependencyManager.addDependencies(listOf(component), facet, true) { editTagNameAndAttributes(project, newTagName) }
  }

  /**
   * Edit the tag name and remove the attributes that are not needed anymore.
   */
  @UiThread
  private fun editTagNameAndAttributes(project: Project, newTagName: String) {
    DumbService.getInstance(project).runWhenSmart {
      NlWriteCommandActionUtil.run(myNlComponent, "Convert " + myNlComponent.tagName + " to ${newTagName.split(".").last()}") {
        myNlComponent.tagDeprecated.name = newTagName
        myNlComponent.removeObsoleteAttributes()
        myNlComponent.children.forEach(NlComponent::removeObsoleteAttributes)
      }
    }
  }

  private fun createMorphPopup(morphPanel: MorphPanel): JBPopup {
    val popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(morphPanel, morphPanel.preferredFocusComponent)
      .setMovable(true)
      .setFocusable(true)
      .setRequestFocus(true)
      .setShowShadow(true)
      .setCancelOnClickOutside(true)
      .setAdText("Set the new type for the selected View")
      .createPopup()

    morphPanel.setOkAction {
      applyTagEdit(it)
      popup.closeOk(null)
    }
    return popup
  }

  override fun actionPerformed(e: AnActionEvent) {
    val oldTagName = myNlComponent.tagName
    val project = myNlComponent.model.project
    val facet = myNlComponent.model.facet
    val morphSuggestion = getMorphSuggestions(myNlComponent)
    val morphDialog = MorphPanel(facet, project, oldTagName, morphSuggestion)
    morphDialog.setTagNameChangeConsumer { newName ->
      myNewName = newName
    }
    createMorphPopup(morphDialog).showCenteredInCurrentWindow(project)
  }
}

