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
package com.android.tools.idea.debug

import com.android.repository.api.UpdatablePackage
import com.android.repository.testframework.FakePackage.FakeRemotePackage
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.debug.AndroidPositionManager.Companion.changeClassExtensionToJava
import com.android.tools.idea.debug.AndroidPositionManager.MyXDebugSessionListener
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.sdk.SdkInstallListener
import com.android.tools.idea.sdk.sources.SdkSourcePositionFinder
import com.android.tools.idea.testing.AndroidProjectRule.Companion.withSdk
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.CompoundPositionManager
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerManagerThreadImpl
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.engine.requests.RequestManagerImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.testFramework.unregisterService
import com.intellij.xdebugger.XDebugSession
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.intellij.lang.annotations.Language
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.Mockito.anyString
import org.mockito.Mockito.eq
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import java.util.concurrent.Semaphore
import kotlin.test.assertFailsWith

private const val COMPANION_PREFIX = "\$-CC"

class AndroidPositionManagerTest {
  private val projectRule = withSdk()

  @get:Rule
  val rule = RuleChain(projectRule)

  private val project get() = projectRule.project

  private val mockDebugProcessImpl: DebugProcessImpl = mock()
  private val mockDebuggerSession: DebuggerSession = mock()
  private val mockXDebugSession: XDebugSession = mock()
  private val mockProcessHandler: ProcessHandler = mock()
  private val mockCompoundPositionManager: CompoundPositionManager = mock()
  private val mockDebuggerManagerThreadImpl: DebuggerManagerThreadImpl = mock()
  private val mockSdkSourcePositionFinder: SdkSourcePositionFinder = mock()
  private val mockVirtualMachineProxyImpl: VirtualMachineProxyImpl = mock()
  private val mockClassPrepareRequestor: ClassPrepareRequestor = mock()
  private val mockRequestManagerImpl: RequestManagerImpl = mock()

  private val targetDeviceAndroidVersion: AndroidVersion = AndroidVersion(30)
  private val installedPackage = UpdatablePackage(FakeRemotePackage("sources;android-${targetDeviceAndroidVersion.apiLevel}"))
  private lateinit var myPositionManager: AndroidPositionManager
  private val allVirtualMachineClasses = mutableListOf<ReferenceType>()
  private val androidSdkClassLocation: Location
    get() {
      val type: MockReferenceType = mock()
      whenever(type.name()).thenReturn("android.view.View")
      whenever(type.sourceName()).thenReturn("View.java")
      val location: MockLocation = mock()
      whenever(location.declaringType()).thenReturn(type)
      whenever(location.lineNumber()).thenReturn(12)
      return location
    }

  @Before
  fun setUp() {
    whenever(mockDebugProcessImpl.session).thenReturn(mockDebuggerSession)
    whenever(mockDebugProcessImpl.positionManager).thenReturn(mockCompoundPositionManager)
    whenever(mockDebugProcessImpl.managerThread).thenReturn(mockDebuggerManagerThreadImpl)
    whenever(mockDebugProcessImpl.project).thenReturn(project)
    whenever(mockDebugProcessImpl.searchScope).thenReturn(GlobalSearchScope.allScope(project))
    whenever(mockDebugProcessImpl.virtualMachineProxy).thenReturn(mockVirtualMachineProxyImpl)
    whenever(mockDebugProcessImpl.processHandler).thenReturn(mockProcessHandler)
    whenever(mockDebuggerSession.xDebugSession).thenReturn(mockXDebugSession)
    whenever(mockProcessHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).thenAnswer { targetDeviceAndroidVersion }
    // todo: In reality, `allClasses` throws so, add a mode where we test when it throws.
    whenever(mockVirtualMachineProxyImpl.allClasses()).thenReturn(allVirtualMachineClasses)
    whenever(mockVirtualMachineProxyImpl.classesByName(anyString())).thenAnswer { invocation ->
      allVirtualMachineClasses.filter { type ->
        type.name() == invocation.getArgument<String>(0)
      }
    }
    whenever(mockDebugProcessImpl.requestsManager).thenReturn(mockRequestManagerImpl)
    whenever(mockRequestManagerImpl.createClassPrepareRequest(any(), Mockito.anyString())).thenAnswer { invocation ->
      FakeClassPrepareRequest(invocation.arguments[1].toString())
    }

    project.registerServiceInstance(SdkSourcePositionFinder::class.java, mockSdkSourcePositionFinder)
    myPositionManager = AndroidPositionManager(mockDebugProcessImpl)
  }

  @After
  fun tearDown() {
    project.unregisterService(SdkSourcePositionFinder::class.java)

    // Invoke an action when not in dumb mode, to ensure anything queued up during the test completes before the next run.
    val semaphore = Semaphore(0)
    DumbService.getInstance(project).smartInvokeLater {
      // Similarly, invoke an action on the event thread to ensure everything finishes out appropriately.
      ApplicationManager.getApplication().invokeAndWait {}
      semaphore.release()
    }
    semaphore.acquire()
  }

