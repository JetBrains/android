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
package com.android.tools.idea.run.deployment.liveedit.analysis

import com.android.tools.idea.flags.StudioFlags

import com.android.tools.idea.run.deployment.liveedit.MutableIrClassCache
import com.android.tools.idea.run.deployment.liveedit.Precompiler
import com.android.tools.idea.run.deployment.liveedit.SourceInlineCandidateCache
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.compile
import com.android.tools.idea.run.deployment.liveedit.findFunction
import com.android.tools.idea.run.deployment.liveedit.setUpComposeInProjectFixture
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import junit.framework.Assert
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LiveEditOutputBuilderTest {
  private lateinit var myProject: Project
  private var files = HashMap<String, PsiFile>()
  private lateinit var inlineCache: SourceInlineCandidateCache
  private lateinit var irClassCache: MutableIrClassCache
  private var useDiffer = StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CLASS_DIFFER.get()

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    myProject = projectRule.project
    inlineCache = SourceInlineCandidateCache()
    irClassCache = MutableIrClassCache()
    StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CLASS_DIFFER.override(true)

    files["A.kt"] = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return \"I am foo\"} fun bar() = 1")
  }

  @After
  fun tearDown() {
    StudioFlags.COMPOSE_DEPLOY_LIVE_EDIT_CLASS_DIFFER.override(useDiffer)
  }

  @Test
  fun testNonCompose() {
    ReadAction.run<Throwable> {
      Precompiler(myProject, inlineCache).compile(files["A.kt"]!! as KtFile).forEach {
        irClassCache.update(IrClass(it))
      }
    }

    var foo = findFunction(files["A.kt"], "foo")

    WriteCommandAction.runWriteCommandAction(myProject) {
      var expresion = ((foo.bodyBlockExpression!!.firstStatement as KtReturnExpression).returnedExpression as KtStringTemplateExpression)
      Assert.assertEquals("\"I am foo\"", expresion.text)
      expresion.updateText("I am not foo")
    }

    var leOutput = compile(files["A.kt"], "foo", irClassCache)
    Assert.assertTrue(leOutput.resetState)
  }
}