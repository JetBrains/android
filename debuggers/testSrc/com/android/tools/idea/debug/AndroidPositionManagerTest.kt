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

import com.android.SdkConstants
import com.android.repository.api.LocalPackage
import com.android.repository.api.RepoPackage
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.argumentCaptor
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.debug.AndroidPositionManager.Companion.changeClassExtensionToJava
import com.android.tools.idea.debug.AndroidPositionManager.MyXDebugSessionListener
import com.android.tools.idea.editors.AttachAndroidSdkSourcesNotificationProvider
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.sdk.AndroidSdks
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
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
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
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.withSettings
import org.mockito.junit.MockitoJUnit
import java.util.concurrent.Semaphore
import kotlin.test.assertFailsWith

class AndroidPositionManagerTest {
  @get:Rule
  val myAndroidProjectRule = withSdk()

  @get:Rule
  val myMockitoRule = MockitoJUnit.rule()

  private val mockDebugProcessImpl: DebugProcessImpl = mock()
  private val mockDebuggerSession: DebuggerSession = mock()
  private val mockXDebugSession: XDebugSession = mock()
  private val mockProcessHandler: ProcessHandler = mock()
  private val mockCompoundPositionManager: CompoundPositionManager = mock()
  private val mockDebuggerManagerThreadImpl: DebuggerManagerThreadImpl = mock()

  private val targetDeviceAndroidVersion: AndroidVersion = AndroidVersion(30)

  private lateinit var myPositionManager: AndroidPositionManager

  private var myOriginalLocalPackages: Collection<LocalPackage>? = null

  @Before
  fun setUp() {
    whenever(mockDebugProcessImpl.session).thenReturn(mockDebuggerSession)
    whenever(mockDebugProcessImpl.positionManager).thenReturn(mockCompoundPositionManager)
    whenever(mockDebugProcessImpl.managerThread).thenReturn(mockDebuggerManagerThreadImpl)
    whenever(mockDebugProcessImpl.project).thenReturn(myAndroidProjectRule.project)
    whenever(mockDebugProcessImpl.searchScope).thenReturn(GlobalSearchScope.allScope(myAndroidProjectRule.project))
    whenever(mockDebugProcessImpl.processHandler).thenReturn(mockProcessHandler)
    whenever(mockDebuggerSession.xDebugSession).thenReturn(mockXDebugSession)

    whenever(mockProcessHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).thenAnswer { targetDeviceAndroidVersion }

    myPositionManager = AndroidPositionManager(mockDebugProcessImpl)
  }

  @After
  fun tearDown() {
    restoreLocalTargetSdkPackages()

    // Invoke an action when not in dumb mode, to ensure anything queued up during the test completes before the next run.
    val semaphore = Semaphore(0)
    DumbService.getInstance(myAndroidProjectRule.project).smartInvokeLater {
      // Similarly, invoke an action on the event thread to ensure everything finishes out appropriately.
      ApplicationManager.getApplication().invokeAndWait {}
      semaphore.release()
    }
    semaphore.acquire()
  }

  /**
   * Create a SourcePosition from a one-based line number.
   */
  private fun createSourcePositionForOneBasedLineNumber(psiFile: PsiFile, line: Int): SourcePosition {
    assert(line > 0)
    // SourcePositions are zero-based. Therefore, we need to adjust the line number accordingly.
    return SourcePosition.createFromLine(psiFile, line - 1)
  }

  @Test
  fun testDesugaringSupport_SimpleClass() {
    @Language("JAVA") val text = """package p1.p2;

class Foo {
  public void bar() {
    int test = 2;
  }

  class Inner {
    static void doSomething() {
    }
  }
}"""
    val file = myAndroidProjectRule.fixture.addFileToProject("src/p1/p2/Foo.java", text)
    assertThat(file).isNotNull()
    val position = createSourcePositionForOneBasedLineNumber(file, 5)
    runTestDesugaringSupportWhenDesugaringIsRequired(position, false)
  }

