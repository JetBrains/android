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
package com.android.tools.idea.refactoring.modularize

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.mockStatic
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
import com.android.tools.idea.projectsystem.getSyncManager
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.actions.BaseRefactoringAction
import org.jetbrains.android.util.AndroidUtils
import org.jetbrains.kotlin.idea.KotlinFileType
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.Mockito
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.quality.Strictness

/**
 * We need to use parameterized tests for several methods
 * so instead of making the whole thing parameterized and
 * dealing with that mess we wrap things in nested classes
 * and use the enclosed runner. Unfortunately, this means
 * everything needs to be in nested classes or it gets skipped.
 * Thus, even the methods with single case tests are wrapped.
 */
@RunWith(Enclosed::class)
class AndroidModularizeActionTest {

  @Rule
  @JvmField
  val strict: MockitoRule = MockitoJUnit.rule().strictness(Strictness.STRICT_STUBS)

  /*
  The overridden methods of AndroidModularizeAction are
  `protected` in both Java and Kotlin, but due to the
  different interpretations of `protected` they are visible
  to tests in Java, but not in Kotlin.

  To get around this difference without overly exposing
  things, we open AndroidModularizeAction (which matches
  Java's default) and create a private nested subclass in
  the test class that does nothing but make the relevant
  methods public. Since the class is private, this makes
  them visible inside the test class only.

  The class is open because one test (IsEnabledOnDataContextTest)
  ends up mocking a sibling method (see details there)
  */
  private open class TestableAndroidModularizeAction : AndroidModularizeAction() {
    public override fun isAvailableInEditorOnly() = super.isAvailableInEditorOnly()
    public override fun isAvailableForFile(file: PsiFile?) = super.isAvailableForFile(file)
    public override fun isEnabledOnDataContext(dataContext: DataContext) = super.isEnabledOnDataContext(dataContext)
    public override fun isAvailableOnElementInEditorAndFile(
      element: PsiElement,
      editor: Editor,
      file: PsiFile,
      context: DataContext,
    ) = super.isAvailableOnElementInEditorAndFile(element, editor, file, context)

    public override fun isEnabledOnElements(elements: Array<out PsiElement>) = super.isEnabledOnElements(elements)
    public override fun getHandler(dataContext: DataContext) = super.getHandler(dataContext)
  }

  class IsAvailableInEditorOnlyTest {
    @Test
    fun `isAvailableInEditorOnly always false`() {
      val action = TestableAndroidModularizeAction()
      assertThat(action.isAvailableInEditorOnly).isFalse()
    }
  }

  @RunWith(Parameterized::class)
  class IsAvailableForFileTest(val case: Case) {
    data class Case(val expected: Boolean, val fileInfo: FileInfo?) {
      val project = mock<Project>()
      val file = fileInfo?.let { fileInfo ->
        mock<PsiFile>().apply {
          whenever(fileType).thenReturn(fileInfo.fileType)
          whenever(project).thenReturn(this@Case.project)
        }
      }
    }

    data class FileInfo(val fileType: FileType, val hasAndroidFacets: Boolean) {
      // To avoid printing object references in test case names, which causes issues for analyzers, use the human-readable description
      override fun toString() = "FileInfo(fileType=${fileType.description}, hasAndroidFacets=$hasAndroidFacets)"
    }

    companion object {
      // Mock FileType instances don't have a description by default so we need to give them one
      private fun mockFileType() = mock<FileType>().also { whenever(it.description).thenReturn("mock<FileType>") }

      @JvmStatic
      @Parameters(name = "{0}")
      fun data() = listOf(
        // null file
        Case(expected = false, fileInfo = null),
        // arbitrary file
        Case(expected = false, FileInfo(mockFileType(), hasAndroidFacets = false)),
        Case(expected = false, FileInfo(mockFileType(), hasAndroidFacets = true)),
        // kotlin file
        Case(expected = false, FileInfo(KotlinFileType.INSTANCE, hasAndroidFacets = false)),
        Case(expected = false, FileInfo(KotlinFileType.INSTANCE, hasAndroidFacets = true)),
        // java file
        Case(expected = false, FileInfo(JavaFileType.INSTANCE, hasAndroidFacets = false)),
        Case(expected = true, FileInfo(JavaFileType.INSTANCE, hasAndroidFacets = true)),
      )
    }

