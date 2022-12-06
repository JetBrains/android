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

import com.android.tools.idea.util.ReformatUtil
import com.android.utils.TraceUtils
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag
import org.jetbrains.annotations.TestOnly

private val LOGGER = Logger.getInstance(NlComponentBackendXml::class.java)

open class NlComponentBackendXml private constructor(
  private val myProject: Project) : NlComponentBackend {
  //TODO(b/70264883): remove this reference to XmlTag to avoid problems with invalid Psi elements
  private lateinit var myTag: XmlTag
  private lateinit var myTagName: String
  private lateinit var myTagPointer: SmartPsiElementPointer<XmlTag>

  companion object {
    val DEBUG = false

    @TestOnly
    fun getForTest(project: Project, xmlTag: XmlTag) = NlComponentBackendXml(project, xmlTag)
  }

  internal constructor(project: Project, tag: XmlTag) : this(project) {
    myTag = tag
    myTagName = tag.name
    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed) {
      myTagPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(myTag)
    }
    else {
      application.runReadAction {
        myTagPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(myTag)
      }
    }
  }

  @VisibleForTesting
  constructor(project: Project, tag: XmlTag, pointer: SmartPsiElementPointer<XmlTag>) : this(project) {
    myTag = tag
    myTagName = tag.name
    myTagPointer = pointer
  }

  override fun setTagElement(tag: XmlTag) {
    // HACK: see getTag
    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed) {
      if (tag.isValid) {
        myTagPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(tag)
        myTagName = tag.getName()
      }
    }
    else {
      application.runReadAction {
        if (tag.isValid()) {
          myTagPointer = SmartPointerManager.getInstance(myProject).createSmartPsiElementPointer(tag)
          myTagName = tag.getName()
        }
      }
    }
    myTag = tag
  }

  override fun getTagDeprecated(): XmlTag {
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

  override val tag: XmlTag?
    get() {
      ApplicationManager.getApplication().assertReadAccessAllowed()
      val tag = myTagPointer.element
      return if (tag != null && tag.isValid) tag else null
    }

  override fun getTagPointer(): SmartPsiElementPointer<XmlTag> {
    return myTagPointer
  }

  override fun setTagName(name: String) {
    myTagName = name
    tag?.name = name
  }

  override fun getTagName(): String {
    return myTagName
  }

  override fun getAffectedFile(): VirtualFile? {
    val application = ApplicationManager.getApplication()
    return if (application.isReadAccessAllowed) {
      myTagPointer.element?.containingFile?.virtualFile
    }
    else {
      application.runReadAction(Computable { myTagPointer.element?.containingFile?.virtualFile })
    }
  }

  override fun getAttribute(attribute: String, namespace: String?): String? {
    val application = ApplicationManager.getApplication()
    if (application.isReadAccessAllowed) {
      return getAttributeImpl(attribute, namespace)
    }
    return application.runReadAction(Computable { getAttributeImpl(attribute, namespace) })
  }

  private fun getAttributeImpl(attribute: String, namespace: String?): String? {
    val xmlTag = tag
    if (xmlTag == null) {
      LOGGER.debug(
        "Unable to get attribute from ${getTagName()} because XmlTag is invalidated ${getStackTrace()}")
      return null
    }
    return xmlTag.getAttributeValue(attribute, namespace)
  }

  override fun setAttribute(attribute: String, namespace: String?, value: String?): Boolean {
    val application = ApplicationManager.getApplication()
    if (!application.isWriteAccessAllowed) {
      // We shouldn't allow write to be performed outside the WriteCommandAction.
      LOGGER.warn(
        "Unable to set attribute to ${getTagName()}. SetAttribute must be called within undo-transparent action ${getStackTrace()}")
      return false
    }

    val xmlTag = tag
    if (xmlTag == null) {
      LOGGER.debug(
        "Unable to set attribute to ${getTagName()} because XmlTag is invalidated ${getStackTrace()}")
      return false
    }
    return xmlTag.setAttribute(attribute, namespace, value) != null
  }

  override fun reformatAndRearrange() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val xmlTag = myTagPointer.element
    if (xmlTag?.containingFile?.virtualFile == null) {
      LOGGER.debug(
        "Not reformatting ${getTagName()} because its virtual file is null ${getStackTrace()}")
      return
    }

    ReformatUtil.reformatAndRearrange(myProject, xmlTag)
  }

  override fun isValid(): Boolean {
    return ApplicationManager.getApplication().isReadAccessAllowed && myTagPointer.element?.isValid == true
  }

  private fun getStackTrace(): String {
    if (!DEBUG) {
      return ""
    }
    return "\n" + TraceUtils.getCurrentStack(1)
  }
}
