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
@file:JvmName("ResourceFilesUtil")
package com.android.tools.idea.res

import com.android.resources.ResourceFolderType
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Studio Independent resource folder util functions
 */
fun getFolderType(file: VirtualFile?): ResourceFolderType? = file?.parent?.let { ResourceFolderType.getFolderType(it.name) }

/**
 * Checks if the given path points to a file resource. The resource path can point
 * to either file on disk, or a ZIP file entry. If the candidate path contains
 * "file:" or "apk:" scheme prefix, the method returns true without doing any I/O.
 * Otherwise, the local file system is checked for existence of the file.
 */
fun isFileResource(candidatePath: String): Boolean =
  candidatePath.startsWith("file:") || candidatePath.startsWith("apk:") || candidatePath.startsWith("jar:") ||
  File(candidatePath).isFile