    @Test
    fun `isAvailableForFile only for Java Android`() {
      val action = TestableAndroidModularizeAction()
      mockStatic<AndroidUtils>().use {
        case.fileInfo?.let { fileInfo ->
          whenever(AndroidUtils.hasAndroidFacets(case.project)).thenReturn(fileInfo.hasAndroidFacets)
        }
        assertThat(action.isAvailableForFile(case.file)).isEqualTo(case.expected)
      }
    }
  }

  @RunWith(Parameterized::class)
  class IsEnabledOnDataContextTest(val case: Case) {
    data class Case(
      val expect: Boolean,
      val lastSyncOk: Boolean?,
      val fileAvail: Boolean?,
      val numAvailElem: Int,
      val addUnavailElem: Boolean,
    ) {
      val project = lastSyncOk?.let { mock<Project>() }
      val syncManager = lastSyncOk?.let {
        mock<ProjectSystemSyncManager>().also {
          val syncRes = mock<ProjectSystemSyncManager.SyncResult>()
          whenever(it.getLastSyncResult()).thenReturn(syncRes)
          whenever(syncRes.isSuccessful).thenReturn(lastSyncOk)
        }
      }
      val file = fileAvail?.let {
        mock<PsiFile>()
      }
      val availableFile = mock<PsiFile>()
      private val availableElement = mock<PsiElement>().also {
        whenever(it.containingFile).thenReturn(availableFile)
      }
      val unavailableFile = mock<PsiFile>()
      private val unavailableElement = mock<PsiElement>().also {
        whenever(it.containingFile).thenReturn(unavailableFile)
      }
      val elements = Array(numAvailElem) { availableElement }
        .let { if (addUnavailElem) it + unavailableElement else it }
        .apply { shuffle() }
    }

    companion object {
      @JvmStatic
      @Parameters(name = "{0}")
      fun data() = listOf(0, 1, 5, 10).flatMap {  // check various sizes including empty
        listOf(
          // last sync failures cause failure regardless of other parameters
          Case(expect = false, lastSyncOk = false, fileAvail = false, numAvailElem = it, addUnavailElem = false),
          Case(expect = false, lastSyncOk = false, fileAvail = false, numAvailElem = it, addUnavailElem = true),
          Case(expect = false, lastSyncOk = false, fileAvail = true, numAvailElem = it, addUnavailElem = false),
          Case(expect = false, lastSyncOk = false, fileAvail = true, numAvailElem = it, addUnavailElem = true),
          Case(expect = false, lastSyncOk = false, fileAvail = null, numAvailElem = it, addUnavailElem = false),
          Case(expect = false, lastSyncOk = false, fileAvail = null, numAvailElem = it, addUnavailElem = true),

          // an unavailable file causes failure regardless of other parameters
          Case(expect = false, lastSyncOk = true, fileAvail = false, numAvailElem = it, addUnavailElem = false),
          Case(expect = false, lastSyncOk = true, fileAvail = false, numAvailElem = it, addUnavailElem = true),
          Case(expect = false, lastSyncOk = null, fileAvail = false, numAvailElem = it, addUnavailElem = false),
          Case(expect = false, lastSyncOk = null, fileAvail = false, numAvailElem = it, addUnavailElem = true),

          // an element with an unavailable containing file causes failure regardless of other parameters
          Case(expect = false, lastSyncOk = true, fileAvail = true, numAvailElem = it, addUnavailElem = true),
          Case(expect = false, lastSyncOk = true, fileAvail = null, numAvailElem = it, addUnavailElem = true),
          Case(expect = false, lastSyncOk = null, fileAvail = true, numAvailElem = it, addUnavailElem = true),
          Case(expect = false, lastSyncOk = null, fileAvail = null, numAvailElem = it, addUnavailElem = true),

          // otherwise, things are ok
          Case(expect = true, lastSyncOk = true, fileAvail = true, numAvailElem = it, addUnavailElem = false),
          Case(expect = true, lastSyncOk = true, fileAvail = null, numAvailElem = it, addUnavailElem = false),
          Case(expect = true, lastSyncOk = null, fileAvail = true, numAvailElem = it, addUnavailElem = false),
          Case(expect = true, lastSyncOk = null, fileAvail = null, numAvailElem = it, addUnavailElem = false),
        )
      }
    }

