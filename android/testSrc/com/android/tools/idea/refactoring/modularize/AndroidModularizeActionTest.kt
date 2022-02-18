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
import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.ProjectSystemService
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager
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
import org.jetbrains.kotlin.miniStdLib.letIf
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.Mockito
import org.mockito.Mockito.`when` as given

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
      assertThat(action.isAvailableInEditorOnly()).isFalse()
    }
  }

  @RunWith(Parameterized::class)
  class IsAvailableForFileTest(val case: Case) {
    data class Case(val expected: Boolean, val fileInfo: FileInfo?) {
      val project = mock<Project>()
      val file = fileInfo?.let { fileInfo ->
        mock<PsiFile>().apply {
          given(fileType).thenReturn(fileInfo.fileType)
          given(project).thenReturn(this@Case.project)
        }
      }
    }

    data class FileInfo(val fileType: FileType, val hasAndroidFacets: Boolean)
    companion object {
      @JvmStatic
      @Parameters(name = "{0}")
      fun data() = listOf(
        // null file
        Case(expected = false, fileInfo = null),
        // arbitrary file
        Case(expected = false, FileInfo(fileType = mock(), hasAndroidFacets = false)),
        Case(expected = false, FileInfo(fileType = mock(), hasAndroidFacets = true)),
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
      val aUtils = mockStatic<AndroidUtils>()
      aUtils.use {
        case.fileInfo?.let { fileInfo ->
          given(AndroidUtils.hasAndroidFacets(case.project)).thenReturn(fileInfo.hasAndroidFacets)
        }
        assertThat(action.isAvailableForFile(case.file)).isEqualTo(case.expected)
      }
    }
  }

  /* This is a helper to handle a tricky bit of mocking used in a of couple tests.

     Due to the @RunWith(Enclosed), tests are in nested classes, which prevents
     them from accessing a function at the outer class level. Making them inner classes
     could fix that, but then it would forbid companion objects, which are required
     to use @RunWith(Parameterized). Thus, I settled on using a class both test
     classes could inherit from. The class is abstract so that the Enclosed
     runner doesn't complain about there not being a test in it.

     Unfortunately, there are some difficulties with vanilla Mockito here due to some Kotlin features.
     `project.getSyncManager()` is an extension method of AndroidProjectSystem, which normally
     should be a static function under the hood, but AndroidProjectSystem uses @JvmName and
     I can't find a way to specify the correct class to Mockito to get it to attach.
     Thus, we need to sadly look at the implementation of the extension method and go deeper.

     Ultimately, this turns into `ProjectSystemService.getInstance(<project>).projectSystem`.
     The issue here is that `getInstance` is a companion object method, which I also can't
     get Mockito to properly attach to. Thus, we have to go deeper _again_ :(

     Finally, we end up at `project.getService(ProjectSystemService.class.java)`, which is a
     normal method we can mock on something we already have (the project) and takes something else
     that we need to mock anyway (ProjectSystemService due to the `projectSystem` property).
     From there we can mock the `projectSystem` property to give a mock AndroidProjectSystem,
     which we can mock the `getSyncManager` method to give a mock ProjectSystemSyncManager.
     Since the extension function `getSyncManager` ends up expanding into the normal method version
     this puts us back into the actual call chain in the SUT.

     If we had one of the kotlin mockito extensions (like mockito-kotlin) then this could be avoidable.
     Those are supposed to support mocking extension functions using a normal style, which would let us scrap this.
     Hopefully, we can eventually adopt one of those and delete this mess,
     but in the meantime we can hide it away here and pretend it doesn't exist.
   */
  abstract class CanMockProjectGetSyncManager {
    protected fun mockProjectGetSyncManager(project: Project, syncManager: ProjectSystemSyncManager) {
      val projSysServ = mock<ProjectSystemService>()
      given(project.getService(ProjectSystemService::class.java)).thenReturn(projSysServ)
      val androidProjSys = mock<AndroidProjectSystem>()
      given(projSysServ.projectSystem).thenReturn(androidProjSys)
      given(androidProjSys.getSyncManager()).thenReturn(syncManager)
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
    ) : CanMockProjectGetSyncManager() {
      val project = lastSyncOk?.let {
        val proj = mock<Project>()
        val syncMang = mock<ProjectSystemSyncManager>()
        mockProjectGetSyncManager(proj, syncMang)
        val syncRes = mock<ProjectSystemSyncManager.SyncResult>()
        given(syncMang.getLastSyncResult()).thenReturn(syncRes)
        given(syncRes.isSuccessful).thenReturn(lastSyncOk)
        proj
      }
      val file = fileAvail?.let {
        mock<PsiFile>()
      }
      val availableFile = mock<PsiFile>()
      private val availableElement = mock<PsiElement>().also {
        given(it.containingFile).thenReturn(availableFile)
      }
      val unavailableFile = mock<PsiFile>()
      private val unavailableElement = mock<PsiElement>().also {
        given(it.containingFile).thenReturn(unavailableFile)
      }
      val elements = Array(numAvailElem) { availableElement }
        .letIf(addUnavailElem) { it + unavailableElement }
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
        Mockito.doReturn(case.fileAvail).`when`(action).isAvailableForFile(it)
      }
      Mockito.doReturn(true).`when`(action).isAvailableForFile(case.availableFile)
      Mockito.doReturn(false).`when`(action).isAvailableForFile(case.unavailableFile)

      val dc = mock<DataContext>()
      given(CommonDataKeys.PROJECT.getData(dc)).thenReturn(case.project)
      given(CommonDataKeys.PSI_FILE.getData(dc)).thenReturn(case.file)
      // getPsiElementArray is a super class static method
      mockStatic<BaseRefactoringAction>().use {
        given(BaseRefactoringAction.getPsiElementArray(dc)).thenReturn(case.elements)

        // finally, we can actually do the test
        assertThat(action.isEnabledOnDataContext(dc)).isEqualTo(case.expect)
      }
    }
  }

  @RunWith(Parameterized::class)
  class IsAvailableOnElementInEditorAndFileTest(val case: Case) {
    data class Case(val lastSyncSuccessful: Boolean) : CanMockProjectGetSyncManager() {
      val file = mock<PsiFile>().also {
        val project = mock<Project>()
        given(it.project).thenReturn(project)
        val syncManager = mock<ProjectSystemSyncManager>()
        mockProjectGetSyncManager(project, syncManager)
        val syncResult = mock<ProjectSystemSyncManager.SyncResult>()
        given(syncManager.getLastSyncResult()).thenReturn(syncResult)
        given(syncResult.isSuccessful).thenReturn(lastSyncSuccessful)
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

      assertThat(action.isAvailableOnElementInEditorAndFile(mock(), mock(), case.file, mock())).isEqualTo(case.lastSyncSuccessful)
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
      val dc: DataContext = mock()
      assertThat(action.getHandler(dc)).isInstanceOf(AndroidModularizeHandler::class.java)
    }
  }
}