/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.android.tools.idea.whatsnew.assistant.v2.model.toWhatsNewData
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.io.path.inputStream
import org.jetbrains.android.AndroidTestBase
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class V2BundleConverterTest {
  @Test
  fun convertBundle() {
    val testFile = getTestDataPath().resolve("whatsnewassistant/v2.xml")
    val bundleData =
      WhatsNewBundle.parse(testFile.inputStream(StandardOpenOption.READ), WhatsNewBundle::class.java, WhatsNewBundleCreator.BUNDLE_ID)

    val whatsNewData = (bundleData as WhatsNewBundle).toWhatsNewData()

    assertEquals("What's New in Narwhal Feature Drop", whatsNewData.label)
    assertEquals(
      """
      This panel describes some of the new features and behavior changes
      included in this update.

      To open this panel again later, select **Help > What's New in Android Studio**
      from the main menu.
      """
        .trimIndent(),
      whatsNewData.description,
    )
    assertEquals(13, whatsNewData.cards.size)

    val card2 = whatsNewData.cards[1]
    assertEquals("Gemini in Android Studio's Agent mode", card2.title)
    assertEquals("https://d.android.com/studio/releases/assistant/2025.1.2/agent-mode-cropped.png", card2.image?.source)
    assertEquals(
      """
      Gemini in Android Studio's Agent mode is a new AI feature designed to handle
      complex, multi-stage development tasks that go beyond what you can experience by
      chatting with Gemini. To use Agent mode, click **Gemini** in the sidebar and then
      select the **Agent** tab. You can describe a complex goal, like generating unit tests or
      fixing errors, and the agent formulates an execution plan that spans multiple files in
      your project. The agent suggests edits and iteratively fixes bugs to reach the
      goal. You can review, accept, or reject the proposed changes and ask the agent to
      iterate on your feedback.

      [Learn more ↗](https://d.android.com/r/studio-ui/gemini/agent-mode)
      """
        .trimIndent(),
      card2.description,
    )

    assertEquals("Last updated 03/24/2025", whatsNewData.footerText)
  }

  private fun getTestDataPath(): Path {
    return Path.of(AndroidTestBase.getTestDataPath())
  }
}