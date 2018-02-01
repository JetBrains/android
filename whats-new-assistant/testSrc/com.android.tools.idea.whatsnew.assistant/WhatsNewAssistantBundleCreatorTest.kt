/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant

import com.android.tools.idea.assistant.AssistantBundleCreator
import com.android.tools.idea.flags.StudioFlags
import junit.framework.TestCase
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.net.URL

class WhatsNewAssistantBundleCreatorTest : AndroidTestCase() {

  fun testDisabled() {
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.override(false)
    TestCase.assertFalse(WhatsNewAssistantBundleCreator.isAssistantEnabled())
  }

  fun testEnabledWithoutFile() {
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.override(true)
    val mockBundler = mock(AssistantBundleCreator::class.java)
    `when`(mockBundler.bundleId).thenReturn(WhatsNewAssistantBundleCreator.BUNDLE_ID)
    `when`(mockBundler.config).thenReturn(null)
    WhatsNewAssistantBundleCreator.setTestCreator(mockBundler)

    TestCase.assertFalse(WhatsNewAssistantBundleCreator.isAssistantEnabled())

    WhatsNewAssistantBundleCreator.setTestCreator(null)
  }

  fun testEnabled() {
    StudioFlags.WHATS_NEW_ASSISTANT_ENABLED.override(true)
    val mockBundler = mock(AssistantBundleCreator::class.java)
    `when`(mockBundler.bundleId).thenReturn(WhatsNewAssistantBundleCreator.BUNDLE_ID)
    `when`(mockBundler.config).thenReturn(URL("file:test.file"))
    WhatsNewAssistantBundleCreator.setTestCreator(mockBundler)

    TestCase.assertTrue(WhatsNewAssistantBundleCreator.isAssistantEnabled())

    WhatsNewAssistantBundleCreator.setTestCreator(null)
  }
}
