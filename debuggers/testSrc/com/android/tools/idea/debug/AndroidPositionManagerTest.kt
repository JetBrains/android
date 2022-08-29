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
import com.android.tools.idea.debug.AndroidPositionManager.MyXDebugSessionListener
import com.android.tools.idea.editors.AttachAndroidSdkSourcesNotificationProvider
import com.android.tools.idea.progress.StudioLoggerProgressIndicator
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.sdk.AndroidSdks
import com.android.tools.idea.testing.AndroidProjectRule.Companion.withSdk
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Maps
import com.google.common.truth.Truth
import com.intellij.debugger.NoDataException
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.PositionManagerImpl
import com.intellij.debugger.engine.requests.RequestManagerImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.debugger.requests.ClassPrepareRequestor
import com.intellij.execution.process.ProcessHandler
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.UsefulTestCase
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import com.sun.jdi.request.ClassPrepareRequest
import org.intellij.lang.annotations.Language
import org.jetbrains.android.ComponentStack
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import org.mockito.junit.MockitoJUnit
import java.util.stream.Collectors

class AndroidPositionManagerTest {
  @Rule
  val myAndroidProjectRule = withSdk()

  @Rule
  val myMockitoRule = MockitoJUnit.rule()

  @Mock
  private val mockDebugProcessImpl: DebugProcessImpl? = null

  @Mock
  private val mockDebuggerSession: DebuggerSession? = null

