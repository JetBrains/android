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
package com.android.tools.idea.npw.template

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.flags.overrideForTest
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test

class TemplateResolverTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun getAllTemplates_journeysTemplateIncluded_whenNewWizardFlagDisabled() {
    StudioFlags.JOURNEYS_WITH_GEMINI_NEW_WIZARD.overrideForTest(
      false,
      projectRule.testRootDisposable,
    )

    val templates = TemplateResolver.getAllTemplates()

    val journeyFileTemplate = templates.find { it.name == "Journey File" }
    assertNotNull(journeyFileTemplate)
  }

  @Test
  fun getAllTemplates_journeysTemplateNotIncluded_whenNewWizardFlagEnabled() {
    StudioFlags.JOURNEYS_WITH_GEMINI_NEW_WIZARD.overrideForTest(
      true,
      projectRule.testRootDisposable,
    )

    val templates = TemplateResolver.getAllTemplates()

    val journeyFileTemplate = templates.find { it.name == "Journey File" }
    assertNull(journeyFileTemplate)
  }
}
