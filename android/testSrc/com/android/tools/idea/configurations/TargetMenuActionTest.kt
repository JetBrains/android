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
package com.android.tools.idea.configurations

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.sdklib.AndroidVersion
import com.android.sdklib.IAndroidTarget
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.actions.prettyPrintActions
import com.android.tools.idea.configurations.TargetMenuAction.SetTargetAction
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.android.AndroidTestCase
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.spy

class TargetMenuActionTest : AndroidTestCase() {

  fun testUpdateTargets() {
    val file = runInEdtAndGet { myFixture.addFileToProject("res/layout/layout.xml", "") }
    val manager = createSpiedConfigurationManager()

    val config = Configuration(manager, file.virtualFile, FolderConfiguration())
    val menuAction = TargetMenuAction { config }
    menuAction.updateActions(DataContext.EMPTY_CONTEXT)

    val expected = """30
    Automatically Pick Best
    ------------------------------------------------------
    33
    32
    31
    30
    29
    28
"""

    val actual = prettyPrintActions(menuAction)
    assertThat(actual).isEqualTo(expected)
  }

  fun testNoDuplicateActionForDifferentRevision() {
    val file = runInEdtAndGet { myFixture.addFileToProject("res/layout/layout.xml", "") }
    val manager = createSpiedConfigurationManager()

    val config = Configuration(manager, file.virtualFile, FolderConfiguration())
    val menuAction = TargetMenuAction { config }
    menuAction.updateActions(DataContext.EMPTY_CONTEXT)

    val children = menuAction.getChildren(null)
    // First child is TargetMenuAction.TogglePickBestAction, second child is Separator
    val target = (children[2] as SetTargetAction).myTarget

    assertThat(target.version.apiLevel).isEqualTo(33)
    assertThat(target.revision).isEqualTo(2)
  }

  private fun createSpiedConfigurationManager(): ConfigurationManager {
    val manager = ConfigurationManager.getOrCreateInstance(myModule)
    val spied = spy(manager)

    val highestApi = createApiTarget(30, 0)
    val targets = arrayOf(createApiTarget(28),
                          createApiTarget(29),
                          highestApi,
                          createApiTarget(31),
                          createApiTarget(32),
                          createApiTarget(33),
                          createApiTarget(33, 1),
                          createApiTarget(33, 2)
    )

    doReturn(targets).whenever(spied).targets
    doReturn(highestApi).whenever(spied).highestApiTarget
    return spied
  }

  private fun createApiTarget(level: Int, revision: Int = 0): IAndroidTarget {
    val target = mock<IAndroidTarget>()
    whenever(target.version).thenReturn(AndroidVersion(level))
    whenever(target.revision).thenReturn(revision)
    whenever(target.hasRenderingLibrary()).thenReturn(true)
    whenever(target.isPlatform).thenReturn(true)
    return target
  }
}

