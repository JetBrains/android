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
package com.android.tools.idea.common.model

import com.android.tools.idea.common.command.NlWriteCommandAction
import com.android.tools.idea.templates.TemplateUtils
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag
import kotlin.properties.Delegates

open class NlComponentBackendXml private constructor(
  private val myModel: NlModel) : NlComponentBackend {
  //TODO(b/70264883): remove this reference to XmlTag to avoid problems with invalid Psi elements
  private lateinit var myTag: XmlTag
  private lateinit var myTagName: String
  private lateinit var myTagPointer: SmartPsiElementPointer<XmlTag>

  internal constructor(model: NlModel, tag: XmlTag) : this(model) {
    myTag = tag
    myTagName = tag.name
    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed) {
      myTagPointer = SmartPointerManager.getInstance(myModel.project).createSmartPsiElementPointer<XmlTag>(myTag)
    }
    else {
      application.runReadAction {
        myTagPointer = SmartPointerManager.getInstance(myModel.project).createSmartPsiElementPointer<XmlTag>(myTag)
      }
    }
  }

  internal constructor(model: NlModel, tag: XmlTag, pointer: SmartPsiElementPointer<XmlTag>) : this(model) {
    myTag = tag
    myTagName = tag.name
    myTagPointer = pointer
  }

  override fun setTag(tag: XmlTag) {
    // HACK: see getTag
    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed) {
      if (tag.isValid()) {
        myTagPointer = SmartPointerManager.getInstance(myModel.project)
          .createSmartPsiElementPointer(tag)
        myTagName = tag.getName()
      }
    }
    else {
      application.runReadAction {
        if (tag.isValid()) {
          myTagPointer = SmartPointerManager.getInstance(myModel.project)
            .createSmartPsiElementPointer(tag)
          myTagName = tag.getName()
        }
      }
    }
    myTag = tag
  }

  override fun getTag(): XmlTag {
    // HACK: We want to use SmartPsiElementPointer as they make sure that the XmlTag we return here is not invalid.
    // However, SmartPsiElementPointer.getElement can return null when the underlying Psi element has been deleted. Since this method is
    // annotated @NotNull, we return the original tag if the pointer gives a null result.
    // We do this because the large usage of getTag makes it very risky for the moment to take care everywhere of a possible null value.
    //TODO(b/70264883): Fix this properly by using more generally SmartPsiElementPointer instead of XmlTag in the layout editor codebase.
    val tag: XmlTag?
    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed) {
      tag = myTagPointer.element
    }
    else {
      tag = application.runReadAction(Computable<XmlTag> { myTagPointer.element })
    }
    return tag ?: this.myTag
  }

  override fun getTagPointer(): SmartPsiElementPointer<XmlTag> {
    return myTagPointer
  }

  override fun setTagName(name: String) {
    myTagName = name
    getTag().name = name
  }

  override fun getTagName(): String {
    return myTagName
  }

  override fun getAffectedFile(): VirtualFile? {
    return getTag().containingFile?.virtualFile
  }

  override fun reformatAndRearrange() {
    if (getAffectedFile() == null) {
      Logger.getInstance(NlWriteCommandAction::class.java).warn("Not reformatting ${getTagName()} because its virtual file is null")
      return
    }

    TemplateUtils.reformatAndRearrange(myModel.project, getTag())
  }
}