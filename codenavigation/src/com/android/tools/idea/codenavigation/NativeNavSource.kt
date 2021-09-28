/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.codenavigation

import com.android.tools.nativeSymbolizer.NativeSymbolizer
import com.intellij.build.FileNavigatable
import com.intellij.build.FilePosition
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import java.io.File
import java.io.IOException

class NativeNavSource(private val project: Project,
                      private val symbolizer: NativeSymbolizer): NavSource {
  override fun lookUp(location: CodeLocation, arch: String?): Navigatable? {
    if (!location.isNativeCode || location.fileName == null || arch == null) {
      return null
    }

    return try {
      val symbol = symbolizer.symbolize(arch,
                                        File(location.fileName),
                                        location.nativeVAddress)

      if (symbol == null) {
        null
      } else {
        FileNavigatable(project, FilePosition(File(symbol.sourceFile), symbol.lineNumber - 1, 0))
      }
    }
    catch (e: IOException) {
      null
    }
  }
}