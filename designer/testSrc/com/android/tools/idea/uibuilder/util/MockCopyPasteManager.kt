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
package com.android.tools.idea.uibuilder.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Document
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

private const val NOT_IMPLEMENTED = "Not Implemented"

class MockCopyPasteManager : CopyPasteManager() {
  var current: Transferable? = null

  override fun setContents(content: Transferable) {
    current = content
  }

  override fun getContents() = current

  override fun getAllContents() = current?.let { arrayOf(it) } ?: emptyArray()

  override fun <T : Any?> getContents(flavor: DataFlavor): T? {
    @Suppress("UNCHECKED_CAST") return current?.isDataFlavorSupported(flavor) as? T
  }

  override fun areDataFlavorsAvailable(vararg flavors: DataFlavor?): Boolean {
    return flavors.all { current?.isDataFlavorSupported(it) ?: false }
  }

  override fun addContentChangedListener(listener: ContentChangedListener) = error(NOT_IMPLEMENTED)

  override fun addContentChangedListener(
    listener: ContentChangedListener,
    parentDisposable: Disposable,
  ) = error(NOT_IMPLEMENTED)

  override fun removeContentChangedListener(listener: ContentChangedListener) =
    error(NOT_IMPLEMENTED)

  override fun stopKillRings() = error(NOT_IMPLEMENTED)

  override fun stopKillRings(document: Document) = error(NOT_IMPLEMENTED)

  override fun isCutElement(element: Any?): Boolean = error(NOT_IMPLEMENTED)
}
