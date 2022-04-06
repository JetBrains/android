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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.testing.caret
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.junit.After
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
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.override(true)
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

  @After
  fun teardown() {
    StudioFlags.COMPOSE_PREVIEW_DEVICESPEC_INJECTOR.clearOverride()
  }

  @Test
  fun providedDeviceInId() {
    fixture.completeDeviceSpec("id:Nexus 7$caret")

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
    fixture.completeDeviceSpec("name:Nexus 7$caret")
    fixture.checkResult("name:Nexus 7")
    assertEquals(0, fixture.lookupElementStrings!!.size)

    // No such thing as id=<device_id>
    fixture.completeDeviceSpec("spec:id=Nexus 7$caret")
    fixture.checkResult("spec:id=Nexus 7")
    assertEquals(0, fixture.lookupElementStrings!!.size)

    // No such thing as parent:<device_id>
    fixture.completeDeviceSpec("parent:Nexus 7$caret")
    fixture.checkResult("parent:Nexus 7")
    assertEquals(0, fixture.lookupElementStrings!!.size)
  }

  @Test
  fun prefixCompletion() {
    // Blank, should provide all possible options
    fixture.completeDeviceSpec("$caret")
    assertEquals(5, fixture.lookupElementStrings!!.size)
    assertEquals("id:pixel_5", fixture.lookupElementStrings!![0])
    assertEquals("spec:width=360dp,height=640dp", fixture.lookupElementStrings!![1])
    assertEquals("spec:width=673dp,height=841dp", fixture.lookupElementStrings!![2])
    assertEquals("spec:width=1280dp,height=800dp", fixture.lookupElementStrings!![3])
    assertEquals("spec:width=1920dp,height=1080dp", fixture.lookupElementStrings!![4])

    // 'pix' should only match the default device (pixel_5)
    fixture.completeDeviceSpec("pix$caret")
    assertEquals(1, fixture.lookupElementStrings!!.size)
    assertEquals("id:pixel_5", fixture.lookupElementStrings!![0])

    // completion for 'id' prefix
    fixture.completeDeviceSpec("id$caret") // Note that 'id' also matches 'width' in the full 'spec:...' definition
    assertEquals(5, fixture.lookupElementStrings!!.size)
    assertEquals("id:pixel_5", fixture.lookupElementStrings!![0])
    assertEquals("spec:width=360dp,height=640dp", fixture.lookupElementStrings!![1])
    assertEquals("spec:width=673dp,height=841dp", fixture.lookupElementStrings!![2])
    assertEquals("spec:width=1280dp,height=800dp", fixture.lookupElementStrings!![3])
    assertEquals("spec:width=1920dp,height=1080dp", fixture.lookupElementStrings!![4])

    // completion for 'spec' prefix
    fixture.completeDeviceSpec("spe$caret")
    assertEquals(4, fixture.lookupElementStrings!!.size)
    assertEquals("spec:width=360dp,height=640dp", fixture.lookupElementStrings!![0])
    assertEquals("spec:width=673dp,height=841dp", fixture.lookupElementStrings!![1])
    assertEquals("spec:width=1280dp,height=800dp", fixture.lookupElementStrings!![2])
    assertEquals("spec:width=1920dp,height=1080dp", fixture.lookupElementStrings!![3])
  }

  @Test
  fun parameterCompletion() {
    fixture.completeDeviceSpec("spec:$caret")
    assertEquals(5, fixture.lookupElementStrings!!.size)
    assertEquals("chinSize", fixture.lookupElementStrings!![0])
    assertEquals("dpi", fixture.lookupElementStrings!![1])
    assertEquals("height", fixture.lookupElementStrings!![2])
    assertEquals("isRound", fixture.lookupElementStrings!![3])
    assertEquals("width", fixture.lookupElementStrings!![4])

    fixture.completeDeviceSpec("spec:width=1080px,$caret")
    assertEquals(4, fixture.lookupElementStrings!!.size)
    assertEquals("chinSize", fixture.lookupElementStrings!![0])
    assertEquals("dpi", fixture.lookupElementStrings!![1])
    assertEquals("height", fixture.lookupElementStrings!![2])
    assertEquals("isRound", fixture.lookupElementStrings!![3])

    fixture.completeDeviceSpec("spec:width=1080px,heigh$caret")
    fixture.checkResult("spec:width=1080px,height=1920px")

    fixture.completeDeviceSpec("spec:width=1080dp,heigh$caret")
    fixture.checkResult("spec:width=1080dp,height=1920dp")

    fixture.completeDeviceSpec("spec:width=1080dp,isRoun$caret")
    fixture.checkResult("spec:width=1080dp,isRound=false")

    fixture.completeDeviceSpec("spec:width=1080dp,chinSiz$caret")
    fixture.checkResult("spec:width=1080dp,chinSize=0dp")

    // No parameters starting with 'spe'
    fixture.completeDeviceSpec("spec:width=300dp,spe$caret")
    assertEquals(0, fixture.lookupElementStrings!!.size)
  }
}

private fun CodeInsightTestFixture.completeDeviceSpec(text: String) {
  configureByText(DeviceSpecFileType, text)
  completeBasic()
}