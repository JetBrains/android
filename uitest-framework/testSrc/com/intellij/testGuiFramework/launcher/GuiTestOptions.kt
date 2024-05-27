/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.testGuiFramework.launcher

import com.android.test.testutils.TestUtils.resolveWorkspacePathUnchecked
import java.io.File

enum class RestartPolicy {
  NEVER, JUNIT_TIMEOUT, IDE_ERROR_OR_JUNIT_TIMEOUT, TEST_FAILURE, EACH_TEST;
}

object GuiTestOptions {

  const val SEGMENT_INDEX = "idea.gui.test.segment.index"
  const val NUM_TEST_SEGMENTS_KEY = "idea.gui.test.segments"
  const val REMOTE_IDE_PATH_KEY = "idea.gui.test.remote.ide.path"
  const val REMOTE_IDE_VM_OPTIONS_PATH_KEY = "idea.gui.test.remote.ide.vmoptions"
  const val RESTART_POLICY = "idea.gui.test.restart.policy"

  fun getPluginPath(): String = getSystemProperty("plugin.path", "")
  fun isDebug(): Boolean = getSystemProperty("idea.debug.mode", false)

  fun getDebugPort(): Int = getSystemProperty("idea.gui.test.debug.port", 5005)
  fun getBootClasspath(): String = getSystemProperty("idea.gui.test.bootclasspath", "../out/production/boot")

  fun getAspectsAgentJar(): String =
    getSystemProperty("aspects.agent.jar", resolveWorkspacePathUnchecked("prebuilts/tools/common/aspects-agent/aspects_agent.jar").toString())
  fun getAspectsAgentRules(): String =
    getSystemProperty("aspects.agent.rules", resolveWorkspacePathUnchecked("tools/adt/idea/android-uitests/default_aspect_rules.json").toString())
  fun getAspectsAgentBaseline(): String =
    getSystemProperty("aspects.agent.baseline", resolveWorkspacePathUnchecked("tools/adt/idea/android-uitests/aspects_baseline.txt").toString())
  fun getAspectsBaselineExportPath(): String = getSystemProperty("aspects.baseline.export.path", "")

  //used for restarted and resumed test to qualify from what point to start
  fun getSegmentIndex(): Int = getSystemProperty(SEGMENT_INDEX, 0)
  fun getNumTestSegments(): Int = getSystemProperty(NUM_TEST_SEGMENTS_KEY, 1)
  fun getRemoteIdePath(): String = getSystemProperty(REMOTE_IDE_PATH_KEY, "undefined")
  fun getVmOptionsFilePath(): String = getSystemProperty(REMOTE_IDE_VM_OPTIONS_PATH_KEY, File(File(getRemoteIdePath()).parent, "studio64.vmoptions").canonicalPath)
  fun getRestartPolicy(): RestartPolicy = RestartPolicy.valueOf(getSystemProperty(RESTART_POLICY, "IDE_ERROR_OR_JUNIT_TIMEOUT"))

  inline fun <reified ReturnType> getSystemProperty(key: String, defaultValue: ReturnType): ReturnType {
    val value = System.getProperty(key) ?: return defaultValue
    return when (defaultValue) {
      is Int -> value.toInt() as ReturnType
      is Boolean -> value.toBoolean() as ReturnType
      is String -> value as ReturnType
      else -> throw Exception("Unable to get returning type of default value (not integer, boolean or string) for key: $key")
    }
  }
}