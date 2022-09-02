/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.util.device.parser

import com.android.tools.idea.compose.preview.util.device.DeviceSpecFileType
import com.android.tools.idea.compose.preview.util.device.DeviceSpecLanguage
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

val DEVICE_SPEC_FILE_TYPE = IFileElementType(DeviceSpecLanguage)

class DeviceSpecTokenType(debugName: String) : IElementType(debugName, DeviceSpecLanguage)

class DeviceSpecElementType(debugName: String) : IElementType(debugName, DeviceSpecLanguage)

class DeviceSpecPsiFile(viewProvider: FileViewProvider) :
  PsiFileBase(viewProvider, DeviceSpecLanguage) {
  override fun getFileType(): FileType = DeviceSpecFileType
}