  @Test
  fun testDesugaringSupport_InterfaceWithStaticInitializer() {
    @Language("JAVA") val text = """package p1.p2;

interface Foo {
  public static final String STR = new String()
    .concat("foo");

  class Inner {
    static void doSomething() {
    }
  }
}"""
    val file = myAndroidProjectRule.fixture.addFileToProject("src/p1/p2/Foo.java", text)
    assertThat(file).isNotNull()
    val position = createSourcePositionForOneBasedLineNumber(file, 5)
    runTestDesugaringSupportWhenDesugaringIsRequired(position, false)
  }

  @Test
  fun testDesugaringSupport_InterfaceWithDefaultMethod() {
    @Language("JAVA") val text = """package p1.p2;

interface Foo {
  default void bar() {
    int test = 2;
  }

  class Inner {
    static void doSomething() {
    }
  }
}"""
    val file = myAndroidProjectRule.fixture.addFileToProject("src/p1/p2/Foo.java", text)
    assertThat(file).isNotNull()
    val position = createSourcePositionForOneBasedLineNumber(file, 5)
    runTestDesugaringSupportWhenDesugaringIsRequired(position, true)
  }

  @Test
  fun testDesugaringSupport_InterfaceWithStaticMethod() {
    @Language("JAVA") val text = """package p1.p2;

interface Foo {
  static void bar() {
    int test = 2;
  }

  class Inner {
    static void doSomething() {
    }
  }
}"""
    val file = myAndroidProjectRule.fixture.addFileToProject("src/p1/p2/Foo.java", text)
    assertThat(file).isNotNull()
    val position = createSourcePositionForOneBasedLineNumber(file, 5)
    runTestDesugaringSupportWhenDesugaringIsRequired(position, true)
  }

  private fun runTestDesugaringSupportWhenDesugaringIsRequired(position: SourcePosition, isDesugaringRequired: Boolean) {
    // Mock the VirtualMachine proxy to manage tested types.
    val vmProxy: VirtualMachineProxyImpl = mock()
    whenever(mockDebugProcessImpl!!.virtualMachineProxy).thenReturn(vmProxy)
    val typesMap = mockReferenceTypes(vmProxy, TOP_CLASS_NAME, INNER_CLASS_NAME, SYNTHESIZED_CLASS_NAME)

    // Mock the RequestManager for the class prepare requests.
    val mockRequestManager: RequestManagerImpl = mock()
    whenever(mockDebugProcessImpl.requestsManager).thenReturn(mockRequestManager)

    // Attach current project to the mocked debug process.
    whenever(mockDebugProcessImpl.project).thenReturn(myAndroidProjectRule.project)

    // Mock locationsOfLine to reflect which class contains the source position.
    val topClass = typesMap[TOP_CLASS_NAME]!!
    val innerClassWithoutLocation = typesMap[INNER_CLASS_NAME]!!
    val desugarCompanionClass = typesMap[SYNTHESIZED_CLASS_NAME]!!
    val mockLocation: Location = mock()
    if (isDesugaringRequired) {
      // If desugaring applies to an interface, its code moves to a synthesized class
      whenever(myPositionManager.locationsOfLine(topClass, position)).thenReturn(emptyList())
      whenever(myPositionManager.locationsOfLine(desugarCompanionClass, position)).thenReturn(listOf(mockLocation))
    }
    else {
      // If desugaring was not needed, the interface remains unchanged.
      whenever(myPositionManager.locationsOfLine(topClass, position)).thenReturn(listOf(mockLocation))
      whenever(myPositionManager.locationsOfLine(desugarCompanionClass, position)).thenReturn(emptyList())
    }
    // The existing inner class is not related to the source position.
    whenever(myPositionManager.locationsOfLine(innerClassWithoutLocation, position)).thenReturn(emptyList())

    // Check that the list of types contains both the top class and the potential synthesized class.
    val typesWithPosition = myPositionManager.getAllClasses(position)
    assertThat(typesWithPosition).isNotNull()
    if (isDesugaringRequired) {
      // If desugaring may happen, both interface and its companion class should be returned.
      assertThat(typesWithPosition).hasSize(2)
      assertThat(typesWithPosition).containsExactly(topClass, desugarCompanionClass)
    }
    else {
      // Without desugaring, the interface is the only class that contains the source position.
      assertThat(typesWithPosition).hasSize(1)
      assertThat(typesWithPosition).containsExactly(topClass)
    }

    // Mock class prepare requests.
    val topClassPrepareRequest: ClassPrepareRequest = mock(withSettings().name("CPR:$TOP_CLASS_NAME").defaultAnswer(RETURNS_DEFAULTS))
    val allInnerClassesPrepareRequest: ClassPrepareRequest = mock(
      withSettings().name("CPR:all inner classes").defaultAnswer(RETURNS_DEFAULTS))
    whenever(mockRequestManager.createClassPrepareRequest(ArgumentMatchers.notNull(), ArgumentMatchers.eq(TOP_CLASS_NAME)))
      .thenReturn(topClassPrepareRequest)
    whenever(mockRequestManager.createClassPrepareRequest(ArgumentMatchers.notNull(), ArgumentMatchers.eq("$TOP_CLASS_NAME$*")))
      .thenReturn(allInnerClassesPrepareRequest)
    val mockRequestor: ClassPrepareRequestor = mock()
    val classPrepareRequests = myPositionManager.createPrepareRequests(mockRequestor, position)
    assertThat(classPrepareRequests).isNotNull()
    if (isDesugaringRequired) {
      // If desugaring is required, we also create a class prepare request for all inner types of the interface so that we can find
      // the source position in the companion class (which is one of the inner classes).
      assertThat(classPrepareRequests).hasSize(2)
      assertThat(classPrepareRequests).containsExactly(topClassPrepareRequest, allInnerClassesPrepareRequest)
    }
    else {
      assertThat(classPrepareRequests).hasSize(1)
      assertThat(classPrepareRequests).containsExactly(topClassPrepareRequest)
    }
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
    val location: Location = mock()
    whenever(location.declaringType()).thenReturn(null)

    assertFailsWith<NoDataException> { myPositionManager.getSourcePosition(location) }
  }

