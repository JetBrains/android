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
package com.android.tools.idea.preview

import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.preview.PreviewDisplaySettings
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class BackgroundManagerTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  private val project
    get() = projectRule.project

  @Test
  fun `setBackground and getBackground work correctly`() = runTest {
    val manager = BackgroundManager.getInstance(project)
    val psiFile = projectRule.fixture.addFileToProject("Test.kt", "fun test() {}")
    val element = readAction {
      SmartPointerManager.getInstance(project).createSmartPsiElementPointer<PsiFile>(psiFile)
    }
    val background = PreviewDisplaySettings.Background.Image {}

    manager.setBackground(element, background)
    assertEquals(background, manager.getBackground(element))
  }

  @Test
  fun `setBackground with null removes the background`() = runTest {
    val manager = BackgroundManager.getInstance(project)
    val psiFile = projectRule.fixture.addFileToProject("Test.kt", "fun test() {}")
    val element = readAction {
      SmartPointerManager.getInstance(project).createSmartPsiElementPointer<PsiFile>(psiFile)
    }
    val background = PreviewDisplaySettings.Background.Image {}

    manager.setBackground(element, background)
    assertEquals(background, manager.getBackground(element))

    manager.setBackground(element, null)
    assertNull(manager.getBackground(element))
  }

  @Test
  fun `modificationTracker updates on background changes`() = runTest {
    val manager = BackgroundManager.getInstance(project)
    val psiFile = projectRule.fixture.addFileToProject("Test.kt", "fun test() {}")
    val element = readAction {
      SmartPointerManager.getInstance(project).createSmartPsiElementPointer<PsiFile>(psiFile)
    }
    val background1 = PreviewDisplaySettings.Background.Image {}
    val background2 = PreviewDisplaySettings.Background.Image {}

    val initialCount = manager.modificationTracker.modificationCount
    println(">> $initialCount")

    // Set for the first time
    manager.setBackground(element, background1)
    assertEquals(initialCount + 1, manager.modificationTracker.modificationCount)

    // Set the same background, no modification
    manager.setBackground(element, background1)
    assertEquals(initialCount + 1, manager.modificationTracker.modificationCount)

    // Set a different background
    manager.setBackground(element, background2)
    val countAfterSecondSet = manager.modificationTracker.modificationCount
    assertEquals(initialCount + 2, countAfterSecondSet)

    // Remove background
    manager.setBackground(element, null)
    val countAfterRemove = manager.modificationTracker.modificationCount
    assertEquals(initialCount + 3, countAfterRemove)

    // Remove again, no modification
    manager.setBackground(element, null)
    assertEquals(initialCount + 3, manager.modificationTracker.modificationCount)
  }

  @Test
  fun `modificationFlow emits on background changes`() = runTest {
    val manager = BackgroundManager.getInstance(project)
    val psiFile = projectRule.fixture.addFileToProject("Test.kt", "fun test() {}")
    val element = readAction {
      SmartPointerManager.getInstance(project).createSmartPsiElementPointer<PsiFile>(psiFile)
    }
    val background = PreviewDisplaySettings.Background.Image {}

    val initialCount = manager.modificationFlow.first()

    manager.setBackground(element, background)
    val countAfterSet = manager.modificationFlow.first()
    assertEquals(initialCount + 1, countAfterSet)

    manager.setBackground(element, null)
    val countAfterRemove = manager.modificationFlow.first()
    assertEquals(initialCount + 2, countAfterRemove)
  }
}
