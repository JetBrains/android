/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.editor.multirepresentation.sourcecode

import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType

fun VirtualFile.hasSourceFileExtension() = when (extension) {
  KotlinFileType.INSTANCE.defaultExtension, StdFileTypes.JAVA.defaultExtension -> true
  else -> false
}