  @Test
  fun sourcePosition_locationIsNonAndroidFile() {
    val type: ReferenceType = mock()
    whenever(type.name()).thenReturn(TOP_CLASS_NAME)

    val location: Location = mock()
    whenever(location.declaringType()).thenReturn(type)

    @Language("JAVA") val text = """package p1.p2;
class Foo {
  private void Bar() {
    int test = 2;
  }
}"""

    val file = myAndroidProjectRule.fixture.addFileToProject("src/p1/Foo.java", text)

    runReadAction {
      // Ensure that the super class is actually finding this class.
      assertThat(myPositionManager.getPsiFileByLocation(myAndroidProjectRule.project, location)).isSameAs(file)

      // Now that it's found, NoDataException should be thrown since it's not in the Android SDK.
      assertFailsWith<NoDataException> { myPositionManager.getSourcePosition(location) }
    }
  }

  @Test
  fun sourcePosition_targetSourcesAvailable() {
    val sourcePosition = runReadAction { myPositionManager.getSourcePosition(androidSdkClassLocation) }
    assertThat(sourcePosition).isNotNull()

    val file = sourcePosition!!.file
    assertThat(file.fileType).isEqualTo(JavaFileType.INSTANCE)
    assertThat(file.virtualFile.path).contains("/android-${targetDeviceAndroidVersion.apiLevel}/")
  }

