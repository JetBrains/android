/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.testing.AndroidProjectRule
import com.ibm.icu.impl.Assert
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PsiEventCheckTest {
  private lateinit var myProject: Project

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    myProject = projectRule.project
  }

  @Test
  fun checkTopLevelField() {
    var file = projectRule.fixture.addFileToProject("Color.kt", "val x = Exception(\"yellow\")")
    var literal = findFirst<KtLiteralStringTemplateEntry>(file) { it.text.equals("yellow")}
    runReadAction {
      Assert.assrt(isClassFieldChanges (literal))
    }
  }

  @Test
  fun checkClassLevelField() {
    var file = projectRule.fixture.addFileToProject("Color.kt", "class X { val x = Exception(\"yellow\") }")
    var literal = findFirst<KtLiteralStringTemplateEntry>(file) { it.text.equals("yellow")}
    runReadAction {
      Assert.assrt(isClassFieldChanges (literal))
    }
  }

  @Test
  fun checkFunctionLevelDeclaration() {
    var file = projectRule.fixture.addFileToProject("Color.kt", "fun X() { val x = Exception(\"yellow\") }")
    var literal = findFirst<KtLiteralStringTemplateEntry>(file) { it.text.equals("yellow")}
    runReadAction {
      Assert.assrt(!isClassFieldChanges (literal))
    }
  }
}