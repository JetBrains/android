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
package com.android.tools.idea.whatsnew.assistant

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.assistant.DefaultTutorialBundle
import com.intellij.testFramework.ApplicationRule
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class WhatsNewXMLContentTest {
  @get:Rule val projectRule = ApplicationRule()

  @Test
  fun contentIsValid() {
    val bundleCreator = mock<WhatsNewBundleCreator>()
    val stream = bundleCreator.javaClass.getResourceAsStream("/whats-new-assistant.xml")

    // If there is an error in the XML, this will throw a SAXParseException
    val bundle =
      DefaultTutorialBundle.parse(
        stream!!,
        WhatsNewBundle::class.java,
        WhatsNewBundleCreator.BUNDLE_ID
      )
    assertNotNull(bundle)
  }
}
