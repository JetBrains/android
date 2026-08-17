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
package com.android.tools.idea.actions

import com.android.ide.common.resources.Locale
import com.android.tools.adtui.actions.createTestActionEvent
import com.android.tools.adtui.actions.updateAndGetActionPresentation
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.waitForResourceRepositoryUpdates
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LocaleMenuActionTest {

  @get:Rule val projectRule = AndroidProjectRule.withAndroidModel(AndroidProjectBuilder())

  @Test
  fun testSetLocaleActionUpdateResetsText() {
    val file =
      projectRule.fixture
        .addFileToProject(
          "src/main/res/layout/layout1.xml",
          "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" />",
        )
        .virtualFile
    projectRule.fixture.addFileToProject(
      "src/main/res/layout-fr/layout1.xml",
      "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" />",
    )
    projectRule.fixture.addFileToProject(
      "src/main/res/values-fr/strings.xml",
      "<resources><string name=\"hello\">Bonjour</string></resources>",
    )
    waitForResourceRepositoryUpdates(projectRule.module)

    val manager = ConfigurationManager.getOrCreateInstance(projectRule.module)
    val configuration = manager.getConfiguration(file)

    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(configuration))

    val action = LocaleMenuAction()
    action.updateActions(dataContext)

    val children = action.getChildren(null)
    val texts = children.map { it.templatePresentation.text }
    val frAction = children.filterIsInstance<AnAction>().firstOrNull { it.templatePresentation.text?.contains("French") == true }
    assertNotNull("Children texts: $texts", frAction)

    val event = createTestActionEvent(frAction!!, dataContext = dataContext)
    val presentation = updateAndGetActionPresentation(frAction, event)

    // With layout-fr/layout1.xml present, a better match is found, so presentation text includes variation file label
    assertThat(presentation.text).contains("fr/layout1.xml")

    // Now update using the same presentation but with a data context containing no configurations
    val emptyDataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, emptyList())
    val eventWithNoConfig = createTestActionEvent(frAction, dataContext = emptyDataContext)
    eventWithNoConfig.presentation.copyFrom(presentation)
    val presentationWithNoConfig = updateAndGetActionPresentation(frAction, eventWithNoConfig)

    // Presentation text should be reset to default locale label "French (fr)" without "layout-fr"
    assertThat(presentationWithNoConfig.text).isEqualTo(Locale.getLocaleLabel(Locale.create("fr"), false))
  }
}