  @Test
  fun testGetAcceptedFileTypes_acceptsJavaFiles() {
    val acceptedFileTypes = myPositionManager.acceptedFileTypes
    assertThat(acceptedFileTypes).hasSize(1)
    assertThat(acceptedFileTypes).containsExactly(JavaFileType.INSTANCE)
  }

  @Test
  fun sourcePosition_nullLocation() {
    assertFailsWith<NoDataException> { myPositionManager.getSourcePosition(null) }
  }

  @Test
  fun sourcePosition_androidVersionNotAvailable() {
    val location: Location = mock()
    whenever(mockProcessHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).thenAnswer { null }

    // Recreate myPositionManager, so that it reinitializes with a null version.
    myPositionManager = AndroidPositionManager(mockDebugProcessImpl)
    assertFailsWith<NoDataException> { myPositionManager.getSourcePosition(location) }

    // getSourcePosition should have exited before `location` was used.
    verifyNoInteractions(location)
  }

  @Test
  fun sourcePosition_locationHasNoDeclaringType() {
    // No declaring type results in `PositionManagerImpl.getPsiFileByLocation()` return a null PsiFile, so this tests a branch of
    // `AndroidPositionManager.getSourcePosition`.
    val location: MockLocation = mock()
    whenever(location.declaringType()).thenReturn(null)

    assertFailsWith<NoDataException> { myPositionManager.getSourcePosition(location) }
  }

  @Test
  fun sourcePosition_locationIsNonAndroidFile() {
    val type: MockReferenceType = mock()
    whenever(type.name()).thenReturn("p1.p2.Foo")

    val location: MockLocation = mock()
    whenever(location.declaringType()).thenReturn(type)

    @Language("JAVA") val text = """
      package p1.p2;
      class Foo {
        private void Bar() {
          int test = 2;
        }
      }
    """.trimIndent()

    val file = projectRule.fixture.addFileToProject("src/p1/Foo.java", text)

    runReadAction {
      // Ensure that the super class is actually finding this class.
      assertThat(myPositionManager.getPsiFileByLocation(project, location)).isSameAs(file)

      // Now that it's found, NoDataException should be thrown since it's not in the Android SDK.
      assertFailsWith<NoDataException> { myPositionManager.getSourcePosition(location) }
    }
  }

  @Test
  fun sourcePosition_locationIsAnAndroidFile() {
    runReadAction { myPositionManager.getSourcePosition(androidSdkClassLocation) }

    val lineNumber = androidSdkClassLocation.lineNumber()
    verify(mockSdkSourcePositionFinder)
      .getSourcePosition(eq(targetDeviceAndroidVersion.apiLevel), any(), eq(lineNumber - 1))
  }

  @Test
  fun refreshAfterDownload_containingPositionManagerIsCleared() {
    project.messageBus.syncPublisher(SdkInstallListener.TOPIC).installCompleted(listOf(installedPackage), emptyList())

    // Wait for the dumb service to complete if necessary, since the task captured below requires not being in dumb mode.
    val semaphore = Semaphore(0)
    DumbService.getInstance(project).smartInvokeLater { semaphore.release() }
    semaphore.acquire()

    // A task will have been put onto the mock debugger manager thread. Get it and invoke it.
    val runnableCaptor: ArgumentCaptor<Runnable> = argumentCaptor()
    verify(mockDebuggerManagerThreadImpl).invoke(any(), runnableCaptor.capture())
    val runnable = runnableCaptor.value!!
    runnable.run()

    // Now the cache should have been cleared.
    verify(mockCompoundPositionManager).clearCache()
  }

  @Test
  fun refreshAfterDownload_debugSessionRefreshed() {
    project.messageBus.syncPublisher(SdkInstallListener.TOPIC).installCompleted(listOf(installedPackage), emptyList())

    // Wait for the dumb service to complete if necessary, since the task captured below requires not being in dumb mode.
    val semaphore = Semaphore(0)
    DumbService.getInstance(project).smartInvokeLater { semaphore.release() }
    semaphore.acquire()

    // A task will have been put onto the mock debugger manager thread. Get it and invoke it.
    val runnableCaptor: ArgumentCaptor<Runnable> = argumentCaptor()
    verify(mockDebuggerManagerThreadImpl).invoke(any(), runnableCaptor.capture())
    val runnable = runnableCaptor.value!!
    runnable.run()

    // Invoke and wait for an empty runnable to clear out any waiting tasks (which include the refresh we want to test).
    ApplicationManager.getApplication().invokeAndWait {}

    // Now the session should be refreshed.
    verify(mockDebuggerSession).refresh(true)
  }

