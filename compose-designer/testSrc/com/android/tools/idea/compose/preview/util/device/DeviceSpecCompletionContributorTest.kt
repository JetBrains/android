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
package com.android.tools.idea.compose.preview.util.device

import com.android.tools.idea.compose.annotator.registerLanguageExtensionPoint
import com.android.tools.idea.compose.preview.util.device.parser.DeviceSpecParserDefinition
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.testing.caret
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

internal class DeviceSpecCompletionContributorTest {
  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  val fixture get() = rule.fixture

  @Before
  fun setup() {
    fixture.registerLanguageExtensionPoint(LanguageParserDefinitions.INSTANCE, DeviceSpecParserDefinition(), DeviceSpecLanguage)
    val ep = ApplicationManager.getApplication().extensionArea.getExtensionPoint<CompletionContributorEP>(CompletionContributor.EP.name)
    ep.registerExtension(
      CompletionContributorEP(DeviceSpecLanguage.id, DeviceSpecCompletionContributor::class.java.name, ep.pluginDescriptor),
      fixture.testRootDisposable
    )
    runInEdtAndWait {
      // Sdk needed for Devices
      Sdks.addLatestAndroidSdk(fixture.testRootDisposable, rule.module)
    }
  }


  @Test
  fun providedDeviceInId() {
    fixture.completeDeviceSpec( "id:Nexus 7$caret")

    assertEquals(2, fixture.lookupElementStrings!!.size)
    assertEquals("Nexus 7", fixture.lookupElementStrings!![0])
    assertEquals("Nexus 7 2013", fixture.lookupElementStrings!![1])
  }

  @Test
  fun providedDeviceInParent() {
    fixture.completeDeviceSpec("spec:parent=Nexus 7$caret")

    assertEquals(2, fixture.lookupElementStrings!!.size)
    assertEquals("Nexus 7", fixture.lookupElementStrings!![0])
    assertEquals("Nexus 7 2013", fixture.lookupElementStrings!![1])
  }

  @Test
  fun nothingProvided() {
    // Currently, not supported in name
    fixture.completeDeviceSpec( "name:Nexus 7$caret")
    fixture.checkResult("name:Nexus 7")
    assertEquals(0, fixture.lookupElementStrings!!.size)

    // No such thing as id=<device_id>
    fixture.completeDeviceSpec( "spec:id=Nexus 7$caret")
    fixture.checkResult("spec:id=Nexus 7")
    assertEquals(0, fixture.lookupElementStrings!!.size)

    // No such thing as parent:<device_id>
    fixture.completeDeviceSpec( "parent:Nexus 7$caret")
    fixture.checkResult("parent:Nexus 7")
    assertEquals(0, fixture.lookupElementStrings!!.size)
  }
}

private fun CodeInsightTestFixture.completeDeviceSpec(text: String) {
  configureByText(DeviceSpecFileType, text)
  completeBasic()
}