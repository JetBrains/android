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

/**
 * Backend impl for interaction with psi elements within NlComponents.
 *
 * Currently only holds xml specific impl.
 * TODO: Update APIs accordingly once refactoring is finished.
 * TODO: Evaluate if psi element dependencies are necessary.
 */
open interface NlComponentBackend {

  // TODO: remove
  fun setTag(tag: XmlTag)

  // TODO: remove
  fun getTag(): XmlTag

  // TODO: remove
  fun getTagPointer(): SmartPsiElementPointer<XmlTag>

  // TODO: remove
  fun setTagName(name: String)

  // TODO: remove
  fun getTagName(): String

  // TODO: return list later.
  fun getAffectedFile(): VirtualFile?

  // TODO: potentially remove later (by adding directly to transaction commit)
  fun reformatAndRearrange()
}