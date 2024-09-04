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
package org.jetbrains.android.dom

import com.android.tools.idea.testing.AndroidGradleTestCase
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.testing.moveCaret
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.VfsTestUtil
import org.junit.Test

// Checks that we have a basic editor support in res folders in all variant regardless chosen one.
class SupportMultiVariantEditingForResourceFilesTest : AndroidGradleTestCase() {

  @Test
  fun testResolveToolNamespace() {
    loadProject(TestProjectPaths.PROJECT_WITH_APPAND_LIB)
    val debugRes = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "app/src/debug/res/values/strings.xml",
      """
          <resources xmlns:tools="http://schemas.android.com/tools">
              <string name="server_url">https://...</string>
          </resources>
          """)

    val releaseRes = VfsTestUtil.createFile(
      project.guessProjectDir()!!,
      "app/src/release/res/values/strings.xml",
      """
          <resources xmlns:tools="http://schemas.android.com/tools">
              <string name="server_url">https://...</string>
          </resources>
          """)

    IndexingTestUtil.waitUntilIndexesAreReady(project)

    myFixture.openFileInEditor(debugRes)
    myFixture.moveCaret("schemas.andro|id.com")
    assertThat(myFixture.elementAtCaret).isNotNull()

    myFixture.openFileInEditor(releaseRes)
    myFixture.moveCaret("schemas.andro|id.com")
    assertThat(myFixture.elementAtCaret).isNotNull()
  }
}