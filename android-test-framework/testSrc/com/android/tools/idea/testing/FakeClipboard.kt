/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.testing

import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.ClipboardOwner
import java.awt.datatransfer.DataFlavor.stringFlavor
import java.awt.datatransfer.Transferable

/** A [Clipboard] that can be used in tests without any dependencies */
class FakeClipboard : Clipboard("fake") {
  override fun setContents(contents: Transferable?, owner: ClipboardOwner?) {
    this.contents = contents
  }

  fun getTextContents() =
    contents.getTransferData(stringFlavor).toString().lines().joinToString("\n") { it }
}