  @Test
  fun refreshAfterDownload_sessionEnded_debugSessionNotRefreshed() {
    // Mark the debug session as stopped.
    whenever(mockDebuggerSession.isStopped).thenReturn(true)

    project.messageBus.syncPublisher(SdkInstallListener.TOPIC).installCompleted(listOf(installedPackage), emptyList())

    // Wait for the dumb service to complete if necessary, since the task captured below requires not being in dumb mode.
    val semaphore = Semaphore(0)
    DumbService.getInstance(project).smartInvokeLater { semaphore.release() }
    semaphore.acquire()

    // A task will have been put onto the mock debugger manager thread. Get it and invoke it.
    val runnableCaptor: ArgumentCaptor<Runnable> = argumentCaptor()
    verify(mockDebuggerManagerThreadImpl).invoke(any(), runnableCaptor.capture())
    val runnable = runnableCaptor.value!!
    runnable.run()

    // Invoke and wait for an empty runnable to clear out any waiting tasks (which include the refresh we want to test).
    ApplicationManager.getApplication().invokeAndWait {}

    // The session should not have been refreshed, since it has stopped.
    verify(mockDebuggerSession, never()).refresh(true)
  }

  @Test
  fun changeClassExtensionToJava_notClassFile() {
    assertThat("foo.bar".changeClassExtensionToJava()).isEqualTo("foo.bar")
    assertThat("foo.java".changeClassExtensionToJava()).isEqualTo("foo.java")
  }

  @Test
  fun changeClassExtensionToJava_classFileChangedToJava() {
    assertThat("foo.class".changeClassExtensionToJava()).isEqualTo("foo.java")
  }

  @Test
  fun relPathForJavaSource_fileIsJavaClass() {
    // The case where the file is a java file is covered by above test cases; but the java class file case is not, due to difficulties in
    // mocking super class logic. Instead, we can test resolution here directly.
    val viewClass = runReadAction {
      PositionManagerImpl.findClass(project, "android.view.View", mockDebugProcessImpl.searchScope, true)
    }
    assertThat(viewClass).isNotNull()
    assertThat(AndroidPositionManager.getRelPathForJavaSource(project, viewClass!!.containingFile))
      .isEqualTo("android/view/View.java")
  }

  @Test
  fun relPathForJavaSource_unknownFileType() {
    val file: PsiFile = mock()
    whenever(file.fileType).thenReturn(UnknownFileType.INSTANCE)

    assertThat(AndroidPositionManager.getRelPathForJavaSource(project, file)).isNull()
  }

  @Test
  fun myXDebugSessionListener_sessionStopped() {
    val mockVirtualFile: VirtualFile = mock()

    val mockFileEditorManager: FileEditorManager = mock()
    val componentStack = ComponentStack(project)
    componentStack.registerServiceInstance(FileEditorManager::class.java, mockFileEditorManager)

    val listener = MyXDebugSessionListener(mockVirtualFile, project)

    listener.sessionStopped()

    // `sessionStopped` should close the file, but does so on the EDT. Queue an empty action and wait for it to complete, so that we're sure
    // the pending close action has completed.
    ApplicationManager.getApplication().invokeAndWait {}

    verify(mockFileEditorManager).closeFile(mockVirtualFile)

    componentStack.restore()
  }

  @Test
  fun createPrepareRequests_InterfaceWithStaticMethod_addsRequest() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      interface Foo {
        static void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val requests = myPositionManager.createPrepareRequests(mockClassPrepareRequestor, position)

    assertThat(requests.map { it.toString() }).containsExactly(
      "p1.p2.Foo",
      "p1.p2.Foo\$*",
    )
  }