  @Mock
  private val mockXDebugSession: XDebugSession? = null
  private var targetDeviceAndroidVersion: AndroidVersion? = AndroidVersion(30)
  private var myPositionManager: AndroidPositionManager? = null
  private var myOriginalLocalPackages: Collection<LocalPackage>? = null
  @Before
  fun setUp() {
    Mockito.`when`(mockDebugProcessImpl!!.session).thenReturn(mockDebuggerSession)
    Mockito.`when`(mockDebugProcessImpl.project).thenReturn(myAndroidProjectRule.project)
    Mockito.`when`(mockDebugProcessImpl.searchScope).thenReturn(GlobalSearchScope.allScope(myAndroidProjectRule.project))
    Mockito.`when`(mockDebuggerSession!!.xDebugSession).thenReturn(mockXDebugSession)
    val mockXDebuggerManager = myAndroidProjectRule.mockProjectService(XDebuggerManager::class.java)
    val mockXDebugSession = Mockito.mock(XDebugSession::class.java)
    val mockXDebugProcess = Mockito.mock(XDebugProcess::class.java)
    val mockProcessHandler = Mockito.mock(
      ProcessHandler::class.java
    )
    Mockito.`when`(mockXDebuggerManager.currentSession).thenReturn(mockXDebugSession)
    Mockito.`when`(mockXDebugSession.debugProcess).thenReturn(mockXDebugProcess)
    Mockito.`when`(mockXDebugProcess.processHandler).thenReturn(mockProcessHandler)
    Mockito.`when`(mockProcessHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL))
      .thenAnswer { invocation: InvocationOnMock? -> targetDeviceAndroidVersion }
    myPositionManager = AndroidPositionManager(mockDebugProcessImpl)
  }

  @After
  fun tearDown() {
    if (myOriginalLocalPackages != null) {
      val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
      val sdkManager = sdkHandler.getSdkManager(StudioLoggerProgressIndicator(AndroidPositionManager::class.java))
      val packages = sdkManager.packages
      packages.setLocalPkgInfos(myOriginalLocalPackages!!)
    }
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
  @Throws(NoDataException::class)
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
    Truth.assertThat(file).isNotNull()
    val position = createSourcePositionForOneBasedLineNumber(file, 5)
    runTestDesugaringSupportWhenDesugaringIsRequired(position, false)
  }

  @Test
  @Throws(NoDataException::class)
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
    Truth.assertThat(file).isNotNull()
    val position = createSourcePositionForOneBasedLineNumber(file, 5)
    runTestDesugaringSupportWhenDesugaringIsRequired(position, false)
  }

  @Test
  @Throws(NoDataException::class)
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
    Truth.assertThat(file).isNotNull()
    val position = createSourcePositionForOneBasedLineNumber(file, 5)
    runTestDesugaringSupportWhenDesugaringIsRequired(position, true)
  }

  @Test
  @Throws(NoDataException::class)
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
    Truth.assertThat(file).isNotNull()
    val position = createSourcePositionForOneBasedLineNumber(file, 5)
    runTestDesugaringSupportWhenDesugaringIsRequired(position, true)
  }

  @Throws(NoDataException::class)
  private fun runTestDesugaringSupportWhenDesugaringIsRequired(position: SourcePosition, isDesugaringRequired: Boolean) {
    // Mock the VirtualMachine proxy to manage tested types.
    val vmProxy = Mockito.mock(VirtualMachineProxyImpl::class.java)
    Mockito.`when`(mockDebugProcessImpl!!.virtualMachineProxy).thenReturn(vmProxy)
    val typesMap = mockReferenceTypes(vmProxy, TOP_CLASS_NAME, INNER_CLASS_NAME, SYNTHESIZED_CLASS_NAME)

    // Mock the RequestManager for the class prepare requests.
    val mockRequestManager = Mockito.mock(RequestManagerImpl::class.java)
    Mockito.`when`(mockDebugProcessImpl.requestsManager).thenReturn(mockRequestManager)

    // Attach current project to the mocked debug process.
    Mockito.`when`(mockDebugProcessImpl.project).thenReturn(myAndroidProjectRule.project)

    // Mock locationsOfLine to reflect which class contains the source position.
    val topClass = typesMap[TOP_CLASS_NAME]
    val innerClassWithoutLocation = typesMap[INNER_CLASS_NAME]
    val desugarCompanionClass = typesMap[SYNTHESIZED_CLASS_NAME]
    val mockLocation = Mockito.mock(Location::class.java)
    if (isDesugaringRequired) {
      // If desugaring applies to an interface, its code moves to a synthesized class
      Mockito.`when`(myPositionManager!!.locationsOfLine(topClass!!, position)).thenReturn(emptyList())
      Mockito.`when`(myPositionManager!!.locationsOfLine(desugarCompanionClass!!, position)).thenReturn(listOf(mockLocation))
    } else {
      // If desugaring was not needed, the interface remains unchanged.
      Mockito.`when`(myPositionManager!!.locationsOfLine(topClass!!, position)).thenReturn(listOf(mockLocation))
      Mockito.`when`(myPositionManager!!.locationsOfLine(desugarCompanionClass!!, position)).thenReturn(emptyList())
    }
    // The existing inner class is not related to the source position.
    Mockito.`when`(myPositionManager!!.locationsOfLine(innerClassWithoutLocation!!, position)).thenReturn(emptyList())

    // Check that the list of types contains both the top class and the potential synthesized class.
    val typesWithPosition = myPositionManager!!.getAllClasses(position)
    Truth.assertThat(typesWithPosition).isNotNull()
    if (isDesugaringRequired) {
      // If desugaring may happen, both interface and its companion class should be returned.
      Truth.assertThat(typesWithPosition).hasSize(2)
      Truth.assertThat(typesWithPosition).containsExactly(topClass, desugarCompanionClass)
    } else {
      // Without desugaring, the interface is the only class that contains the source position.
      Truth.assertThat(typesWithPosition).hasSize(1)
      Truth.assertThat(typesWithPosition).containsExactly(topClass)
    }

    // Mock class prepare requests.
    val topClassPrepareRequest = Mockito.mock(ClassPrepareRequest::class.java, "CPR:" + TOP_CLASS_NAME)
    val allInnerClassesPrepareRequest = Mockito.mock(ClassPrepareRequest::class.java, "CPR:all inner classes")
    Mockito.`when`(mockRequestManager.createClassPrepareRequest(ArgumentMatchers.notNull(), ArgumentMatchers.eq(TOP_CLASS_NAME)))
      .thenReturn(topClassPrepareRequest)
    Mockito.`when`(mockRequestManager.createClassPrepareRequest(ArgumentMatchers.notNull(), ArgumentMatchers.eq(TOP_CLASS_NAME + "$*")))
      .thenReturn(allInnerClassesPrepareRequest)
    val mockRequestor = Mockito.mock(ClassPrepareRequestor::class.java)
    val classPrepareRequests = myPositionManager!!.createPrepareRequests(mockRequestor, position)
    Truth.assertThat(classPrepareRequests).isNotNull()
    if (isDesugaringRequired) {
      // If desugaring is required, we also create a class prepare request for all inner types of the interface so that we can find
      // the source position in the companion class (which is one of the inner classes).
      Truth.assertThat(classPrepareRequests).hasSize(2)
      Truth.assertThat(classPrepareRequests).containsExactly(topClassPrepareRequest, allInnerClassesPrepareRequest)
    } else {
      Truth.assertThat(classPrepareRequests).hasSize(1)
      Truth.assertThat(classPrepareRequests).containsExactly(topClassPrepareRequest)
    }
  }

  @Test
  fun testGetAcceptedFileTypes_acceptsJavaFiles() {
    val acceptedFileTypes = myPositionManager!!.acceptedFileTypes
    Truth.assertThat(acceptedFileTypes).hasSize(1)
    Truth.assertThat(acceptedFileTypes).containsExactly(JavaFileType.INSTANCE)
  }

  @get:Test
  val sourcePosition_nullLocation: Unit
    get() {
      UsefulTestCase.assertThrows(NoDataException::class.java) { myPositionManager!!.getSourcePosition(null) }
    }
  // Recreate myPositionManager, so that it reinitializes with a null version.

  // getSourcePosition should have exited before `location` was used.
  @get:Test
  val sourcePosition_androidVersionNotAvailable: Unit
    get() {
      val location = Mockito.mock(Location::class.java)
      targetDeviceAndroidVersion = null

      // Recreate myPositionManager, so that it reinitializes with a null version.
      myPositionManager = AndroidPositionManager(mockDebugProcessImpl)
      UsefulTestCase.assertThrows(NoDataException::class.java) { myPositionManager!!.getSourcePosition(location) }

      // getSourcePosition should have exited before `location` was used.
      Mockito.verifyNoInteractions(location)
    }

  // No declaring type results in `PositionManagerImpl.getPsiFileByLocation()` return a null PsiFile, so this tests a branch of
  // `AndroidPositionManager.getSourcePosition`.
  @get:Test
  val sourcePosition_locationHasNoDeclaringType: Unit
    get() {
      // No declaring type results in `PositionManagerImpl.getPsiFileByLocation()` return a null PsiFile, so this tests a branch of
      // `AndroidPositionManager.getSourcePosition`.
      val location = Mockito.mock(Location::class.java)
      Mockito.`when`(location.declaringType()).thenReturn(null)
      UsefulTestCase.assertThrows(NoDataException::class.java) { myPositionManager!!.getSourcePosition(location) }
    }
  // Ensure that the super class is actually finding this class.

  // Now that it's found, NoDataException should be thrown since it's not in the Android SDK.
  @get:Test
  val sourcePosition_locationIsNonAndroidFile: Unit
    get() {
      val type = Mockito.mock(ReferenceType::class.java)
      Mockito.`when`(type.name()).thenReturn(TOP_CLASS_NAME)
      val location = Mockito.mock(Location::class.java)
      Mockito.`when`(location.declaringType()).thenReturn(type)
      @Language("JAVA") val text = """package p1.p2;

class Foo {
  
  private void Bar() {
    int test = 2;
  }
}"""
      val file = myAndroidProjectRule.fixture.addFileToProject("src/p1/Foo.java", text)
      ApplicationManager.getApplication().runReadAction {

        // Ensure that the super class is actually finding this class.
        Truth.assertThat(myPositionManager!!.getPsiFileByLocation(myAndroidProjectRule.project, location)).isSameAs(file)

        // Now that it's found, NoDataException should be thrown since it's not in the Android SDK.
        UsefulTestCase.assertThrows(NoDataException::class.java) { myPositionManager!!.getSourcePosition(location) }
      }
    }

  @get:Throws(Exception::class)
  @get:Test
  val sourcePosition_targetSourcesAvailable: Unit
    get() {
      val location = androidSdkClassLocation
      val sourcePosition = ApplicationManager.getApplication()
        .runReadAction(ThrowableComputable<SourcePosition?, NoDataException?> { myPositionManager!!.getSourcePosition(location) })
      Truth.assertThat(sourcePosition).isNotNull()
      val file = sourcePosition!!.file
      Truth.assertThat(file.fileType).isEqualTo(JavaFileType.INSTANCE)
      Truth.assertThat(file.virtualFile.path).contains("/android-" + targetDeviceAndroidVersion!!.apiLevel + "/")
    }

  @get:Throws(Exception::class)
  @get:Test
  val sourcePosition_targetSourcesNotAvailable: Unit
    get() {
      removeLocalTargetSdkPackages()
      val location = androidSdkClassLocation
      val sourcePosition = ApplicationManager.getApplication()
        .runReadAction(ThrowableComputable<SourcePosition?, NoDataException?> { myPositionManager!!.getSourcePosition(location) })
      Truth.assertThat(sourcePosition).isNotNull()
      val file = sourcePosition!!.file
      Truth.assertThat(file.fileType).isEqualTo(JavaFileType.INSTANCE)
      Truth.assertThat(file.virtualFile.path).doesNotContain("/android-" + targetDeviceAndroidVersion!!.apiLevel + "/")
      Truth.assertThat(file.virtualFile).isInstanceOf(LightVirtualFile::class.java)
      val fileContent = (file.virtualFile as LightVirtualFile).content.toString()
      Truth.assertThat(fileContent).contains(
        "device under debug has API level " + targetDeviceAndroidVersion!!.apiLevel + "."
      )
      Truth.assertThat(file.name).isEqualTo("Unavailable Source")
      val requiredVersions = file.virtualFile.getUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY)!!
      Truth.assertThat(requiredVersions).containsExactly(targetDeviceAndroidVersion)
    }

  private fun removeLocalTargetSdkPackages() {
    val packagesToRemove: Set<String> = ImmutableSet.of(
      SdkConstants.FD_ANDROID_SOURCES + RepoPackage.PATH_SEPARATOR + "android-" + targetDeviceAndroidVersion!!.apiLevel,
      SdkConstants.FD_PLATFORMS + RepoPackage.PATH_SEPARATOR + "android-" + targetDeviceAndroidVersion!!.apiLevel
    )
    val sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler()
    val sdkManager = sdkHandler.getSdkManager(StudioLoggerProgressIndicator(AndroidPositionManager::class.java))
    val packages = sdkManager.packages
    val localPackages = packages.localPackages
    if (myOriginalLocalPackages == null) {
      // This won't get reset at the end of each test automatically. Store original list to restore it later.
      myOriginalLocalPackages = localPackages.values
    }
    val updatedPackages = localPackages.values.stream()
      .filter { localPackage: LocalPackage -> !packagesToRemove.contains(localPackage.path) }
      .collect(Collectors.toList())
    packages.setLocalPkgInfos(updatedPackages)
  }

  @get:Test
  val androidVersionFromDebugSession_nullSession: Unit
    get() {
      val mockXDebuggerManager = myAndroidProjectRule.mockProjectService(XDebuggerManager::class.java)
      Mockito.`when`(mockXDebuggerManager.currentSession).thenReturn(null)
      Truth.assertThat(AndroidPositionManager.getAndroidVersionFromDebugSession(myAndroidProjectRule.project)).isNull()
    }

  @get:Test
  val androidVersionFromDebugSession_nullAndroidVersion: Unit
    get() {
      targetDeviceAndroidVersion = null
      Truth.assertThat(AndroidPositionManager.getAndroidVersionFromDebugSession(myAndroidProjectRule.project)).isNull()
    }

  @get:Test
  val androidVersionFromDebugSession_androidVersionExists: Unit
    get() {
      targetDeviceAndroidVersion = AndroidVersion(32)
      Truth.assertThat(AndroidPositionManager.getAndroidVersionFromDebugSession(myAndroidProjectRule.project))
        .isSameAs(targetDeviceAndroidVersion)
    }

  @Test
  fun changeClassExtensionToJava_null() {
    Truth.assertThat(AndroidPositionManager.changeClassExtensionToJava(null)).isNull()
  }

  @Test
  fun changeClassExtensionToJava_notClassFile() {
    Truth.assertThat(AndroidPositionManager.changeClassExtensionToJava("foo.bar")).isEqualTo("foo.bar")
    Truth.assertThat(AndroidPositionManager.changeClassExtensionToJava("foo.java")).isEqualTo("foo.java")
  }

  @Test
  fun changeClassExtensionToJava_classFileChangedToJava() {
    Truth.assertThat(AndroidPositionManager.changeClassExtensionToJava("foo.class")).isEqualTo("foo.java")
  }

  // The case where the file is a java file is covered by above test cases; but the java class file case is not, due to difficulties in
  // mocking super class logic. Instead, we can test resolution here directly.
  @get:Test
  val relPathForJavaSource_fileIsJavaClass: Unit
    get() {
      // The case where the file is a java file is covered by above test cases; but the java class file case is not, due to difficulties in
      // mocking super class logic. Instead, we can test resolution here directly.
      val viewClass = ApplicationManager.getApplication().runReadAction(
        Computable {
          PositionManagerImpl.findClass(
            myAndroidProjectRule.project,
            "android.view.View",
            mockDebugProcessImpl!!.searchScope,
            true
          )
        })
      Truth.assertThat(viewClass).isNotNull()
      Truth.assertThat(AndroidPositionManager.getRelPathForJavaSource(myAndroidProjectRule.project, viewClass!!.containingFile)).isEqualTo(
        "android/view/View.java"
      )
    }

  @get:Test
  val relPathForJavaSource_unknownFileType: Unit
    get() {
      val file = Mockito.mock(PsiFile::class.java)
      Mockito.`when`(file.fileType).thenReturn(UnknownFileType.INSTANCE)
      Truth.assertThat(AndroidPositionManager.getRelPathForJavaSource(myAndroidProjectRule.project, file)).isNull()
    }

  @Test
  fun myXDebugSessionListener_sessionStopped() {
    val mockVirtualFile = Mockito.mock(VirtualFile::class.java)
    val mockFileEditorManager = Mockito.mock(FileEditorManager::class.java)
    val componentStack = ComponentStack(myAndroidProjectRule.project)
    componentStack.registerComponentInstance(FileEditorManager::class.java, mockFileEditorManager)
    val listener = MyXDebugSessionListener(mockVirtualFile, myAndroidProjectRule.project)
    listener.sessionStopped()
    Mockito.verify(mockFileEditorManager).closeFile(mockVirtualFile)
    componentStack.restore()
  }

  companion object {
    // The name of the top class or interface.
    private const val TOP_CLASS_NAME = "p1.p2.Foo"

    // The name of an inner class that does not contain any tested source position. It is used to make sure we do not incorrectly consider an
    // inner class that is not related to the breakpoint position that is set up.
    private const val INNER_CLASS_NAME = TOP_CLASS_NAME + "\$Inner"

    // The name of an inner class that would be the "companion" class to support desugaring. This is the class that will contain the actual
    // code at execution time. Therefore this is the type where the breakpoint position will be set up.
    // Note: the name of the synthesized class does not matter. But it has to be an inner class.
    private const val SYNTHESIZED_CLASS_NAME = TOP_CLASS_NAME + "\$DesugaringCompanion"
    private fun mockReferenceTypes(mockVmProxy: VirtualMachineProxyImpl, vararg typeNames: String): Map<String, ReferenceType> {
      val map: MutableMap<String, ReferenceType> = Maps.newHashMap()
      for (typeName in typeNames) {
        val type = Mockito.mock(ReferenceType::class.java, typeName)
        Mockito.`when`(type.name()).thenReturn(typeName)
        Mockito.`when`(mockVmProxy.classesByName(typeName)).thenReturn(listOf(type))
        map[typeName] = type
      }
      Mockito.`when`(mockVmProxy.allClasses()).thenReturn(ImmutableList.copyOf(map.values))
      return map
    }

    @get:Throws(Exception::class)
    private val androidSdkClassLocation: Location
      private get() {
        val type = Mockito.mock(ReferenceType::class.java)
        Mockito.`when`(type.name()).thenReturn("android.view.View")
        Mockito.`when`(type.sourceName()).thenReturn("View.java")
        val location = Mockito.mock(Location::class.java)
        Mockito.`when`(location.declaringType()).thenReturn(type)
        return location
      }
  }
}