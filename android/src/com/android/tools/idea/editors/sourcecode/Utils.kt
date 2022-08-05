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
package com.android.tools.idea.editors.sourcecode

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.KotlinFileType

/**
 * Returns true if the [VirtualFile] is a Kotlin or Java file.
 */
fun VirtualFile.isSourceFileType() = when (fileType) {
  KotlinFileType.INSTANCE, JavaFileType.INSTANCE -> true
  else -> false
}

/**
 * Extension method that returns if the file is a Kotlin file. This method first checks for the extension to fail fast without having to
 * actually trigger the potentially costly [VirtualFile#fileType] call.
 */
fun VirtualFile.isKotlinFileType(): Boolean =
  extension == KotlinFileType.INSTANCE.defaultExtension && fileType == KotlinFileType.INSTANCE