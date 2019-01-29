/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property2

import com.android.SdkConstants.*
import com.android.annotations.VisibleForTesting
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.lint.detector.api.stripIdPrefix
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.DialogWrapper.CANCEL_EXIT_CODE
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.openapi.ui.Messages
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.text.nullize
import org.jetbrains.android.dom.attrs.AttributeDefinition
import org.jetbrains.android.dom.wrappers.ValueResourceElementWrapper
import org.jetbrains.annotations.TestOnly
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JLabel
import javax.swing.JPanel

@VisibleForTesting
const val NO_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE

@VisibleForTesting
const val PREVIEW_EXIT_CODE = DialogWrapper.NEXT_USER_EXIT_CODE + 1

class NeleIdPropertyItem(model: NelePropertiesModel, definition: AttributeDefinition?, components: List<NlComponent>) :
  NelePropertyItem(ANDROID_URI, ATTR_ID, NelePropertyType.ID, definition, "", model, listOf(components.first())) {

  override var value: String?
    get() = stripIdPrefix(super.value)
    set(value) {
      val oldId = stripIdPrefix(super.value)
      val newId = stripIdPrefix(value)
      val newValue = toValue(newId)
      val tag = firstTag
      val attribute = if (tag != null && tag.isValid) tag.getAttribute(ATTR_ID, ANDROID_URI) else null
      val xmlValue = attribute?.valueElement

      if (!renameRefactoring(xmlValue, oldId, newId, newValue)) {
        super.value = newValue
      }
    }

  @set:TestOnly
  var renameProcessorProvider: (Project, XmlAttributeValue, String) -> RenameProcessor =
    { project, value, newValue -> RenameProcessor(project, ValueResourceElementWrapper(value), newValue, false, false) }

  @set:TestOnly
  var dialogProvider: (Project) -> DialogBuilder =
    { project -> DialogBuilder(project) }

  private fun toValue(id: String): String? {
    return if (id.isNotEmpty() && !id.startsWith("@")) NEW_ID_PREFIX + id else id.nullize()
  }

  override fun getCompletionValues(): List<String> {
    return emptyList()
  }

  private fun renameRefactoring(value: XmlAttributeValue?, oldId: String, newId: String, newValue: String?): Boolean {
    if (oldId.isEmpty() || newId.isEmpty() ||
        newValue == null || value == null || !value.isValid ||
        choiceForNextRename == RefactoringChoice.NO) {
      return false
    }

    // Exact replace only
    val project = model.facet.module.project
    val processor = renameProcessorProvider(project, value, newValue)

    // Do a quick usage search to see if we need to ask about renaming
    val usages = processor.findUsages()
    if (usages.isEmpty()) {
      return false
    }
    var choice = choiceForNextRename
    if (choice == RefactoringChoice.ASK) {
      choice = showRenameDialog(project)
    }

    when (choice) {
      RefactoringChoice.YES -> processor.setPreviewUsages(false)
      RefactoringChoice.PREVIEW -> processor.setPreviewUsages(true)
      RefactoringChoice.CANCEL -> return true
      else -> return false
    }
    processor.run()
    return true
  }

  private fun showRenameDialog(project: Project): RefactoringChoice {
    val builder = dialogProvider(project)
    builder.setTitle("Update Usages?")
    val panel = JPanel(BorderLayout())
    val label = JLabel("""
      <html>Update usages as well?<br>
          This will update all XML references and Java R field references.<br>
          <br>
      </html>""".trimIndent())
    panel.add(label, BorderLayout.CENTER)
    val checkBox = JBCheckBox("Don't ask again during this session")
    panel.add(checkBox, BorderLayout.SOUTH)
    builder.setCenterPanel(panel)
    builder.setDimensionServiceKey("idPropertyDimension")
    builder.removeAllActions()

    val yesAction = builder.addOkAction()
    yesAction.setText(Messages.YES_BUTTON)

    builder.addActionDescriptor(DialogBuilder.ActionDescriptor { dialogWrapper ->
                                  object : AbstractAction(Messages.NO_BUTTON) {
                                    override fun actionPerformed(actionEvent: ActionEvent) {
                                      dialogWrapper.close(NO_EXIT_CODE)
                                    }
                                  }
                                })

    builder.addActionDescriptor(DialogBuilder.ActionDescriptor { dialogWrapper ->
                                  object : AbstractAction("Preview") {
                                    override fun actionPerformed(actionEvent: ActionEvent) {
                                      dialogWrapper.close(PREVIEW_EXIT_CODE)
                                    }
                                  }
                                })

    builder.addCancelAction()
    val exitCode = builder.show()

    val choice = when (exitCode) {
      OK_EXIT_CODE -> RefactoringChoice.YES
      NO_EXIT_CODE -> RefactoringChoice.NO
      PREVIEW_EXIT_CODE -> RefactoringChoice.PREVIEW
      CANCEL_EXIT_CODE -> RefactoringChoice.CANCEL
      else -> choiceForNextRename
    }
    if (checkBox.isSelected && choice != RefactoringChoice.CANCEL) {
      choiceForNextRename = choice
    }
    return choice
  }

  companion object {
    // TODO move this static field to a PropertiesComponent setting (need a UI to reset)
    enum class RefactoringChoice { ASK, NO, YES, PREVIEW, CANCEL }

    var choiceForNextRename = RefactoringChoice.ASK
  }
}
