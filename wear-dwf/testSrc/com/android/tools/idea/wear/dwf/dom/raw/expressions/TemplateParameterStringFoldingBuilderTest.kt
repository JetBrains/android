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
package com.android.tools.idea.wear.dwf.dom.raw.expressions

import com.android.flags.junit.FlagRule
import com.android.testutils.TestUtils
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class TemplateParameterStringFoldingBuilderTest {

  @get:Rule val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val flagRule = FlagRule(StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT, true)

  private val fixture
    get() = projectRule.fixture

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/").toString()

    fixture.addFileToProject(
      "res/values/strings.xml",
      // language=XML
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
          <string name="greeting">Hello, World!</string>
      </resources>
    """
        .trimIndent(),
    )
    projectRule.waitForResourceRepositoryUpdates()
  }

  @Test
  fun `string references in template parameter expressions are folded`() {
    fixture.testFolding("${fixture.testDataPath}/res/raw/watch_face_folding.xml")
  }

  @Test
  fun `string references in template parameter expressions are not folded when the flag is disabled`() {
    StudioFlags.WEAR_DECLARATIVE_WATCH_FACE_XML_EDITOR_SUPPORT.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )
    fixture.testFolding("${fixture.testDataPath}/res/raw/watch_face_folding_disabled.xml")
  }

  // Regression test for b/441987780
  @Test
  fun `does not throw exceptions on invalid resource names`() {
    fixture.testFolding(
      "${fixture.testDataPath}/res/raw/watch_face_folding_with_invalid_resource_name.xml"
    )
  }
}
