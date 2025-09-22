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
package com.android.tools.idea.actions

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.actions.TargetMenuAction.SetTargetAction
import com.android.tools.idea.configurations.ConfigurationForFile
import com.android.tools.idea.configurations.ConfigurationManager
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TargetMenuActionTest : AndroidTestCase() {

  fun testUpdateTargets() {
    val file = runInEdtAndGet { myFixture.addFileToProject("res/layout/layout.xml", "") }
    val manager = createSpiedConfigurationManager()

    val config = ConfigurationForFile(file.virtualFile, manager, FolderConfiguration())
    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(config))
    val menuAction = TargetMenuAction()
    menuAction.updateActions(dataContext)

    val expected =
      """30
    ✔ Automatically Pick Best
    ------------------------------------------------------
    33
    32
    31
    30
    29
    28
"""

    val actual = prettyPrintActions(menuAction, dataContext = dataContext)
    assertThat(actual).isEqualTo(expected)
  }

  fun testNoDuplicateActionForDifferentRevision() {
    val file = runInEdtAndGet { myFixture.addFileToProject("res/layout/layout.xml", "") }
    val manager = createSpiedConfigurationManager()

    val config = ConfigurationForFile(file.virtualFile, manager, FolderConfiguration())
    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(config))
    val menuAction = TargetMenuAction()
    menuAction.updateActions(dataContext)

    val children = menuAction.getChildren(null)
    run {
      val target =
        children
          .filterIsInstance<SetTargetAction>()
          .map { it.myTarget }
          .single { it.version.androidApiLevel.majorVersion == 33 }

      assertThat(target.revision).isEqualTo(2)
    }

    run {
      val target =
        children
          .filterIsInstance<SetTargetAction>()
          .map { it.myTarget }
          .single { it.version.androidApiLevel.majorVersion == 32 }

      assertThat(target.version.androidApiLevel.minorVersion).isEqualTo(1)
    }
  }

  fun testNotSelectAutomaticallyPickupActionWhenSelectOtherTarget() {
    // When switching the selected action from "Automatically Pick Best" to specified target, the
    // "Automatically Pick Best" action should
    // not be marked as selected.

    val file = runInEdtAndGet { myFixture.addFileToProject("res/layout/layout.xml", "") }
    val manager = createSpiedConfigurationManager()

    val config = ConfigurationForFile(file.virtualFile, manager, FolderConfiguration())
    val dataContext = SimpleDataContext.getSimpleContext(CONFIGURATIONS, listOf(config))
    val menuAction = TargetMenuAction()

    manager.stateManager.projectState.isPickTarget = true

    menuAction.updateActions(dataContext)
    menuAction.getChildren(null).let { children ->
      val event = TestActionEvent.createTestEvent(dataContext)
      children[0].update(event)
      assertTrue(Toggleable.isSelected(event.presentation))
      for (child in children.drop(2)) {
        child.update(event)
        assertFalse(Toggleable.isSelected(event.presentation))
      }

      // Choose particular target
      children[2].actionPerformed(TestActionEvent.createTestEvent(dataContext))
    }

    runInEdtAndWait { menuAction.updateActions(dataContext) }
    menuAction.getChildren(null).let { children ->
      val event = TestActionEvent.createTestEvent(dataContext)
      // Automatically pick best should not be selected
      children[0].update(event)
      assertFalse(Toggleable.isSelected(event.presentation))

      // The performed action should be selected
      children[2].update(event)
      assertTrue(Toggleable.isSelected(event.presentation))

      // Other action is not selected
      for (child in children.drop(3)) {
        child.update(event)
        assertFalse(Toggleable.isSelected(event.presentation))
      }

      // Select Automatically Pick Best action
      children[0].actionPerformed(TestActionEvent.createTestEvent(dataContext))
    }

    menuAction.updateActions(dataContext)
    menuAction.getChildren(null).let { children ->
      val event = TestActionEvent.createTestEvent(dataContext)
      // Automatically pick best should be selected
      children[0].update(event)
      assertTrue(Toggleable.isSelected(event.presentation))

      // Other actions should not be selected
      for (child in children.drop(2)) {
        child.update(event)
        assertFalse(Toggleable.isSelected(event.presentation))
      }
    }
  }

  private fun createSpiedConfigurationManager(): ConfigurationManager {
    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    val spied = spy(manager)

    val highestApi = createApiTarget(30, 0, 0)
    val targets =
      arrayOf(
        createApiTarget(28, 0),
        createApiTarget(29, 0),
        highestApi,
        createApiTarget(31, 0),
        createApiTarget(32, 0, 1),
        createApiTarget(32, 1),
        createApiTarget(33, 0, 1),
        createApiTarget(33, 0, 2),
      )

    doReturn(targets).whenever(spied).targets
    doReturn(highestApi).whenever(spied).highestApiTarget
    return spied
  }

  private fun createApiTarget(level: Int, minorLevel: Int, revision: Int = 0): IAndroidTarget {
    val target = mock<IAndroidTarget>()
    whenever(target.version).thenReturn(AndroidVersion(level, minorLevel))
    whenever(target.revision).thenReturn(revision)
    whenever(target.hasRenderingLibrary()).thenReturn(true)
    whenever(target.isPlatform).thenReturn(true)
    return target
  }
}
