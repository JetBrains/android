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

import com.android.tools.idea.concurrency.waitForCondition
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.guessProjectDir
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.ResolveScopeEnlarger
import com.intellij.refactoring.rename.RenameHandler
import com.intellij.refactoring.rename.inplace.MemberInplaceRenameHandler
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.util.concurrency.SameThreadExecutor
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.refactoring.renaming.KotlinResourceRenameHandler
import org.jetbrains.android.refactoring.renaming.ResourceRenameHandler
import org.junit.Assert.assertTrue
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

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
 * Renames element at caret using injected [RenameHandler]s only when Android handler is available.
 * Returns true if Android handler is available.
 *
 * We can either invoke the processor directly or go through the handler layer. Unfortunately [MemberInplaceRenameHandler] won't work in
 * unit test mode, the default handler fails for light elements and some tests depend on the logic from ResourceRenameHandler. To handle
 * that mess, rename the element only when Android handler is available.
 */
fun CodeInsightTestFixture.renameElementAtCaretUsingAndroidHandler(newName: String): Boolean {
  val context = (editor as EditorEx).dataContext
  if (ResourceRenameHandler().isAvailableOnDataContext(context) || KotlinResourceRenameHandler().isAvailableOnDataContext(context)) {
    renameElementAtCaretUsingHandler(newName)
    return true
  }
  return false
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
fun String.highlightedAs(level: HighlightSeverity, message: String?): String {
  // See com.intellij.testFramework.ExpectedHighlightingData
  val marker = when (level) {
    HighlightSeverity.ERROR -> "error"
    HighlightSeverity.WARNING -> "warning"
    HighlightSeverity.WEAK_WARNING -> "weak_warning"
    else -> error("Don't know how to handle $level.")
  }

  return if (message != null) "<$marker descr=\"$message\">$this</$marker>" else "<$marker>$this</$marker>"
}

/**
 * Helper function for constructing strings understood by [com.intellij.testFramework.ExpectedHighlightingData].
 *
 * Meant to be used in a Kotlin string template to stand out from the surrounding XML.
 */
infix fun String.highlightedAs(level: HighlightSeverity) = highlightedAs(level, null)

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

/**
 * Schedules the suspending [block] to run on the UI thread and "busy waits" for it to finish by draining the event queue.
 *
 * This is an alternative to [runBlocking] that avoids deadlocks if the current thread is the UI thread and the suspending [block] needs to
 * schedule work on the UI thread.
 */
fun <T> runDispatching(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T {
  require(SwingUtilities.isEventDispatchThread()) // That's the thread dispatchAllEventsInIdeEventQueue requires.

  val result = CoroutineScope(context).async(block = block)
  while (!result.isCompleted) {
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  }

  val value = runBlocking { result.await() } // Re-throws exceptions, if any.
  PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
  return value
}

/** Waits for the app resource repository to finish currently pending updates. */
@Throws(InterruptedException::class, TimeoutException::class)
@JvmOverloads
fun waitForResourceRepositoryUpdates(facet: AndroidFacet, timeout: Long = 2, unit: TimeUnit = TimeUnit.SECONDS) {
  waitForUpdates(ResourceRepositoryManager.getInstance(facet).projectResources, timeout, unit)
}

/** Waits for the app resource repository to finish currently pending updates. */
@Throws(InterruptedException::class, TimeoutException::class)
@JvmOverloads
fun waitForResourceRepositoryUpdates(module: Module, timeout: Long = 2, unit: TimeUnit = TimeUnit.SECONDS) {
  waitForUpdates(ResourceRepositoryManager.getInstance(module)!!.projectResources, timeout, unit)
}

/** Waits for the app resource repository to finish currently pending updates. */
@Throws(InterruptedException::class, TimeoutException::class)
@JvmOverloads
fun waitForUpdates(repository: LocalResourceRepository, timeout: Long = 2, unit: TimeUnit = TimeUnit.SECONDS) {
  if (EdtInvocationManager.getInstance().isEventDispatchThread) {
    EDT.dispatchAllInvocationEvents()
  }
  val done = AtomicBoolean()
  repository.invokeAfterPendingUpdatesFinish(SameThreadExecutor.INSTANCE) { done.set(true) }
  waitForCondition(timeout, unit) { done.get() }
}

/**
 * Invalidates the file document to ensure it is reloaded from scratch. This will ensure that we run the code path that requires
 * the read lock and we ensure that the handling of files is correctly done in the right thread.
 */
private fun PsiFile.invalidateDocumentCache() = ApplicationManager.getApplication().invokeAndWait {
  val cachedDocument = PsiDocumentManager.getInstance(project).getCachedDocument(this) ?: return@invokeAndWait
  // Make sure it is invalidated
  cachedDocument.putUserData(FileDocumentManagerImpl.NOT_RELOADABLE_DOCUMENT_KEY, true)
  ApplicationManager.getApplication().runWriteAction {
    FileDocumentManager.getInstance().reloadFiles(virtualFile)
  }
}

/**
 * Same as [CodeInsightTestFixture.addFileToProject] but invalidates immediately the cached document.
 * This ensures that the code immediately after this does not work with a cached version and reloads it from disk. This
 * ensures that the loading from disk is executed and the code path that needs the read lock will be executed.
 * The idea is to help detecting code paths that require the [ReadAction] during testing.
 */
fun CodeInsightTestFixture.addFileToProjectAndInvalidate(relativePath: String, fileText: String): PsiFile =
  addFileToProject(relativePath, fileText).also {
    it.invalidateDocumentCache()
  }
