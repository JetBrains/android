/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.naveditor.dialogs

import com.android.tools.adtui.model.stdui.DefaultCommonTextFieldModel
import com.android.tools.adtui.model.stdui.EditingSupport
import com.android.tools.adtui.model.stdui.EditorCompletion
import com.intellij.xml.util.documentation.MimeTypeDictionary

private val MIME_TYPES = MimeTypeDictionary.HTML_CONTENT_TYPES.toList()

class MimeTypeTextFieldModel : DefaultCommonTextFieldModel("", "e.g. text/html") {
  override val editingSupport =
    object : EditingSupport {
      override val completion: EditorCompletion = { MIME_TYPES }
    }
}
