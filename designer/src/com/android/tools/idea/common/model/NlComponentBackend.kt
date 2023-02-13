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

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.xml.XmlTag
import com.intellij.util.IncorrectOperationException

/**
 * Backend impl for interaction with psi elements within NlComponents.
 *
 * Currently only holds xml specific impl.
 * TODO: Update APIs accordingly once refactoring is finished.
 * TODO: Evaluate if psi element dependencies are necessary.
 */
interface NlComponentBackend {

  // TODO: remove
  fun setTagElement(tag: XmlTag)

  // TODO: remove
  @Deprecated("Use getTag", ReplaceWith("getTag()"))
  fun getTagDeprecated(): XmlTag

  /**
   * Returns the [XmlTag] element, or null if the tag was not set or the tag element is no longer valid.
   * Has to be called with read access allowed.
   */
  val tag: XmlTag?

  // TODO: remove
  fun getTagName(): String

  /**
   * Returns the value of an attribute. May block if not run inside a read action.
   * If read access is not allowed, it'll schedule operations on the read-accessible thread.
   * @return attribute of the string value.
   */
  fun getAttribute(attribute: String, namespace: String?): String?

  /**
   * Precondition : Must be called within undo-transparent action. See [NlWriteCommandActionUtil]
   * Attempts to set the value of an attribute. May block if not run inside a write action.
   *
   * @return true if attribute is set. False otherwise.
   * @throws IncorrectOperationException if not called within undo-transparent action
   */
  @Throws(IncorrectOperationException::class)
  fun setAttribute(attribute: String, namespace: String?, value: String?): Boolean

  // TODO: return list later.
  fun getAffectedFile(): VirtualFile?

  // TODO: potentially remove later (by adding directly to transaction commit)
  fun reformatAndRearrange()

  fun isValid(): Boolean
}
