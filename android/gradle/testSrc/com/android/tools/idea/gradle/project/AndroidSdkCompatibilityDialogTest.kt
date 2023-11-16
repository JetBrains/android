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
package com.android.tools.idea.gradle.project

import com.android.sdklib.AndroidVersion
import com.android.testutils.waitForCondition
import com.android.tools.adtui.swing.FakeUi
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.findModelessDialog
import com.android.tools.idea.serverflags.protos.StudioVersionRecommendation
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JEditorPane

/**
 * Tests for [AndroidSdkCompatibilityDialog].
 */
@RunsInEdt
class AndroidSdkCompatibilityDialogTest {

  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()
  @get:Rule val rule = RuleChain(projectRule, HeadlessDialogRule())

  private fun findDialog(): AndroidSdkCompatibilityDialog? {
    return findModelessDialog { it is AndroidSdkCompatibilityDialog } as AndroidSdkCompatibilityDialog?
  }

  @Test
  fun `test dialog content with same channel recommendation`() {
    val recommendation = StudioVersionRecommendation.newBuilder().apply {
      versionReleased = true
      buildDisplayName = "Android Studio Canary X"
    }.build()

    val dialog = AndroidSdkCompatibilityDialog(
      projectRule.project,
      recommendation,
      null,
      listOf(
        ".myapp" to AndroidVersion(1000)
      )
    )
    dialog.show()
    waitForCondition(5, TimeUnit.SECONDS) { findDialog() != null }

    dialog.let {
      assertThat(it.title).isEqualTo("Android Studio upgrade suggested")
      val rootPane = it.rootPane
      val ui = FakeUi(rootPane)
      val messageComponents = ui.findAllComponents<JEditorPane>()
      val mainContent = messageComponents.find { c -> c.name == "main-content" }!!.normalizedText
      assertThat(mainContent).contains("Your project is configured with a compile SDK version that is not supported by this version of Android Studio")
      assertThat(mainContent).contains("You can upgrade to Android Studio Canary X or higher to have better IDE support for this project")
      val affectedModules = messageComponents.find { c -> c.name == "affected-modules" }!!.normalizedText
      assertThat(affectedModules).contains(".myapp")
      assertThat(affectedModules).contains("(compileSdk=1000)")

      val buttons = ui.findAllComponents(JButton::class.java)
      assertThat(buttons).hasSize(3)
      assertThat(buttons.map { btn -> btn.text }).containsAllOf(
        "Close",  "Don't ask for this project", "Android Studio documentation"
      )
      buttons.find { btn -> btn.text == "Close" }!!.doClick()
    }
    assertThat(dialog.exitCode).isEqualTo(DialogWrapper.CLOSE_EXIT_CODE)
  }

  @Test
  fun `test dialog content with different channel recommendation`() {
    val recommendation = StudioVersionRecommendation.newBuilder().apply {
      versionReleased = false
      buildDisplayName = "Android Studio Beta Y"
    }.build()

    val fallbackRecommendation = StudioVersionRecommendation.newBuilder().apply {
      versionReleased = true
      buildDisplayName = "Android Studio Canary Z"
    }.build()

    val dialog = AndroidSdkCompatibilityDialog(
      projectRule.project,
      recommendation,
      fallbackRecommendation,
      listOf(
        ".myapp1" to AndroidVersion(1000),
        ".myapp2" to AndroidVersion(1000),
        ".myapp3" to AndroidVersion(1000),
        ".mylib1" to AndroidVersion(1000),
        ".mylib2" to AndroidVersion(1000),
        ".mylib3" to AndroidVersion(1000)
      )
    )

    dialog.show()
    waitForCondition(5, TimeUnit.SECONDS) { findDialog() != null }

    dialog.let {
      assertThat(it.title).isEqualTo("Android Studio upgrade suggested")
      val rootPane = it.rootPane
      val ui = FakeUi(rootPane)
      val messageComponents = ui.findAllComponents<JEditorPane>()
      val mainContent = messageComponents.find { c -> c.name == "main-content" }!!.normalizedText
      assertThat(mainContent).isNotNull()
      assertThat(mainContent).contains("We recommend installing Android Studio Canary Z")
      val affectedModules = messageComponents.find { c -> c.name == "affected-modules" }!!.normalizedText
      assertThat(affectedModules).contains(".myapp1")
      assertThat(affectedModules).contains(".mylib1")
      assertThat(affectedModules).contains("(compileSdk=1000)")
      assertThat(affectedModules).contains("(and 1 more)")

      val buttons = ui.findAllComponents(JButton::class.java)
      assertThat(buttons).hasSize(3)
      assertThat(buttons.map { btn -> btn.text }).containsAllOf(
        "Close", "Don't ask for this project", "Android Studio documentation"
      )
      buttons.find { btn -> btn.text == "Don't ask for this project" }!!.doClick()
    }
    assertThat(dialog.exitCode).isEqualTo(DialogWrapper.OK_EXIT_CODE)
  }

  @Test
  fun `test dialog content with no recommendation`() {
    val dialog = AndroidSdkCompatibilityDialog(
      projectRule.project,
      StudioVersionRecommendation.getDefaultInstance(),
      null,
      emptyList()
    )

    dialog.show()
    waitForCondition(5, TimeUnit.SECONDS) { findDialog() != null }

    dialog.let {
      assertThat(it.title).isEqualTo("Android Studio does not support the specified Android API level")
      val rootPane = it.rootPane
      val ui = FakeUi(rootPane)
      val messageComponents = ui.findAllComponents<JEditorPane>()
      val mainContent = messageComponents.find { c -> c.name == "main-content" }!!.normalizedText
      assertThat(mainContent).contains("Currently, there is no version of Android Studio that supports this compile SDK. You may experience issues while working on this project")

      val buttons = ui.findAllComponents(JButton::class.java)
      assertThat(buttons).hasSize(3)
      assertThat(buttons.map { btn -> btn.text }).containsAllOf(
        "Close", "Don't ask for this project", "Android Studio documentation"
      )
      buttons.find { btn -> btn.text == "Close" }!!.doClick()
    }
    assertThat(dialog.exitCode).isEqualTo(DialogWrapper.CLOSE_EXIT_CODE)
  }

  private val JEditorPane.normalizedText: String
    get() = text.replace(Regex("&#160;|\\s+"), " ")
}