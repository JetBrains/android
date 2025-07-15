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
package com.android.tools.idea.wear.dwf.importer.wfs.honeyface

import com.android.testutils.TestUtils
import com.android.testutils.truth.PathSubject.assertThat
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HoneyFaceParserTest {

  @get:Rule var temporaryFolder = TemporaryFolder()

  private val parser = HoneyFaceParser()

  @Test
  fun `parses honeyface example file successfully`() {
    val honeyfaceFile =
      TestUtils.resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/wfs/honeyface.json").toFile()
    assertThat(honeyfaceFile).exists()

    val honeyface = parser.parse(honeyfaceFile)
    assertThat(honeyface).isNotNull()
    assertThat(honeyface!!.background).isNotEmpty()
    assertThat(honeyface.scene).isNotEmpty()
    assertThat(honeyface.stringResource).isNotEmpty()
  }

  @Test
  fun `parsing an invalid honeyface file returns null`() {
    val invalidJsonFile = temporaryFolder.newFile("invalid.json")
    invalidJsonFile.writeText("]]")

    assertThat(parser.parse(invalidJsonFile)).isNull()
  }

  @Test
  fun `parsing an empty honeyface file returns null`() {
    val invalidJsonFile = temporaryFolder.newFile("empty.json")
    invalidJsonFile.writeText("{}")

    assertThat(parser.parse(invalidJsonFile)).isNull()
  }
}
