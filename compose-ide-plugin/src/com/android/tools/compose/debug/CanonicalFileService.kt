/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.compose.debug

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level.PROJECT
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.JarFileSystem
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.psi.KtFile

@Service(PROJECT)
class CanonicalFileService @VisibleForTesting constructor(private val jarDetector: JarDetector) {
  private val fileCache = ConcurrentHashMap<String, KtFile>()

  constructor(
    @Suppress("unused") // required for a Project Service
    project: Project
  ) : this(JarDetector { it.isInJar })

  fun getCanonicalFile(file: KtFile): KtFile {
    return when {
      !StudioFlags.COMPOSE_CLASS_NAME_CALCULATOR_CANONICAL_FILE_CACHE.get() -> file
      jarDetector.isFileInJar(file) -> handleJarFile(file)
      else -> handleSourceFile(file)
    }
  }

  // Use file from cache if exists, otherwise, use self and update cache
  private fun handleJarFile(file: KtFile): KtFile {
    return fileCache.getOrPut(file.key) { file }
  }

  // Prefer source files over jar files so use self and update cache
  private fun handleSourceFile(file: KtFile): KtFile {
    fileCache[file.key] = file
    return file
  }

  fun interface JarDetector {
    fun isFileInJar(file: KtFile): Boolean
  }
}

private val KtFile.isInJar
  get() = virtualFile.fileSystem is JarFileSystem

private val KtFile.key
  get() = "${packageFqName.asString()}.$name"