  @Test
  fun createPrepareRequests_InnerInterfaceWithStaticMethod_addsRequest() {
    @Language("JAVA")
    val text = """
      package p1.p2;

      interface Foo {
        interface Bar {
          static void bar() {
            int test = 2; // break here
          }
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val requests = myPositionManager.createPrepareRequests(mockClassPrepareRequestor, position)

    assertThat(requests.map { it.toString() }).containsExactly(
      "p1.p2.Foo\$Bar",
      "p1.p2.Foo\$Bar\$*",
    )
  }

  @Test
  fun createPrepareRequests_InterfaceWithDefaultMethod_addsRequest() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      interface Foo {
        default void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val requests = myPositionManager.createPrepareRequests(mockClassPrepareRequestor, position)

    assertThat(requests.map { it.toString() }).containsExactly(
      "p1.p2.Foo",
      "p1.p2.Foo\$*",
    )
  }

  @Test
  fun getExtraPrepareRequests_SimpleClass_noResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      class Foo {
        static void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val requests = myPositionManager.createPrepareRequests(mockClassPrepareRequestor, position)

    assertThat(requests.map { it.toString() }).containsExactly(
      "p1.p2.Foo",
    )
  }

  @Test
  fun getExtraPrepareRequests_InterfaceWithStaticInitializer_noResults() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      interface Foo {
        String STR = "foo"
          .concat("bar"); // break here
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val requests = myPositionManager.createPrepareRequests(mockClassPrepareRequestor, position)

    assertThat(requests.map { it.toString() }).containsExactly(
      "p1.p2.Foo",
    )
  }

  private fun setupFromFile(content: String): PsiFile {
    val path = content.lineSequence().first().substringAfter("package ").substringBefore(";").trim().replace('.', '/')
    val psiFile = projectRule.fixture.addFileToProject("src/$path/Test.java", content)
    runInEdtAndWait {
      PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java).forEach { psiClass ->
        val jvmName = psiClass.getJvmName()
        val referenceType = FakeReferenceType(jvmName)
        allVirtualMachineClasses.add(referenceType)
        if (psiClass.isInterfaceWithStaticMethod()) {
          allVirtualMachineClasses.add(FakeReferenceType("$jvmName$COMPANION_PREFIX", hasLocations = true))
        }
      }
    }
    return psiFile
  }

  private fun PsiFile.getBreakpointPosition(): SourcePosition {
    val line = text.lineSequence().indexOfFirst { it.contains("break here") }
    return SourcePosition.createFromLine(this, line)
  }

  private class FakeReferenceType(
    val name: String,
    val hasLocations: Boolean = false,
    delegate: ReferenceType = mock(),
  ) : ReferenceType by delegate {
    override fun name(): String = name

    override fun locationsOfLine(stratum: String?, sourceName: String?, lineNumber: Int) =
      if (hasLocations) listOf(mock<Location>()) else emptyList()
  }

  @Test
  fun getAllClasses_InterfaceWithStaticMethod_hasResults_addsCompanion() {
    @Language("JAVA")
    val text = """
      package p1.p2;

      interface Foo {
        static void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val types = myPositionManager.getAllClasses(position)

    assertThat(types.map { it.name() }).containsExactly(
      "p1.p2.Foo",
      "p1.p2.Foo$-CC",
    )
  }

  @Test
  fun getAllClasses_InterfaceWithDefaultMethod_hasResults_addsCompanion() {
    @Language("JAVA")
    val text = """
      package p1.p2;

      interface Foo {
        default void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val types = myPositionManager.getAllClasses(position)

    assertThat(types.map { it.name() }).containsExactly(
      "p1.p2.Foo",
      "p1.p2.Foo$-CC",
    )
  }

  @Test
  fun getAllClasses_InnerInterfaceWithStaticMethod_hasResults_addsCompanion() {
    @Language("JAVA")
    val text = """
      package p1.p2;

      interface Foo {
        interface Bar {
          static void bar() {
            int test = 2; // break here
          }
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val types = myPositionManager.getAllClasses(position)

    assertThat(types.map { it.name() }).containsExactly(
      "p1.p2.Foo\$Bar",
      "p1.p2.Foo\$Bar$-CC",
    )
  }

  @Test
  fun getAllClasses_SimpleClass_noResults_doesNotAddCompanion() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      class Foo {
        static void bar() {
          int test = 2; // break here
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val types = myPositionManager.getAllClasses(position)

    assertThat(types.map { it.name() }).containsExactly(
      "p1.p2.Foo",
    )
  }

  @Test
  fun getAllClasses_InterfaceWithStaticInitializer__doesNotAddCompanion() {
    @Language("JAVA")
    val text = """
      package p1.p2;
      
      interface Foo {
        String STR = "foo"
          .concat("bar"); // break here
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val types = myPositionManager.getAllClasses(position)

    assertThat(types.map { it.name() }).containsExactly(
      "p1.p2.Foo",
    )
  }

  @Test
  fun getAllClasses_IgnoresUnrelatedInnerClass() {
    @Language("JAVA")
    val text = """
      package p1.p2;

      interface Foo {
        static void bar() {
          int test = 2; // break here
        }
        
        class Unrelated {
          static void unrelated() {
            int test = true;
          }
        }
      }
    """.trimIndent()
    val file = setupFromFile(text)
    val position = file.getBreakpointPosition()

    val types = myPositionManager.getAllClasses(position)

    assertThat(types.map { it.name() }).containsExactly(
      "p1.p2.Foo",
      "p1.p2.Foo$-CC",
    )
  }

  class FakeClassPrepareRequest(private val filter: String, delegate: ClassPrepareRequest = mock()) : ClassPrepareRequest by delegate {
    override fun toString() = filter
  }

  /*
   * IDEA doesn't like it when interfaces from `com.sun.jdi` are mocked.
   */

  private interface MockReferenceType : ReferenceType
  private interface MockLocation : Location
}
