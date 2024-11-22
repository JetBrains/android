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
package com.android.tools.idea.journeys

import com.android.ide.common.resources.escape.xml.CharacterDataEscaper
import com.android.tools.idea.journeys.view.JourneysEditorViewImpl.Action
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException

class XmlTagUpdater(private val project: Project, private val file: VirtualFile) {
  suspend fun addNewActionToFile(text: String) {
    readAndWriteAction {
      val actionsTag = getActionsTag()
      writeAction {
        actionsTag?.let {
          val resource: XmlTag =
            it
              .createChildTag(
                "action",
                it.namespace,
                text,
                /* enforceNamespacesDeep= */ false,
              )

          WriteCommandAction.writeCommandAction(project).withName("Adding action ${it.name}").run<Nothing> {
            it.addSubTag(resource, /* first= */ false)
          }
        }
      }
    }
  }

  private fun updateTag(value: String, tag: XmlTag?, parentTag: XmlTag?) {
    if (tag == null || parentTag == null) return
    WriteCommandAction.writeCommandAction(project, tag.containingFile)
      .withName("Setting attribute of ${tag.name}")
      .run<IncorrectOperationException> {
        // Makes the command global even if only one xml file is modified.
        // That way, the Undo is always available from the translation editor.
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
        val escapedXml =
          try {
            CharacterDataEscaper.escape(value)
          } catch (e: IllegalArgumentException) {
            Logger.getInstance(this.javaClass).warn(e)
            value
          }
        val newTag: XmlTag = XmlElementFactory.getInstance(project).createTagFromText("<${escapedXml}></${escapedXml}>")
        tag.value.children.forEach(newTag::add)
        parentTag.addBefore(newTag, tag)
        tag.delete()
      }
  }

  private fun updateTagAttribute(value: String, tagAttribute: String?, tag: XmlTag?) {
    if (tag == null || tagAttribute == null) return
    WriteCommandAction.writeCommandAction(project, tag.containingFile)
      .withName("Setting attribute of ${tag.name}")
      .run<IncorrectOperationException> {
        // Makes the command global even if only one xml file is modified.
        // That way, the Undo is always available from the translation editor.
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
        val escapedXml =
          try {
            CharacterDataEscaper.escape(value)
          } catch (e: IllegalArgumentException) {
            Logger.getInstance(this.javaClass).warn(e)
            value
          }
        tag.getAttribute(tagAttribute)?.setValue(escapedXml)
      }
  }

  private fun updateTagValue(value: String, tag: XmlTag?) {
    if (tag == null) return
    WriteCommandAction.writeCommandAction(project, tag.containingFile)
      .withName("Setting value of ${tag.name}")
      .run<IncorrectOperationException> {
        // Makes the command global even if only one xml file is modified.
        // That way, the Undo is always available from the translation editor.
        CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
        val escapedXml =
          try {
            CharacterDataEscaper.escape(value)
          } catch (e: IllegalArgumentException) {
            Logger.getInstance(this.javaClass).warn(e)
            value
          }
        tag.value.text = escapedXml
      }
  }

  suspend fun updateActionTagValue(row: Int, text: String) {
    readAndWriteAction {
      val actionsTag = getActionsTag()
      val tag = actionsTag?.subTags?.get(row)
      writeAction {
        updateTagValue(text, tag)
      }
    }
  }

  fun updateActionTag(row: Int, action: Action) {
    val actionsTag = getActionsTag()
    val tag = actionsTag?.subTags?.get(row)
    updateTag(action.value, tag, actionsTag)
  }

  suspend fun updateDescription(text: String) {
    readAndWriteAction {
      val descriptionTag = getDescriptionTag()
      writeAction {
        updateTagValue(text, descriptionTag)
      }
    }
  }

  suspend fun updateJourneyName(text: String) {
    readAndWriteAction {
      val rootTag = getRootTag()
      writeAction {
        updateTagAttribute(text, rootTag?.getAttribute("name")?.name, rootTag)
      }
    }
  }

  suspend fun removeAction(row: Int) {
    readAndWriteAction {
      val actionsTag = getActionsTag()
      val tag = actionsTag?.subTags?.get(row)

      writeAction {
        WriteCommandAction.writeCommandAction(project, tag?.containingFile)
          .withName("Deleting action ${tag?.name}")
          .run<IncorrectOperationException> {
            // Makes the command global even if only one xml file is modified.
            // That way, the Undo is always available from the translation editor.
            CommandProcessor.getInstance().markCurrentCommandAsGlobal(project)
            tag?.delete()
          }
      }
    }
  }

  suspend fun moveAction(currentIndex: Int, newIndex: Int) {
    readAndWriteAction {
      val actionsTag = getActionsTag()
      writeAction {
        actionsTag?.let {
          val tagToMove = it.subTags[currentIndex]
          val anchorTag = it.subTags[newIndex]

          WriteCommandAction.writeCommandAction(project).withName("Moving Action").run<IncorrectOperationException> {
            if (newIndex < currentIndex) {
              it.addBefore(tagToMove.copy(), anchorTag)
            } else {
              it.addAfter(tagToMove.copy(), anchorTag)
            }
            tagToMove.delete()
          }
        }
      }
    }
  }

  private fun getRootTag(): XmlTag? {
    return (PsiManager.getInstance(project).findFile(file) as? XmlFile)?.rootTag
  }

  private fun getDescriptionTag(): XmlTag? {
    return getRootTag()?.subTags?.firstOrNull {tag -> tag.name == "description" }
  }

  private fun getActionsTag(): XmlTag? {
    return getRootTag()?.subTags?.firstOrNull {tag -> tag.name == "actions" }
  }
}