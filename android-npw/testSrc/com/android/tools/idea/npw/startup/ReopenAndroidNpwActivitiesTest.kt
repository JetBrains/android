// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.android.tools.idea.npw.startup

import com.android.tools.idea.npw.ideahost.AndroidModuleBuilder
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.wizard.template.FormFactor
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.wizard.impl.resetStoredProjectTypeSelectionForTests
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ReopenAndroidNpwActivitiesTest {
  @get:Rule val projectRule = ProjectRule()

  @Before
  fun resetProjectWizardGroup() {
    resetStoredProjectTypeSelectionForTests()
  }

  @After
  fun resetProjectWizardGroupAfterTest() {
    resetStoredProjectTypeSelectionForTests()
  }

  @Test
  fun welcomeScreenReopensAndroidWizardOnce() = timeoutRunBlocking {
    withWizardAction { state, events ->
      val listener = OpenAndroidProjectWizardStartupLifecycleListener()
      listener.appFrameCreated(emptyList())
      listener.welcomeScreenDisplayed()
      // Let the EDT run the action queued by EdtExecutorService before assertions.
      withContext(Dispatchers.EDT) {}
      assertThat(events).isEmpty()

      state.requestNpwReopenOnNextStartup("com.jetbrains.kmm", "Kotlin Multiplatform", FormFactor.Generic)
      listener.appFrameCreated(emptyList())
      listener.welcomeScreenDisplayed()
      listener.welcomeScreenDisplayed()
      withContext(Dispatchers.EDT) {}

      assertThat(events).hasSize(1)
      assertThat(PropertiesComponent.getInstance().getValue("project.wizard.group")).isEqualTo(AndroidModuleBuilder().builderId)
      assertThat(state.consumePromotedTemplate())
        .isEqualTo(PromotedTemplate("Kotlin Multiplatform", FormFactor.Generic, "com.jetbrains.kmm"))
    }
  }

  @Test
  fun openingAProjectConsumesTheSameReopenRequest() = timeoutRunBlocking {
    withWizardAction { state, events ->
      state.requestNpwReopenOnNextStartup("com.jetbrains.kmm", "Kotlin Multiplatform", FormFactor.Mobile)
      val listener = OpenAndroidProjectWizardStartupLifecycleListener()
      listener.appFrameCreated(emptyList())
      val activity = ReopenAndroidNpwStartupActivity()
      activity.execute(projectRule.project)
      withContext(Dispatchers.EDT) {}
      assertThat(events).hasSize(1)

      activity.execute(projectRule.project)
      listener.welcomeScreenDisplayed()
      withContext(Dispatchers.EDT) {}

      assertThat(events).hasSize(1)
    }
  }

  private suspend fun withWizardAction(test: suspend (PromotionTemplateStateService, List<AnActionEvent>) -> Unit) {
    val state = PromotionTemplateStateService()
    ApplicationManager.getApplication().replaceService(PromotionTemplateStateService::class.java, state, projectRule.disposable)
    val manager = ActionManager.getInstance()
    val actionId = "WelcomeScreen.CreateNewProject"
    val originalAction = manager.getAction(actionId)
    val events = mutableListOf<AnActionEvent>()
    val action = object : AnAction() {
      override fun actionPerformed(e: AnActionEvent) {
        events.add(e)
      }
    }
    withContext(Dispatchers.EDT) {
      if (originalAction == null) manager.registerAction(actionId, action) else manager.replaceAction(actionId, action)
    }
    try {
      test(state, events)
    }
    finally {
      withContext(Dispatchers.EDT) {
        if (originalAction == null) manager.unregisterAction(actionId) else manager.replaceAction(actionId, originalAction)
      }
    }
  }
}
