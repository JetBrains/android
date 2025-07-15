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

import com.android.ide.common.xml.XmlFormatPreferences
import com.android.ide.common.xml.XmlFormatStyle
import com.android.ide.common.xml.XmlPrettyPrinter
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.google.common.truth.Truth.assertThat
import kotlin.io.path.readText
import org.junit.Test

class HoneyFaceXmlConverterTest {

  private val parser = HoneyFaceParser()
  private val converter = HoneyFaceXmlConverter()

  @Test
  fun `converts example honeyface file successfully`() {
    val honeyfaceFile =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/wfs/honeyface.json").toFile()
    val honeyface = parser.parse(honeyfaceFile) ?: error("expected honeyface to not be null")

    val convertedXmlDocument = converter.toXml(honeyface)

    val expectedXml =
      resolveWorkspacePath("tools/adt/idea/wear-dwf/testData/wfs/expected/raw/watchface.xml")
        .readText()
    val actualXml =
      XmlPrettyPrinter.prettyPrint(
        convertedXmlDocument,
        XmlFormatPreferences.defaults(),
        XmlFormatStyle.get(convertedXmlDocument),
        "\n",
        false,
      )
    assertThat(actualXml).isEqualTo(expectedXml)
  }
}
