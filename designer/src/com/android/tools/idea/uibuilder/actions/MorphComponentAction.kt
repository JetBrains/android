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

import com.android.tools.idea.common.command.NlWriteCommandActionUtil
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlDependencyManager
import com.android.tools.idea.common.util.XmlTagUtil
import com.android.tools.idea.uibuilder.model.NlComponentHelper
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import org.jetbrains.android.facet.AndroidFacet

/**
 * Action that shows a dialog to change the tag name of a component
 */
class MorphComponentAction(component: NlComponent)
  : AnAction("Convert view...") {

  private val myFacet: AndroidFacet = component.model.facet
  private val myProject = component.model.project
  private val myNlComponent = component
  private var myNewName = component.tagName

  /**
   * Apply the provided tag name to the component in the model
   */
  private fun applyTagEdit(newTagName: String) {
    val dependencyManager = NlDependencyManager.getInstance()
    val component = NlComponent(myNlComponent.model, XmlTagUtil.createTag(myNlComponent.model.project, "<$newTagName/>"))
    NlComponentRegistrar.accept(component)
    dependencyManager.addDependencies(listOf(component), myFacet, true) { editTagNameAndAttributes(newTagName) }
  }

  /**
   * Edit the tag name and remove the attributes that are not needed anymore.
   */
  private fun editTagNameAndAttributes(newTagName: String) {
    DumbService.getInstance(myProject).runWhenSmart {
      NlWriteCommandActionUtil.run(myNlComponent, "Convert " + myNlComponent.tagName + " to ${newTagName.split(".").last()}") {
        myNlComponent.tagDeprecated.name = newTagName
        TransactionGuard.getInstance().submitTransactionAndWait {
          myNlComponent.removeObsoleteAttributes()
          myNlComponent.children.forEach(NlComponent::removeObsoleteAttributes)
        }
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
      applyTagEdit(myNewName)
      popup.closeOk(null)
    }
    return popup
  }

  private fun showMorphPopup() {
    val oldTagName = myNlComponent.tagName
    val morphSuggestion = MorphManager.getMorphSuggestion(myNlComponent)
    val morphDialog = MorphPanel(myFacet, myProject, oldTagName, morphSuggestion)
    morphDialog.setTagNameChangeConsumer { newName ->
      myNewName = newName
    }
    createMorphPopup(morphDialog).showCenteredInCurrentWindow(myProject)
  }

  override fun actionPerformed(e: AnActionEvent) = showMorphPopup()
}

