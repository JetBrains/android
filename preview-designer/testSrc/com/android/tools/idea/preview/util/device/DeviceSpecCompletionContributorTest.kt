/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.preview.util.device

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.Sdks
import com.android.tools.idea.testing.caret
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class DeviceSpecCompletionContributorTest {
  @get:Rule val rule = AndroidProjectRule.inMemory()

  val fixture
    get() = rule.fixture

  @Before
  fun setup() {
    val ep =
      ApplicationManager.getApplication()
        .extensionArea
        .getExtensionPoint<CompletionContributorEP>(CompletionContributor.EP.name)
    ep.registerExtension(
      CompletionContributorEP(
        DeviceSpecLanguage.id,
        DeviceSpecCompletionContributor::class.java.name,
        ep.pluginDescriptor,
      ),
      fixture.testRootDisposable,
    )
    runInEdtAndWait {
      // Sdk needed for Devices
      Sdks.addLatestAndroidSdk(fixture.testRootDisposable, rule.module)
    }
  }

  @Test
  fun providedDeviceInId() {
    fixture.completeDeviceSpec("id:pixel_8$caret")

    assertEquals(2, fixture.lookupElementStrings!!.size)
    assertEquals("pixel_8", fixture.lookupElementStrings!![0])
    assertEquals("pixel_8_pro", fixture.lookupElementStrings!![1])
  }

  @Test
  fun providedDeviceInParent() {
    fixture.completeDeviceSpec("spec:parent=pixel_8$caret")

    assertEquals(2, fixture.lookupElementStrings!!.size)
    assertEquals("pixel_8", fixture.lookupElementStrings!![0])
    assertEquals("pixel_8_pro", fixture.lookupElementStrings!![1])
  }

  @Test
  fun providedOrientationValues() {
    fixture.completeDeviceSpec("spec:orientation=$caret")

    assertEquals(2, fixture.lookupElementStrings!!.size)
    assertEquals("landscape", fixture.lookupElementStrings!![0])
    assertEquals("portrait", fixture.lookupElementStrings!![1])
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
    fixture.completeDeviceSpec(caret)
    assertEquals(6, fixture.lookupElementStrings!!.size)
    assertEquals("id:pixel_5", fixture.lookupElementStrings!![0])
    assertEquals("spec:", fixture.lookupElementStrings!![1]) // Driven by Live Template
    assertEquals(
      "spec:id=reference_desktop,shape=Normal,width=1920,height=1080,unit=dp,dpi=160",
      fixture.lookupElementStrings!![2],
    )

    assertEquals(
      "spec:id=reference_foldable,shape=Normal,width=673,height=841,unit=dp,dpi=420",
      fixture.lookupElementStrings!![3],
    )
    assertEquals(
      "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420",
      fixture.lookupElementStrings!![4],
    )
    assertEquals(
      "spec:id=reference_tablet,shape=Normal,width=1280,height=800,unit=dp,dpi=240",
      fixture.lookupElementStrings!![5],
    )

    // 'pix' should only match the default device (pixel_5)
    fixture.completeDeviceSpec("pix$caret")
    assertEquals(1, fixture.lookupElementStrings!!.size)
    assertEquals("id:pixel_5", fixture.lookupElementStrings!![0])

    // completion for 'id' prefix
    fixture.completeDeviceSpec(
      "id$caret"
    ) // Note that 'id' also matches 'width' in the full 'spec:...' definition
    assertEquals(5, fixture.lookupElementStrings!!.size)
    assertEquals("id:pixel_5", fixture.lookupElementStrings!![0])
    assertEquals(
      "spec:id=reference_desktop,shape=Normal,width=1920,height=1080,unit=dp,dpi=160",
      fixture.lookupElementStrings!![1],
    )

    assertEquals(
      "spec:id=reference_foldable,shape=Normal,width=673,height=841,unit=dp,dpi=420",
      fixture.lookupElementStrings!![2],
    )
    assertEquals(
      "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420",
      fixture.lookupElementStrings!![3],
    )
    assertEquals(
      "spec:id=reference_tablet,shape=Normal,width=1280,height=800,unit=dp,dpi=240",
      fixture.lookupElementStrings!![4],
    )

    // completion for 'spec' prefix
    fixture.completeDeviceSpec("spe$caret")
    assertEquals(5, fixture.lookupElementStrings!!.size)
    assertEquals("spec:", fixture.lookupElementStrings!![0]) // Driven by Live Template
    assertEquals(
      "spec:id=reference_desktop,shape=Normal,width=1920,height=1080,unit=dp,dpi=160",
      fixture.lookupElementStrings!![1],
    )

    assertEquals(
      "spec:id=reference_foldable,shape=Normal,width=673,height=841,unit=dp,dpi=420",
      fixture.lookupElementStrings!![2],
    )
    assertEquals(
      "spec:id=reference_phone,shape=Normal,width=411,height=891,unit=dp,dpi=420",
      fixture.lookupElementStrings!![3],
    )
    assertEquals(
      "spec:id=reference_tablet,shape=Normal,width=1280,height=800,unit=dp,dpi=240",
      fixture.lookupElementStrings!![4],
    )
  }

  @Test
  fun parameterCompletion() {
    fixture.completeDeviceSpec("spec:$caret")
    assertEquals(7, fixture.lookupElementStrings!!.size)
    assertEquals("chinSize", fixture.lookupElementStrings!![0])
    assertEquals("dpi", fixture.lookupElementStrings!![1])
    assertEquals("height", fixture.lookupElementStrings!![2])
    assertEquals("isRound", fixture.lookupElementStrings!![3])
    assertEquals("orientation", fixture.lookupElementStrings!![4])
    assertEquals("parent", fixture.lookupElementStrings!![5])
    assertEquals("width", fixture.lookupElementStrings!![6])

    fixture.completeDeviceSpec("spec:orientation=portrait,$caret")
    assertEquals(6, fixture.lookupElementStrings!!.size)
    assertEquals("chinSize", fixture.lookupElementStrings!![0])
    assertEquals("dpi", fixture.lookupElementStrings!![1])
    assertEquals("height", fixture.lookupElementStrings!![2])
    assertEquals("isRound", fixture.lookupElementStrings!![3])
    assertEquals("parent", fixture.lookupElementStrings!![4])
    assertEquals("width", fixture.lookupElementStrings!![5])

    fixture.completeDeviceSpec("spec:parent=pixel_5,$caret")
    assertEquals(1, fixture.lookupElementStrings!!.size)
    assertEquals("orientation", fixture.lookupElementStrings!![0])

    fixture.completeDeviceSpec("spec:width=1080px,$caret")
    assertEquals(5, fixture.lookupElementStrings!!.size)
    assertEquals("chinSize", fixture.lookupElementStrings!![0])
    assertEquals("dpi", fixture.lookupElementStrings!![1])
    assertEquals("height", fixture.lookupElementStrings!![2])
    assertEquals("isRound", fixture.lookupElementStrings!![3])
    assertEquals("orientation", fixture.lookupElementStrings!![4])

    fixture.completeDeviceSpec("spec:width=1080px,heigh$caret")
    fixture.checkResult("spec:width=1080px,height=891px")

    fixture.completeDeviceSpec("spec:width=1080dp,heigh$caret")
    fixture.checkResult("spec:width=1080dp,height=891dp")

    fixture.completeDeviceSpec("spec:width=1080dp,isRoun$caret")
    fixture.checkResult("spec:width=1080dp,isRound=false")

    fixture.completeDeviceSpec("spec:width=1080dp,chinSiz$caret")
    fixture.checkResult("spec:width=1080dp,chinSize=0dp")

    fixture.completeDeviceSpec("spec:parent=pixel_5,orient$caret")
    fixture.checkResult("spec:parent=pixel_5,orientation=portrait")

    fixture.completeDeviceSpec("spec:orientation=portrait,par$caret")
    fixture.checkResult("spec:orientation=portrait,parent=pixel_5")

    // Nothing else to complete when using `parent`
    fixture.completeDeviceSpec("spec:orientation=portrait,parent=pixel_5,$caret")
    fixture.checkResult("spec:orientation=portrait,parent=pixel_5,")
    assertEquals(0, fixture.lookupElementStrings!!.size)

    // No parameters starting with 'spe'
    fixture.completeDeviceSpec("spec:width=300dp,spe$caret")
    assertEquals(0, fixture.lookupElementStrings!!.size)
  }
}

private fun CodeInsightTestFixture.completeDeviceSpec(text: String) {
  configureByText(DeviceSpecFileType, text)
  completeBasic()
}
