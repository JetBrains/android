/*
 * Copyright (C) 2016 The Android Open Source Project
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
@file:JvmName("AndroidTestUtils")

package com.android.tools.idea.testing

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.junit.Assert.assertTrue

/**
 * Finds an [IntentionAction] with given name, if present.
 */
fun CodeInsightTestFixture.getIntentionAction(message: String) = availableIntentions.firstOrNull { it.text == message }

/**
 * Finds an intention action with given name and class, if present.
 */
fun <T> CodeInsightTestFixture.getIntentionAction(aClass: Class<T>, message: String): T? where T : IntentionAction {
  return availableIntentions
    .asSequence()
    .filter { it.text == message }
    .map { (it as? IntentionActionDelegate)?.delegate ?: it }
    .filterIsInstance(aClass)
    .firstOrNull()
}

/**
 * Moves caret in the currently open editor to position indicated by the [window] string.
 *
 * The [window] string needs to contain a `|` character surrounded by a prefix and/or suffix to be found in the file. The file is searched
 * for the concatenation of prefix and suffix strings and the caret is placed at the first matching offset, between the prefix and suffix.
 */
fun CodeInsightTestFixture.moveCaret(window: String): PsiElement {
  val text = editor.document.text
  val delta = window.indexOf("|")
  assertTrue("No caret marker found in the window.", delta != -1)
  val target = window.substring(0, delta) + window.substring(delta + 1)
  val start = text.indexOf(target)
  assertTrue("Didn't find the string '$target' in the source '$text'", start != -1)
  val offset = start + delta
  editor.caretModel.moveToOffset(offset)
  // myFixture.elementAtCaret seems to do something else
  return file.findElementAt(offset)!!
}

/**
 * Creates a new file with the given contents under the given path, treated as relative to the project root. Opens the file in the
 * in-memory editor and returns the corresponding [PsiFile].
 */
fun CodeInsightTestFixture.loadNewFile(path: String, contents: String): PsiFile {
  val virtualFile = VfsTestUtil.createFile(project.guessProjectDir()!!, path, contents)
  configureFromExistingVirtualFile(virtualFile)
  return file
}

/**
 * Marker used for caret position by [com.intellij.testFramework.EditorTestUtil.extractCaretAndSelectionMarkers]. This top-level value is
 * meant to be used in a Kotlin string template to stand out from the surrounding XML.
 */
const val caret = EditorTestUtil.CARET_TAG

/**
 * Helper function for constructing strings understood by [com.intellij.testFramework.ExpectedHighlightingData].
 *
 * Meant to be used in a Kotlin string template to stand out from the surrounding XML.
 */
infix fun String.highlightedAs(level: HighlightSeverity): String {
  // See com.intellij.testFramework.ExpectedHighlightingData
  val marker = when (level) {
    HighlightSeverity.ERROR -> "error"
    HighlightSeverity.WARNING -> "warning"
    else -> error("Don't know how to handle $this.")
  }

  return "<$marker>$this</$marker>"
}

fun CodeInsightTestFixture.goToElementAtCaret() {
  performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
}

/**
 * Finds class with the given name in the [PsiElement.getResolveScope] of the context element.
 *
 * This means using the same scope as the real code editor will use and also makes this method work with light classes, since
 * [PsiElement.getResolveScope] is subject to [ResolveScopeEnlarger]s.
 *
 * @see JavaCodeInsightTestFixture.findClass
 * @see PsiElement.getResolveScope
 */
fun JavaCodeInsightTestFixture.findClass(name: String, context: PsiElement): PsiClass? {
  return javaFacade.findClass(name, context.resolveScope)
}