    @Test
    fun `isEnabledOnDataContext when no sync failure and no unavailable files`() {
      // we use a spy because we need to mock a sibling method (unmocked methods are unaffected)
      val action = Mockito.spy(TestableAndroidModularizeAction())
      // the sibling method returns a boolean and generating both cases can be complex (see its own test)
      // it's simpler to just mock out both result cases to make sure we're handling all scenarios
      case.file?.let {
        // The normal syntax is iffy on spies so they suggest this version instead
        Mockito.doReturn(case.fileAvail).whenever(action).isAvailableForFile(it)
      }
      Mockito.doReturn(true).whenever(action).isAvailableForFile(case.availableFile)
      Mockito.doReturn(false).whenever(action).isAvailableForFile(case.unavailableFile)

      val dc = mock<DataContext>()
      whenever(CommonDataKeys.PROJECT.getData(dc)).thenReturn(case.project)
      whenever(CommonDataKeys.PSI_FILE.getData(dc)).thenReturn(case.file)
      // getPsiElementArray is a super class static method
      mockStatic<BaseRefactoringAction>().use {
        whenever(BaseRefactoringAction.getPsiElementArray(dc)).thenReturn(case.elements)

        val `JVM class name containing Project extension method getSyncManager` = Class.forName("com.android.tools.idea.projectsystem.ProjectSystemUtil")
        Mockito.mockStatic(`JVM class name containing Project extension method getSyncManager`).use {
          // if there is a project then hook it up to a sync manager
          case.project?.let {
            whenever(it.getSyncManager()).thenReturn(case.syncManager!!)
          }

          // finally, we can actually do the test
          assertThat(action.isEnabledOnDataContext(dc)).isEqualTo(case.expect)
        }
      }
    }
  }

  @RunWith(Parameterized::class)
  class IsAvailableOnElementInEditorAndFileTest(val case: Case) {
    data class Case(val lastSyncSuccessful: Boolean) {
      val project = mock<Project>()
      val file = mock<PsiFile>().also {
        whenever(it.project).thenReturn(project)
      }
      val syncManager = mock<ProjectSystemSyncManager>().also {
        val syncResult = mock<ProjectSystemSyncManager.SyncResult>()
        whenever(it.getLastSyncResult()).thenReturn(syncResult)
        whenever(syncResult.isSuccessful).thenReturn(lastSyncSuccessful)
      }
    }

    companion object {
      @JvmStatic
      @Parameters(name = "{0}")
      fun data() = listOf(
        Case(lastSyncSuccessful = false),
        Case(lastSyncSuccessful = true),
      )
    }

    @Test
    fun `isAvailableOnElementInEditorAndFile matches last sync status`() {
      val action = TestableAndroidModularizeAction()

      val `JVM class name containing Project extension method getSyncManager` = Class.forName("com.android.tools.idea.projectsystem.ProjectSystemUtil")
      Mockito.mockStatic(`JVM class name containing Project extension method getSyncManager`).use {
        whenever(case.project.getSyncManager()).thenReturn(case.syncManager)

        assertThat(action.isAvailableOnElementInEditorAndFile(mock(), mock(), case.file, mock())).isEqualTo(case.lastSyncSuccessful)
      }
    }
  }

  @RunWith(Parameterized::class)
  class IsEnabledOnElementsTest(val case: Case) {
    // We can't mock arrays so we'll use parameterized
    // tests to check a bunch of different lengths
    // to make sure the method doesn't depend on any elements
    data class Case(val length: Int) {
      val elements = Array(length) { mock<PsiElement>() }
    }

    companion object {
      @JvmStatic
      @Parameters(name = "{0}")
      fun data() = listOf(
        Case(length = 0),
        Case(length = 1),
        Case(length = 10),
      )
    }

    @Test
    fun `isEnabledOnElements always false`() {
      val action = TestableAndroidModularizeAction()
      assertThat(action.isEnabledOnElements(case.elements)).isFalse()
    }
  }

  class GetHandlerTest {
    // Make sure we're returning the correct handler
    @Test
    fun `getHandler is a AndroidModularizeHandler`() {
      val action = TestableAndroidModularizeAction()
      assertThat(action.getHandler(mock())).isInstanceOf(AndroidModularizeHandler::class.java)
    }
  }
}