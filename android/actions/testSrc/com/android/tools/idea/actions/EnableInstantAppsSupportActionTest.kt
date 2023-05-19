/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.testutils.ignore.IgnoreTestRule
import com.android.tools.idea.actions.EnableInstantAppsSupportAction.Companion.addInstantAppSupportToManifest
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.XmlElementFactory
import com.intellij.testFramework.MapDataContext
import com.intellij.testFramework.TestActionEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EnableInstantAppsSupportActionTest {

  @get:Rule
  val rule = AndroidProjectRule.inMemory()

  @get:Rule
  val ignoreTests = IgnoreTestRule()

  private val context = MapDataContext()
  private val event by lazy {
    // lazy - object needs ActionManager, and that is only available after test is fully instantiated
    TestActionEvent.createTestEvent(context)
  }

  @Test
  fun `check action is enabled with parent module`() {
    context.put(PlatformCoreDataKeys.MODULE, rule.module)

    val action = EnableInstantAppsSupportAction()
    action.update(event)

    assertTrue("Action should be visible", event.presentation.isVisible)
    assertTrue("Action should be enabled", event.presentation.isEnabled)
  }

  @Test
  fun `check action is disabled with no parent module`() {
    context.put(PlatformCoreDataKeys.MODULE, null)

    val action = EnableInstantAppsSupportAction()
    action.update(event)

    assertFalse("Action should be disabled with no parent module", event.presentation.isVisible)
    assertFalse("Action should be disabled with no parent module", event.presentation.isEnabled)
  }

  @Test
  fun `enable instant app support for new manifest`() {
    WriteCommandAction.writeCommandAction(rule.project)
      .withName("Test")
      .run<Throwable> {
        val manifest = XmlElementFactory.getInstance(rule.project).createTagFromText("<foo>\n</foo>")

        addInstantAppSupportToManifest(manifest)
        val expected = """
          |<foo xmlns:dist="http://schemas.android.com/apk/distribution">
          |    <dist:module dist:instant="true" />
          |</foo>""".trimMargin()
        Truth.assertThat(manifest.text).isEqualTo(expected)

        // Nothing should happen if called twice
        addInstantAppSupportToManifest(manifest)
        Truth.assertThat(manifest.text).isEqualTo(expected)
      }
  }

  @Test
  fun `enable instant app support for manifest with existing namespace`() {
    WriteCommandAction.writeCommandAction(rule.project)
      .withName("Test")
      .run<Throwable> {
        val manifest = XmlElementFactory.getInstance(rule.project).createTagFromText("<foo xmlns:dist2=\"http://schemas.android.com/apk/distribution\">\n</foo>")

        addInstantAppSupportToManifest(manifest)
        val expected = """
          |<foo xmlns:dist2="http://schemas.android.com/apk/distribution">
          |    <dist2:module dist2:instant="true" />
          |</foo>""".trimMargin()
        Truth.assertThat(manifest.text).isEqualTo(expected)
      }
  }
}