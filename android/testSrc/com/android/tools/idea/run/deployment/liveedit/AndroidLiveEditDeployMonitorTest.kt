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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.editors.literals.LiveEditService
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.wireless.android.sdk.stats.LiveEditEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import junit.framework.Assert
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AndroidLiveEditDeployMonitorTest {
  private lateinit var myProject: Project

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    myProject = projectRule.project
    setUpComposeInProjectFixture(projectRule)
  }

  @Test
  fun manualModeCompileError() {
    var monitor = AndroidLiveEditDeployMonitor(LiveEditService.getInstance(myProject), myProject);
    var file = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return 1}")
    var foo = findFunction(file, "foo")
    monitor.handleChangedMethods(myProject, listOf(EditEvent(file, foo)))
    monitor.doOnManualLETrigger()
    Assert.assertEquals(1, monitor.numFilesWithCompilationErrors())
  }

  @Test
  fun autoModeCompileSuccess() {
    var monitor = AndroidLiveEditDeployMonitor(LiveEditService.getInstance(myProject), myProject);
    var file = projectRule.fixture.configureByText("A.kt", "fun foo() : Int { return 1}")
    var foo = findFunction(file, "foo")
    monitor.processChanges(myProject, listOf(EditEvent(file, foo)), LiveEditEvent.Mode.AUTO)
    monitor.onPsiChanged(EditEvent(file, foo))
    Assert.assertEquals(0, monitor.numFilesWithCompilationErrors())
  }

  @Test
  fun autoModeCompileError() {
    var monitor = AndroidLiveEditDeployMonitor(LiveEditService.getInstance(myProject), myProject);
    var file = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return 1}")
    var foo = findFunction(file, "foo")
    monitor.processChanges(myProject, listOf(EditEvent(file, foo)), LiveEditEvent.Mode.AUTO)
    monitor.onPsiChanged(EditEvent(file, foo))
    Assert.assertEquals(1, monitor.numFilesWithCompilationErrors())
  }

  @Test
  fun autoModeCompileErrorInOtherFile() {
    var monitor = AndroidLiveEditDeployMonitor(LiveEditService.getInstance(myProject), myProject);
    var file = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return 1}")
    var foo = findFunction(file, "foo")
    monitor.processChanges(myProject, listOf(EditEvent(file, foo)), LiveEditEvent.Mode.AUTO)
    monitor.onPsiChanged(EditEvent(file, foo))

    var file2 = projectRule.fixture.configureByText("B.kt", "fun foo2() {}")
    var foo2 = findFunction(file2, "foo2")
    monitor.processChanges(myProject, listOf(EditEvent(file2, foo2)), LiveEditEvent.Mode.AUTO)
    monitor.onPsiChanged(EditEvent(file2, foo2))
    Assert.assertEquals(1, monitor.numFilesWithCompilationErrors())
  }

  @Test
  fun `Auto Mode with Private and Public Inline`() {
    var monitor = AndroidLiveEditDeployMonitor(LiveEditService.getInstance(myProject), myProject);
    var file = projectRule.fixture.configureByText("A.kt", "public inline fun foo() : Int { return 1}")
    var foo = findFunction(file, "foo")
    monitor.processChanges(myProject, listOf(EditEvent(file, foo)), LiveEditEvent.Mode.AUTO)
    monitor.onPsiChanged(EditEvent(file, foo))

    var file2 = projectRule.fixture.configureByText("B.kt", "private inline fun foo2() : Int { return 1}")
    var foo2 = findFunction(file2, "foo2")
    monitor.processChanges(myProject, listOf(EditEvent(file2, foo2)), LiveEditEvent.Mode.AUTO)
    monitor.onPsiChanged(EditEvent(file2, foo2))
    Assert.assertEquals(1, monitor.numFilesWithCompilationErrors())
  }
}