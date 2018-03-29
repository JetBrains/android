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

import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import com.intellij.openapi.application.PathManager

object GuiTestOptions {

  const val SEGMENT_INDEX = "idea.gui.test.segment.index"
  const val NUM_TEST_SEGMENTS_KEY = "idea.gui.test.segments"
  const val REMOTE_IDE_PATH_KEY = "idea.gui.test.remote.ide.path"

  var buildSystem = TargetBuildSystem.BuildSystem.GRADLE

  fun getConfigPath(): String = PathManager.getConfigPath()
  fun getSystemPath(): String = PathManager.getSystemPath()
  fun getPluginPath(): String = getSystemProperty("plugin.path", "")
  fun isDebug(): Boolean = getSystemProperty("idea.debug.mode", false)
  fun suspendDebug(): String = if (isDebug()) "y" else "n"
  fun isInternal(): Boolean = getSystemProperty("idea.is.internal", true)
  fun useAppleScreenMenuBar(): Boolean = getSystemProperty("apple.laf.useScreenMenuBar", false)

  fun getDebugPort(): Int = getSystemProperty("idea.gui.test.debug.port", 5005)
  fun getBootClasspath(): String = getSystemProperty("idea.gui.test.bootclasspath", "../out/production/boot")
  fun getEncoding(): String = getSystemProperty("idea.gui.test.encoding", "UTF-8")
  fun getXmxSize(): Int = getSystemProperty("idea.gui.test.xmx", 512)
  //used for restarted and resumed test to qualify from what point to start
  fun getSegmentIndex(): Int = getSystemProperty(SEGMENT_INDEX, 0)
  fun getNumTestSegments(): Int = getSystemProperty(NUM_TEST_SEGMENTS_KEY, 1)
  fun getRemoteIdePath(): String = getSystemProperty(REMOTE_IDE_PATH_KEY, "undefined")
  fun isRunningOnRelease(): Boolean = getRemoteIdePath() != "undefined"

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