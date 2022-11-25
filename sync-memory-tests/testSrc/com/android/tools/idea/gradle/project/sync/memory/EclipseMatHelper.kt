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
package com.android.tools.idea.gradle.project.sync.memory

import com.android.testutils.TestUtils
import com.intellij.util.io.createDirectories
import java.io.File
import kotlin.io.path.absolutePathString

/**
 * Invokes Eclipse Memory Analyzer to inspect the contents of an hprof file.
 *
 * More info at: https://help.eclipse.org/latest/topic/org.eclipse.mat.ui.help/welcome.html
 */
class EclipseMatHelper(private val scriptPath: String = "prebuilts/tools/common/eclipse-mat/ParseHeapDump.sh") {
  /** Returns the retained heap size by invoking the tool and parsing the report generated. */
  fun getHeapDumpSize(hprofPath: String) : Long{
    check(hprofPath.endsWith(".hprof") )

    // See https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.mat.ui.help%2Freference%2Finspections%2Fquery_report.html for
    // example usage of the following script.
    exec(scriptPath,
         "-vm", "${getJdkPath()}/bin",
         "-configuration", CONFIGURATION_DIRECTORY,
         hprofPath,
         "-format=txt",
         "-unzip",
         "-command=heap_dump_overview",
         "org.eclipse.mat.api:query",
         "-vmargs", "-Xmx8g"
    )
    // The script will generate the result relative the input file name as follows.
    // Unfortunately there is no option to give it an output path instead, so the behavior is hardcoded.
    val resultFile = File(hprofPath.replace(".hprof", RELATIVE_RESULT_PATH))

    return resultFile.readLines().firstNotNullOf {
      USED_HEAP_DUMP_PATTERN.findAll(it).firstOrNull()?.groups
    }[GROUP_NAME]!!.value.sizeInBytes()
  }

  private fun getJdkPath(): String {
    val jdk = if (System.getProperty("java.version", "").startsWith("17")) "jdk17" else "jdk11"
    return TestUtils.getWorkspaceRoot().resolve("prebuilts/studio/jdk/${jdk}/linux").absolutePathString()
  }

  private fun String.sizeInBytes() : Long {
    val (sizeStr, unit) = this.split(" ")
    val size = sizeStr.toDouble()
    return when (unit) {
      "B" -> size
      "KB" -> size * (1L shl 10)
      "MB" -> size * (1L shl 20)
      "GB" -> size * (1L shl 30)
      else -> throw IllegalArgumentException("'$this' is not a recognized size string.")
    }.toLong()

  }

  private fun exec(vararg cmd: String) {
    val exitCode = ProcessBuilder().command(*cmd).inheritIO().start().waitFor()
    if (exitCode != 0) {
       throw RuntimeException("Script exited with code: $exitCode")
    }
  }

  companion object {
    private const val RELATIVE_RESULT_PATH = "_Query/pages/Query_Command2.txt"
    private const val GROUP_NAME = "size"
    private val USED_HEAP_DUMP_PATTERN = """Used heap dump *\|(?<$GROUP_NAME>.*)""".toRegex()
    private val CONFIGURATION_DIRECTORY = File(System.getenv("TEST_TMPDIR"), "eclipse-configurations").also {
      if (!it.exists()) {
        it.toPath().createDirectories()
      }
    }.absolutePath
  }
}