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
package com.android.tools.nativeSymbolizer

import com.android.sdklib.devices.Abi
import java.io.File

/**
 * A wrapper around a SymbolSource that helps with finding all the symbol files and/or directories
 * for a given cpu architecture. This class does not (and should not) cache any results as the
 * underlying symbol source may update.
 */
class SymbolFilesLocator(private val source: SymbolSource) {
  private companion object {
    // This does not include all the abis in the Abi enum since some ABIs (e.g. MIPS) are not used
    // and are therefore considered "no supported".
    val abis = setOf(Abi.X86, Abi.X86_64, Abi.ARM64_V8A, Abi.ARMEABI, Abi.ARMEABI_V7A)
  }

  /** Gets all directories that contain symbol files. */
  fun getDirectories(cpuArch: String): Collection<File> {
    val dirs = mutableSetOf<File>()

    for (abi in abis.filter { it.cpuArch == cpuArch }) {
      dirs.addAll(source.getDirsFor(abi))
    }

    return dirs.filter { containsSymbolFiles(it) }
  }

  /** Gets all symbol files. */
  fun getFiles(cpuArch: String): List<File> {
    val extensions = setOf("so", "dwo")

    val files = mutableSetOf<File>()

    for (dir in getDirectories(cpuArch)) {
      files.addAll(dir.listFiles() ?: emptyArray())
    }

    return files.filter { extensions.contains(it.extension) }
  }

  /** Checks if a directory actually contains symbol files. */
  private fun containsSymbolFiles(dir: File): Boolean {
    assert(dir != null)

    // It could be possible that a file was accidentally provided to a symbol source (e.g. bad JNI
    // dir definition).
    if (!dir.isDirectory) {
      return false
    }

    val extensions = setOf("so", "dwo")

    val files = dir.listFiles()

    if (files == null) {
      getLogger().warn("Failed to list directory $dir for native symbols. Will ignore it.")
      return false
    }

    return files.any { extensions.contains(it.extension) }
  }
}