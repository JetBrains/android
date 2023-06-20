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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.editors.liveedit.LiveEditAdvancedConfiguration
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class LiveEditLogger(val tag: String) {
  internal fun log(message: String) {
    if (!enabled()) {
      return
    }
    println("$tag: $message")
  }

  private fun enabled(): Boolean {
    try {
      return LiveEditAdvancedConfiguration.getInstance().useDebugMode
    } catch (e: Exception) {
      return false;
    }
  }

  internal fun dumpCompilerOutputs(outputs: List<LiveEditCompiledClass>) {
    if (!LiveEditAdvancedConfiguration.getInstance().useDebugMode) {
      return
    }

    for (clazz in outputs) {
      writeDebugToTmp(clazz.name.replace("/".toRegex(), ".") + ".class", clazz.data)
    }
  }

  internal fun dumpDesugarOutputs(outputs: Map<Int, Map<String, ByteArray>>) {
    if (!LiveEditAdvancedConfiguration.getInstance().useDebugMode) {
      return
    }

    for (versionClasses in outputs) {
      val apiLevel = versionClasses.key
      versionClasses.value.forEach {
        val className = it.key
        val data = it.value
        writeDebugToTmp(className.replace("/".toRegex(), ".") + ".$apiLevel.class", data)
      }
    }
  }

  private fun writeDebugToTmp(name: String, data: ByteArray) {
    val tmpPath = System.getProperty("java.io.tmpdir") ?: return
    val path = Paths.get(tmpPath, name)
    try {
      Files.write(path, data)
      log("Wrote debug file at '${path.toAbsolutePath()}'")

    }
    catch (e: IOException) {
      log("Unable to write debug file '${path.toAbsolutePath()}'")
    }
  }
}