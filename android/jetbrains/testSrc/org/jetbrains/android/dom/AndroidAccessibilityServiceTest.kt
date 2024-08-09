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
package org.jetbrains.android.dom

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.dom.inspections.AndroidDomInspection
import org.jetbrains.android.dom.inspections.AndroidElementNotAllowedInspection
import org.jetbrains.android.dom.inspections.AndroidUnknownAttributeInspection
import org.jetbrains.android.dom.xml.AccessibilityService
import org.jetbrains.android.dom.xml.AccessibilityServiceDomFileDescription
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [AccessibilityService] and [AccessibilityServiceDomFileDescription]. */
@RunWith(JUnit4::class)
class AndroidAccessibilityServiceTest {
  @get:Rule val projectRule = AndroidProjectRule.withSdk()

  private val fixture by lazy { projectRule.fixture }

  @Before
  fun setUp() {
    fixture.enableInspections(
      AndroidDomInspection::class.java,
      AndroidUnknownAttributeInspection::class.java,
      AndroidElementNotAllowedInspection::class.java,
    )
  }

  @Test
  fun highlighting() {
    val accessibilityService =
      fixture.addFileToProject(
        "res/xml/accessibility_service.xml",
        // language=XML
        """
        <?xml version="1.0" encoding="utf-8"?>
        <accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
            android:description="Some string"
            android:canControlMagnification="true"
            <warning>android:unknownAttribute</warning>="foo">
            <<warning>unknownSubtag</warning> />
        </accessibility-service>
        """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(accessibilityService.virtualFile)
    fixture.checkHighlighting()
  }

  @Test
  fun completion() {
    val accessibilityService =
      fixture.addFileToProject(
        "res/xml/accessibility_service.xml",
        // language=XML
        """
        <?xml version="1.0" encoding="utf-8"?>
        <accessibility-service xmlns:android="http://schemas.android.com/apk/res/android" <caret>
        """
          .trimIndent(),
      )

    fixture.configureFromExistingVirtualFile(accessibilityService.virtualFile)
    fixture.completeBasic()

    assertThat(fixture.lookupElementStrings)
      .containsAllOf("android:description", "android:accessibilityFeedbackType")
  }
}
