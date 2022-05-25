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
package com.android.tools.idea.npw.assetstudio

import com.android.tools.idea.projectsystem.AndroidModulePaths
import com.android.tools.idea.projectsystem.NamedModuleTemplate
import com.google.common.truth.Truth
import org.junit.Test
import java.io.File

class AssetStudioUtilsTest {

  @Test
  fun orderTemplates() {
    val noopPath = object: AndroidModulePaths {
      override val moduleRoot: File?
        get() = null

      override fun getSrcDirectory(packageName: String?): File? {
        return null
      }

      override fun getTestDirectory(packageName: String?): File? {
        return null
      }

      override fun getUnitTestDirectory(packageName: String?): File? {
        return null
      }

      override val resDirectories: List<File>
        get() = listOf()

      override fun getAidlDirectory(packageName: String?): File? {
        return null
      }

      override val manifestDirectory: File?
        get() = null
    }
    val zzz = NamedModuleTemplate("zzz", noopPath)
    val release = NamedModuleTemplate("release", noopPath)
    val main = NamedModuleTemplate("main", noopPath)
    val aaa = NamedModuleTemplate("aaa", noopPath)
    val debug = NamedModuleTemplate("debug", noopPath)
    val templates = listOf(zzz, release, main, aaa, debug)
    val orderedList = orderTemplates(templates)
    Truth.assertThat(orderedList).containsExactly(main, debug, release, aaa, zzz).inOrder()
  }
}