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

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWrapper
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiTreeChangeEventImpl
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.testFramework.utils.vfs.getPsiFile
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@Rule
public var tempDir: TemporaryFolder = TemporaryFolder()

class PsiListenerTest {
  private lateinit var myProject: Project

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    setUpComposeInProjectFixture(projectRule)
    myProject = projectRule.project

  }

  // Acts as a group of PSI listeners that collects all the EditEvents in a list for validation
  class EditEventCollector {
    val editEvents = ArrayList<EditEvent>()
    private fun onPsiChanged (event: EditEvent) = editEvents.add(event)
    fun startListening() = PsiListener(::onPsiChanged)
  }

  @Test
  fun simpleChange() {
    var file = projectRule.fixture.configureByText("A.kt", "fun foo() : String { return \"I am foo\"} fun bar() = 1")
    var string = findFirst<KtLiteralStringTemplateEntry>(file) { true }

    var collector = EditEventCollector()
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(collector.startListening(), projectRule.testRootDisposable)

    WriteCommandAction.runWriteCommandAction(myProject) {
      (string.firstChild as LeafElement).replaceWithText("hi")
    }

    assert(collector.editEvents[0].file == file)
    assert(collector.editEvents[0].origin is KtNamedFunction)
  }

  @Test
  fun javaChange() {
    var file = projectRule.fixture.configureByText("A.java", "class A {public A() { System.out.println(\"foo\"); } }")
    var string = findFirst<PsiLiteralExpression>(file) { true }

    var collector = EditEventCollector()
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(collector.startListening(), projectRule.testRootDisposable)

    WriteCommandAction.runWriteCommandAction(myProject) {
      (string.firstChild as LeafElement).replaceWithText("hi")
    }

    assert(collector.editEvents[0].file == file)
    assert(collector.editEvents[0].unsupportedPsiEvents[0] == UnsupportedPsiEvent.NON_KOTLIN)
  }

  @Test
  fun importChange() {
    var file = projectRule.fixture.configureByText("A.kt", "import foo.bar")
    var string = findFirst<KtReferenceExpression>(file) { true }

    var collector = EditEventCollector()
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(collector.startListening(), projectRule.testRootDisposable)

    WriteCommandAction.runWriteCommandAction(myProject) {
      (string.firstChild as LeafElement).replaceWithText("hi")
    }

    assert(collector.editEvents[0].file == file)
    assert(collector.editEvents[0].unsupportedPsiEvents[0] == UnsupportedPsiEvent.IMPORT_DIRECTIVES)
  }

  @Test
  fun constructorChange() {
    var file = projectRule.fixture.configureByText("A.kt", "class A { constructor(a : A) { print(\"foo\") } }")
    var string = findFirst<KtReferenceExpression>(file) { true }

    var collector = EditEventCollector()
    PsiManager.getInstance(myProject).addPsiTreeChangeListener(collector.startListening(), projectRule.testRootDisposable)

    WriteCommandAction.runWriteCommandAction(myProject) {
      (string.firstChild as LeafElement).replaceWithText("hi")
    }

    assert(collector.editEvents[0].file == file)
    assert(collector.editEvents[0].unsupportedPsiEvents[0] == UnsupportedPsiEvent.CONSTRUCTORS)
  }

  @Test
  fun fileOutsideOfProject() {
    var collector = EditEventCollector()
    var listener = collector.startListening()

    PsiManager.getInstance(myProject).addPsiTreeChangeListener(listener, projectRule.testRootDisposable)
    tempDir.create()
    var file = tempDir.newFile()
    var vFile = VirtualFileWrapper(file).virtualFile!!
    var event = PsiTreeChangeEventImpl(PsiManager.getInstance(myProject))

    WriteCommandAction.runWriteCommandAction(myProject) {
      FileEditorManager.getInstance(myProject).openFile(vFile)
      event.file = vFile.getPsiFile(myProject)
      listener.beforeChildrenChange(event)
    }
    assert(collector.editEvents.isEmpty())
  }
}