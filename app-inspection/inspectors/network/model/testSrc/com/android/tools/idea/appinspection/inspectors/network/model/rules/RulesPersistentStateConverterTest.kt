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
package com.android.tools.idea.appinspection.inspectors.network.model.rules

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.file.createInMemoryFileSystemAndFolder
import com.android.testutils.file.recordExistingFile
import com.google.common.truth.Truth.assertThat
import com.intellij.conversion.ConversionContext
import com.intellij.util.io.readText
import org.junit.Before
import org.junit.Test

class RulesPersistentStateConverterTest {

  private val inMemoryMiscXml = createInMemoryFileSystemAndFolder("baseDir").resolve(MISC_XML)
  private lateinit var conversionContext: ConversionContext

  private val toChangeMiscXml =
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <project version="4">
      <component name="NetworkInspectorRules">
        <option name="rulesList" value="" />
      </component>
      <component name="NotNetworkInspectorRules">
        <option name="rulesList" value="" />
      </component>
    </project>
  """
      .trimIndent()

  private val expectedMiscXml =
    """
    <project version="4">
      <component name="deprecatedNetworkInspectorRules">
        <option name="rulesList" value="" />
      </component>
      <component name="NotNetworkInspectorRules">
        <option name="rulesList" value="" />
      </component>
    </project>
  """
      .trimIndent()

  @Before
  fun setUp() {
    inMemoryMiscXml.recordExistingFile(toChangeMiscXml)
    conversionContext = mock()
    whenever(conversionContext.settingsBaseDir).thenReturn(inMemoryMiscXml.parent)
  }

  @Test
  fun testConversion() {
    // Setup
    val converter = RulesPersistentStateConverter(conversionContext)

    // Act and Assert
    assertThat(converter.isConversionNeeded).isFalse()
    assertThat(inMemoryMiscXml.readText()).isEqualTo(expectedMiscXml)
  }
}