  @Test
  fun sourcePosition_targetSourcesNotAvailable() {
    removeLocalTargetSdkPackages()

    val sourcePosition = runReadAction { myPositionManager.getSourcePosition(androidSdkClassLocation) }
    assertThat(sourcePosition).isNotNull()

    val file = sourcePosition!!.file
    assertThat(file.fileType).isEqualTo(JavaFileType.INSTANCE)
    assertThat(file.virtualFile.path).doesNotContain("/android-${targetDeviceAndroidVersion.apiLevel}/")
    assertThat(file.virtualFile).isInstanceOf(LightVirtualFile::class.java)
    val fileContent = (file.virtualFile as LightVirtualFile).content.toString()
    assertThat(fileContent).contains("device under debug has API level ${targetDeviceAndroidVersion.apiLevel}.")
    assertThat(file.name).isEqualTo("Unavailable Source")

    val callback = file.virtualFile.getUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY)!!
    assertThat(callback.missingSourceVersion).isEqualTo(targetDeviceAndroidVersion)
  }

  @Test
  fun refreshAfterDownload_sameLocationReturnsSourceFile() {
    removeLocalTargetSdkPackages()

    val sourceFileOriginal = runReadAction { myPositionManager.getSourcePosition(androidSdkClassLocation) }!!.file
    assertThat(sourceFileOriginal.name).isEqualTo("Unavailable Source")
    val callback = sourceFileOriginal.virtualFile.getUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY)!!

    // Mimic source download by restoring the original packages
    restoreLocalTargetSdkPackages()

    // Refresh is expected to update AndroidPositionManager's source folder internally, so that it can now find appropriate sources.
    ApplicationManager.getApplication().invokeAndWait { callback.refreshAfterDownload() }

    val sourceFileUpdated = runReadAction { myPositionManager.getSourcePosition(androidSdkClassLocation) }!!.file
    assertThat(sourceFileUpdated.fileType).isEqualTo(JavaFileType.INSTANCE)
    assertThat(sourceFileUpdated.virtualFile.path).contains("/android-${targetDeviceAndroidVersion.apiLevel}/")
  }

  @Test
  fun refreshAfterDownload_containingPositionManagerIsCleared() {
    removeLocalTargetSdkPackages()

    val sourceFileOriginal = runReadAction { myPositionManager.getSourcePosition(androidSdkClassLocation) }!!.file
    assertThat(sourceFileOriginal.name).isEqualTo("Unavailable Source")
    val callback = sourceFileOriginal.virtualFile.getUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY)!!

    // Mimic source download by restoring the original packages
    restoreLocalTargetSdkPackages()

    // Refresh is expected to update AndroidPositionManager's source folder internally, so that it can now find appropriate sources.
    ApplicationManager.getApplication().invokeAndWait { callback.refreshAfterDownload() }

    // Wait for the dumb service to complete if necessary, since the task captured below requires not being in dumb mode.
    val semaphore = Semaphore(0)
    DumbService.getInstance(myAndroidProjectRule.project).smartInvokeLater { semaphore.release() }
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
    removeLocalTargetSdkPackages()

    val sourceFileOriginal = runReadAction { myPositionManager.getSourcePosition(androidSdkClassLocation) }!!.file
    assertThat(sourceFileOriginal.name).isEqualTo("Unavailable Source")
    val callback = sourceFileOriginal.virtualFile.getUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY)!!

    // Mimic source download by restoring the original packages
    restoreLocalTargetSdkPackages()

    // Refresh is expected to update AndroidPositionManager's source folder internally, so that it can now find appropriate sources.
    ApplicationManager.getApplication().invokeAndWait { callback.refreshAfterDownload() }

    // Wait for the dumb service to complete if necessary, since the task captured below requires not being in dumb mode.
    val semaphore = Semaphore(0)
    DumbService.getInstance(myAndroidProjectRule.project).smartInvokeLater { semaphore.release() }
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
    removeLocalTargetSdkPackages()

    val sourceFileOriginal = runReadAction { myPositionManager.getSourcePosition(androidSdkClassLocation) }!!.file
    assertThat(sourceFileOriginal.name).isEqualTo("Unavailable Source")
    val callback = sourceFileOriginal.virtualFile.getUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY)!!

    // Mimic source download by restoring the original packages
    restoreLocalTargetSdkPackages()

    // Mark the debug session as stopped.
    whenever(mockDebuggerSession.isStopped).thenReturn(true)

    // Refresh is expected to update AndroidPositionManager's source folder internally, so that it can now find appropriate sources.
    ApplicationManager.getApplication().invokeAndWait { callback.refreshAfterDownload() }

    // Wait for the dumb service to complete if necessary, since the task captured below requires not being in dumb mode.
    val semaphore = Semaphore(0)
    DumbService.getInstance(myAndroidProjectRule.project).smartInvokeLater { semaphore.release() }
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

  private fun removeLocalTargetSdkPackages() {
    val packagesToRemove = setOf(
      "${SdkConstants.FD_ANDROID_SOURCES}${RepoPackage.PATH_SEPARATOR}android-${targetDeviceAndroidVersion.apiLevel}",
      "${SdkConstants.FD_PLATFORMS}${RepoPackage.PATH_SEPARATOR}android-${targetDeviceAndroidVersion.apiLevel}"
    )

    val packages = AndroidSdks.getInstance().tryToChooseSdkHandler()
      .getSdkManager(StudioLoggerProgressIndicator(AndroidPositionManager::class.java))
      .packages
    val localPackages = packages.localPackages.values

    if (myOriginalLocalPackages == null) {
      // This won't get reset at the end of each test automatically. Store original list to restore it later.
      myOriginalLocalPackages = localPackages
    }

    val updatedPackages = localPackages.filter { !packagesToRemove.contains(it.path) }
    packages.setLocalPkgInfos(updatedPackages)
  }

  private fun restoreLocalTargetSdkPackages() {
    if (myOriginalLocalPackages != null) {
      val packages = AndroidSdks.getInstance().tryToChooseSdkHandler()
        .getSdkManager(StudioLoggerProgressIndicator(AndroidPositionManager::class.java))
        .packages
      packages.setLocalPkgInfos(myOriginalLocalPackages!!)

      myOriginalLocalPackages = null
    }
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
      PositionManagerImpl.findClass(myAndroidProjectRule.project, "android.view.View", mockDebugProcessImpl.searchScope, true)
    }
    assertThat(viewClass).isNotNull()
    assertThat(AndroidPositionManager.getRelPathForJavaSource(myAndroidProjectRule.project, viewClass!!.containingFile))
      .isEqualTo("android/view/View.java")
  }

  @Test
  fun relPathForJavaSource_unknownFileType() {
    val file: PsiFile = mock()
    whenever(file.fileType).thenReturn(UnknownFileType.INSTANCE)

    assertThat(AndroidPositionManager.getRelPathForJavaSource(myAndroidProjectRule.project, file)).isNull()
  }

  @Test
  fun myXDebugSessionListener_sessionStopped() {
    val mockVirtualFile: VirtualFile = mock()

    val mockFileEditorManager: FileEditorManager = mock()
    val componentStack = ComponentStack(myAndroidProjectRule.project)
    componentStack.registerComponentInstance(FileEditorManager::class.java, mockFileEditorManager)

    val listener = MyXDebugSessionListener(mockVirtualFile, myAndroidProjectRule.project)

    listener.sessionStopped()

    verify(mockFileEditorManager).closeFile(mockVirtualFile)

    componentStack.restore()
  }

  companion object {
    // The name of the top class or interface.
    private const val TOP_CLASS_NAME = "p1.p2.Foo"

    // The name of an inner class that does not contain any tested source position. It is used to make sure we do not incorrectly consider an
    // inner class that is not related to the breakpoint position that is set up.
    private const val INNER_CLASS_NAME = "$TOP_CLASS_NAME\$Inner"

    // The name of an inner class that would be the "companion" class to support desugaring. This is the class that will contain the actual
    // code at execution time. Therefore this is the type where the breakpoint position will be set up.
    // Note: the name of the synthesized class does not matter. But it has to be an inner class.
    private const val SYNTHESIZED_CLASS_NAME = "$TOP_CLASS_NAME\$DespairingCompanion"

    private fun mockReferenceTypes(mockVmProxy: VirtualMachineProxyImpl, vararg typeNames: String): Map<String, ReferenceType> {
      val map = typeNames.associateWith { typeName ->
        val type: ReferenceType = mock(withSettings().name(typeName).defaultAnswer(RETURNS_DEFAULTS))!!
        whenever(type.name()).thenReturn(typeName)
        whenever(mockVmProxy.classesByName(typeName)).thenReturn(listOf(type))
        type
      }

      whenever(mockVmProxy.allClasses()).thenReturn(map.values.toList())
      return map
    }

    private val androidSdkClassLocation: Location
      private get() {
        val type: ReferenceType = mock()
        whenever(type.name()).thenReturn("android.view.View")
        whenever(type.sourceName()).thenReturn("View.java")
        val location: Location = mock()
        whenever(location.declaringType()).thenReturn(type)
        return location
      }
  }
}
