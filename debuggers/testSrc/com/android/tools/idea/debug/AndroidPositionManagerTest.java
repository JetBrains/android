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
package com.android.tools.idea.debug;

import static com.google.common.truth.Truth.assertThat;
import static com.intellij.testFramework.UsefulTestCase.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.RepoManager;
import com.android.repository.api.RepoPackage;
import com.android.repository.impl.meta.RepositoryPackages;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.debug.AndroidPositionManager.MyXDebugSessionListener;
import com.android.tools.idea.editors.AttachAndroidSdkSourcesNotificationProvider;
import com.android.tools.idea.progress.StudioLoggerProgressIndicator;
import com.android.tools.idea.run.AndroidSessionInfo;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.PositionManagerImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.ComponentStack;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AndroidPositionManagerTest {
  @Rule public final AndroidProjectRule myAndroidProjectRule = AndroidProjectRule.withSdk();
  @Rule public final MockitoRule myMockitoRule = MockitoJUnit.rule();

  // The name of the top class or interface.
  private static final String TOP_CLASS_NAME = "p1.p2.Foo";

  // The name of an inner class that does not contain any tested source position. It is used to make sure we do not incorrectly consider an
  // inner class that is not related to the breakpoint position that is set up.
  private static final String INNER_CLASS_NAME = TOP_CLASS_NAME + "$Inner";

  // The name of an inner class that would be the "companion" class to support desugaring. This is the class that will contain the actual
  // code at execution time. Therefore this is the type where the breakpoint position will be set up.
  // Note: the name of the synthesized class does not matter. But it has to be an inner class.
  private static final String SYNTHESIZED_CLASS_NAME = TOP_CLASS_NAME + "$DesugaringCompanion";

  @Mock private DebugProcessImpl mockDebugProcessImpl;
  @Mock private DebuggerSession mockDebuggerSession;
  @Mock private XDebugSession mockXDebugSession;

  private AndroidVersion targetDeviceAndroidVersion = new AndroidVersion(30);

  private AndroidPositionManager myPositionManager;

  private Collection<LocalPackage> myOriginalLocalPackages;

  @Before
  public void setUp() {
    when(mockDebugProcessImpl.getSession()).thenReturn(mockDebuggerSession);
    when(mockDebugProcessImpl.getProject()).thenReturn(myAndroidProjectRule.getProject());
    when(mockDebugProcessImpl.getSearchScope()).thenReturn(GlobalSearchScope.allScope(myAndroidProjectRule.getProject()));
    when(mockDebuggerSession.getXDebugSession()).thenReturn(mockXDebugSession);

    XDebuggerManager mockXDebuggerManager = myAndroidProjectRule.mockProjectService(XDebuggerManager.class);
    XDebugSession mockXDebugSession = mock(XDebugSession.class);
    XDebugProcess mockXDebugProcess = mock(XDebugProcess.class);
    ProcessHandler mockProcessHandler = mock(ProcessHandler.class);

    when(mockXDebuggerManager.getCurrentSession()).thenReturn(mockXDebugSession);
    when(mockXDebugSession.getDebugProcess()).thenReturn(mockXDebugProcess);
    when(mockXDebugProcess.getProcessHandler()).thenReturn(mockProcessHandler);
    when(mockProcessHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).thenAnswer(invocation -> targetDeviceAndroidVersion);

    myPositionManager = new AndroidPositionManager(mockDebugProcessImpl);
  }

  @After
  public void tearDown() {
    if (myOriginalLocalPackages != null) {
      AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
      RepoManager sdkManager = sdkHandler.getSdkManager(new StudioLoggerProgressIndicator(AndroidPositionManager.class));
      RepositoryPackages packages = sdkManager.getPackages();
      packages.setLocalPkgInfos(myOriginalLocalPackages);
    }
  }

  /**
   * Create a SourcePosition from a one-based line number.
   */
  private SourcePosition createSourcePositionForOneBasedLineNumber(PsiFile psiFile, int line) {
    assert line > 0;
    // SourcePositions are zero-based. Therefore, we need to adjust the line number accordingly.
    return SourcePosition.createFromLine(psiFile, line - 1);
  }

  @Test
  public void testDesugaringSupport_SimpleClass() throws NoDataException {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "class Foo {\n" +
                  "  public void bar() {\n" +
                  "    int test = 2;\n" +
                  "  }\n" +
                  "\n" +
                  "  class Inner {\n" +
                  "    static void doSomething() {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    PsiFile file = myAndroidProjectRule.getFixture().addFileToProject("src/p1/p2/Foo.java", text);
    assertThat(file).isNotNull();
    SourcePosition position = createSourcePositionForOneBasedLineNumber(file, 5);
    runTestDesugaringSupportWhenDesugaringIsRequired(position, false);
  }

  @Test
  public void testDesugaringSupport_InterfaceWithStaticInitializer() throws NoDataException {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "interface Foo {\n" +
                  "  public static final String STR = new String()\n" +
                  "    .concat(\"foo\");\n" +
                  "\n" +
                  "  class Inner {\n" +
                  "    static void doSomething() {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    PsiFile file = myAndroidProjectRule.getFixture().addFileToProject("src/p1/p2/Foo.java", text);
    assertThat(file).isNotNull();
    SourcePosition position = createSourcePositionForOneBasedLineNumber(file, 5);
    runTestDesugaringSupportWhenDesugaringIsRequired(position, false);
  }

  @Test
  public void testDesugaringSupport_InterfaceWithDefaultMethod() throws NoDataException {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "interface Foo {\n" +
                  "  default void bar() {\n" +
                  "    int test = 2;\n" +
                  "  }\n" +
                  "\n" +
                  "  class Inner {\n" +
                  "    static void doSomething() {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    PsiFile file = myAndroidProjectRule.getFixture().addFileToProject("src/p1/p2/Foo.java", text);
    assertThat(file).isNotNull();
    SourcePosition position = createSourcePositionForOneBasedLineNumber(file, 5);
    runTestDesugaringSupportWhenDesugaringIsRequired(position, true);
  }

  @Test
  public void testDesugaringSupport_InterfaceWithStaticMethod() throws NoDataException {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "interface Foo {\n" +
                  "  static void bar() {\n" +
                  "    int test = 2;\n" +
                  "  }\n" +
                  "\n" +
                  "  class Inner {\n" +
                  "    static void doSomething() {\n" +
                  "    }\n" +
                  "  }\n" +
                  "}";

    PsiFile file = myAndroidProjectRule.getFixture().addFileToProject("src/p1/p2/Foo.java", text);
    assertThat(file).isNotNull();
    SourcePosition position = createSourcePositionForOneBasedLineNumber(file, 5);
    runTestDesugaringSupportWhenDesugaringIsRequired(position, true);
  }

  private void runTestDesugaringSupportWhenDesugaringIsRequired(@NotNull SourcePosition position, boolean isDesugaringRequired)
    throws NoDataException {
    // Mock the VirtualMachine proxy to manage tested types.
    VirtualMachineProxyImpl vmProxy = mock(VirtualMachineProxyImpl.class);
    when(mockDebugProcessImpl.getVirtualMachineProxy()).thenReturn(vmProxy);
    Map<String, ReferenceType> typesMap = mockReferenceTypes(vmProxy, TOP_CLASS_NAME, INNER_CLASS_NAME, SYNTHESIZED_CLASS_NAME);

    // Mock the RequestManager for the class prepare requests.
    RequestManagerImpl mockRequestManager = mock(RequestManagerImpl.class);
    when(mockDebugProcessImpl.getRequestsManager()).thenReturn(mockRequestManager);

    // Attach current project to the mocked debug process.
    when(mockDebugProcessImpl.getProject()).thenReturn(myAndroidProjectRule.getProject());

    // Mock locationsOfLine to reflect which class contains the source position.
    ReferenceType topClass = typesMap.get(TOP_CLASS_NAME);
    ReferenceType innerClassWithoutLocation = typesMap.get(INNER_CLASS_NAME);
    ReferenceType desugarCompanionClass = typesMap.get(SYNTHESIZED_CLASS_NAME);
    Location mockLocation = mock(Location.class);
    if (isDesugaringRequired) {
      // If desugaring applies to an interface, its code moves to a synthesized class
      when(myPositionManager.locationsOfLine(topClass, position)).thenReturn(Collections.emptyList());
      when(myPositionManager.locationsOfLine(desugarCompanionClass, position)).thenReturn(Collections.singletonList(mockLocation));
    }
    else {
      // If desugaring was not needed, the interface remains unchanged.
      when(myPositionManager.locationsOfLine(topClass, position)).thenReturn(Collections.singletonList(mockLocation));
      when(myPositionManager.locationsOfLine(desugarCompanionClass, position)).thenReturn(Collections.emptyList());
    }
    // The existing inner class is not related to the source position.
    when(myPositionManager.locationsOfLine(innerClassWithoutLocation, position)).thenReturn(Collections.emptyList());

    // Check that the list of types contains both the top class and the potential synthesized class.
    List<ReferenceType> typesWithPosition = myPositionManager.getAllClasses(position);
    assertThat(typesWithPosition).isNotNull();
    if (isDesugaringRequired) {
      // If desugaring may happen, both interface and its companion class should be returned.
      assertThat(typesWithPosition).hasSize(2);
      assertThat(typesWithPosition).containsExactly(topClass, desugarCompanionClass);
    }
    else {
      // Without desugaring, the interface is the only class that contains the source position.
      assertThat(typesWithPosition).hasSize(1);
      assertThat(typesWithPosition).containsExactly(topClass);
    }

    // Mock class prepare requests.
    ClassPrepareRequest topClassPrepareRequest = mock(ClassPrepareRequest.class, "CPR:" + TOP_CLASS_NAME);
    ClassPrepareRequest allInnerClassesPrepareRequest = mock(ClassPrepareRequest.class, "CPR:all inner classes");
    when(mockRequestManager.createClassPrepareRequest(notNull(), eq(TOP_CLASS_NAME))).thenReturn(topClassPrepareRequest);
    when(mockRequestManager.createClassPrepareRequest(notNull(), eq(TOP_CLASS_NAME + "$*"))).thenReturn(allInnerClassesPrepareRequest);
    ClassPrepareRequestor mockRequestor = mock(ClassPrepareRequestor.class);
    List<ClassPrepareRequest> classPrepareRequests = myPositionManager.createPrepareRequests(mockRequestor, position);
    assertThat(classPrepareRequests).isNotNull();
    if (isDesugaringRequired) {
      // If desugaring is required, we also create a class prepare request for all inner types of the interface so that we can find
      // the source position in the companion class (which is one of the inner classes).
      assertThat(classPrepareRequests).hasSize(2);
      assertThat(classPrepareRequests).containsExactly(topClassPrepareRequest, allInnerClassesPrepareRequest);
    }
    else {
      assertThat(classPrepareRequests).hasSize(1);
      assertThat(classPrepareRequests).containsExactly(topClassPrepareRequest);
    }
  }

  private static Map<String, ReferenceType> mockReferenceTypes(VirtualMachineProxyImpl mockVmProxy, String... typeNames) {
    Map<String, ReferenceType> map = Maps.newHashMap();
    for (String typeName : typeNames) {
      ReferenceType type = mock(ReferenceType.class, typeName);
      when(type.name()).thenReturn(typeName);
      when(mockVmProxy.classesByName(typeName)).thenReturn(Collections.singletonList(type));
      map.put(typeName, type);
    }
    when(mockVmProxy.allClasses()).thenReturn(ImmutableList.copyOf(map.values()));
    return map;
  }

  @Test
  public void testGetAcceptedFileTypes_acceptsJavaFiles() {
    Set<? extends FileType> acceptedFileTypes = myPositionManager.getAcceptedFileTypes();

    assertThat(acceptedFileTypes).hasSize(1);
    assertThat(acceptedFileTypes).containsExactly(JavaFileType.INSTANCE);
  }

  @Test
  public void getSourcePosition_nullLocation() {
    assertThrows(NoDataException.class, () -> myPositionManager.getSourcePosition(null));
  }

  @Test
  public void getSourcePosition_androidVersionNotAvailable() {
    Location location = mock(Location.class);
    targetDeviceAndroidVersion = null;

    assertThrows(NoDataException.class, () -> myPositionManager.getSourcePosition(location));

    // getSourcePosition should have exited before `location` was used.
    verifyNoInteractions(location);
  }

  @Test
  public void getSourcePosition_locationHasNoDeclaringType() {
    // No declaring type results in `PositionManagerImpl.getPsiFileByLocation()` return a null PsiFile, so this tests a branch of
    // `AndroidPositionManager.getSourcePosition`.
    Location location = mock(Location.class);
    when(location.declaringType()).thenReturn(null);

    assertThrows(NoDataException.class, () -> myPositionManager.getSourcePosition(location));
  }

  @Test
  public void getSourcePosition_locationIsNonAndroidFile() {
    ReferenceType type = mock(ReferenceType.class);
    when(type.name()).thenReturn(TOP_CLASS_NAME);

    Location location = mock(Location.class);
    when(location.declaringType()).thenReturn(type);

    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "class Foo {\n" +
                  "  \n" +
                  "  private void Bar() {\n" +
                  "    int test = 2;\n" +
                  "  }\n" +
                  "}";

    PsiFile file = myAndroidProjectRule.getFixture().addFileToProject("src/p1/Foo.java", text);

    ApplicationManager.getApplication().runReadAction(
      () -> {
        // Ensure that the super class is actually finding this class.
        assertThat(myPositionManager.getPsiFileByLocation(myAndroidProjectRule.getProject(), location)).isSameAs(file);

        // Now that it's found, NoDataException should be thrown since it's not in the Android SDK.
        assertThrows(NoDataException.class, () -> myPositionManager.getSourcePosition(location));
      });
  }

  @Test
  public void getSourcePosition_targetSourcesAvailable() throws Exception {
    Location location = getAndroidSdkClassLocation();

    SourcePosition sourcePosition = ApplicationManager.getApplication()
      .runReadAction((ThrowableComputable<SourcePosition, NoDataException>)() -> myPositionManager.getSourcePosition(location));

    assertThat(sourcePosition).isNotNull();

    PsiFile file = sourcePosition.getFile();
    assertThat(file.getFileType()).isEqualTo(JavaFileType.INSTANCE);
    assertThat(file.getVirtualFile().getPath()).contains("/android-" + targetDeviceAndroidVersion.getApiLevel() + "/");
  }

  @Test
  public void getSourcePosition_targetSourcesNotAvailable() throws Exception {
    removeLocalTargetSdkPackages();

    Location location = getAndroidSdkClassLocation();

    SourcePosition sourcePosition = ApplicationManager.getApplication()
      .runReadAction((ThrowableComputable<SourcePosition, NoDataException>)() -> myPositionManager.getSourcePosition(location));

    assertThat(sourcePosition).isNotNull();

    PsiFile file = sourcePosition.getFile();
    assertThat(file.getFileType()).isEqualTo(JavaFileType.INSTANCE);
    assertThat(file.getVirtualFile().getPath()).doesNotContain("/android-" + targetDeviceAndroidVersion.getApiLevel() + "/");
    assertThat(file.getVirtualFile()).isInstanceOf(LightVirtualFile.class);
    String fileContent = ((LightVirtualFile)file.getVirtualFile()).getContent().toString();
    assertThat(fileContent).contains(
      "device under debug has API level " + targetDeviceAndroidVersion.getApiLevel() + ".");
    assertThat(file.getName()).isEqualTo("Unavailable Source");

    List<AndroidVersion> requiredVersions =
      file.getVirtualFile().getUserData(AttachAndroidSdkSourcesNotificationProvider.REQUIRED_SOURCES_KEY);
    assertThat(requiredVersions).containsExactly(targetDeviceAndroidVersion);
  }

  private static Location getAndroidSdkClassLocation() throws Exception {
    ReferenceType type = mock(ReferenceType.class);
    when(type.name()).thenReturn("android.view.View");
    when(type.sourceName()).thenReturn("View.java");

    Location location = mock(Location.class);
    when(location.declaringType()).thenReturn(type);

    return location;
  }

  private void removeLocalTargetSdkPackages() {
    Set<String> packagesToRemove = ImmutableSet.of(
      SdkConstants.FD_ANDROID_SOURCES + RepoPackage.PATH_SEPARATOR + "android-" + targetDeviceAndroidVersion.getApiLevel(),
      SdkConstants.FD_PLATFORMS + RepoPackage.PATH_SEPARATOR + "android-" + targetDeviceAndroidVersion.getApiLevel()
    );

    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    RepoManager sdkManager = sdkHandler.getSdkManager(new StudioLoggerProgressIndicator(AndroidPositionManager.class));
    RepositoryPackages packages = sdkManager.getPackages();

    Map<String, LocalPackage> localPackages = packages.getLocalPackages();

    if (myOriginalLocalPackages == null) {
      // This won't get reset at the end of each test automatically. Store original list to restore it later.
      myOriginalLocalPackages = localPackages.values();
    }

    List<LocalPackage> updatedPackages = localPackages.values().stream()
      .filter(localPackage -> !packagesToRemove.contains(localPackage.getPath()))
      .collect(Collectors.toList());

    packages.setLocalPkgInfos(updatedPackages);
  }

  @Test
  public void getAndroidVersionFromDebugSession_nullSession() {
    XDebuggerManager mockXDebuggerManager = myAndroidProjectRule.mockProjectService(XDebuggerManager.class);

    when(mockXDebuggerManager.getCurrentSession()).thenReturn(null);

    assertThat(AndroidPositionManager.getAndroidVersionFromDebugSession(myAndroidProjectRule.getProject())).isNull();
  }

  @Test
  public void getAndroidVersionFromDebugSession_nullAndroidVersion() {
    targetDeviceAndroidVersion = null;

    assertThat(AndroidPositionManager.getAndroidVersionFromDebugSession(myAndroidProjectRule.getProject())).isNull();
  }

  @Test
  public void getAndroidVersionFromDebugSession_androidVersionExists() {
    targetDeviceAndroidVersion = new AndroidVersion(32);

    assertThat(AndroidPositionManager.getAndroidVersionFromDebugSession(myAndroidProjectRule.getProject()))
      .isSameAs(targetDeviceAndroidVersion);
  }

  @Test
  public void changeClassExtensionToJava_null() {
    assertThat(AndroidPositionManager.changeClassExtensionToJava(null)).isNull();
  }

  @Test
  public void changeClassExtensionToJava_notClassFile() {
    assertThat(AndroidPositionManager.changeClassExtensionToJava("foo.bar")).isEqualTo("foo.bar");
    assertThat(AndroidPositionManager.changeClassExtensionToJava("foo.java")).isEqualTo("foo.java");
  }

  @Test
  public void changeClassExtensionToJava_classFileChangedToJava() {
    assertThat(AndroidPositionManager.changeClassExtensionToJava("foo.class")).isEqualTo("foo.java");
  }

  @Test
  public void getRelPathForJavaSource_fileIsJavaClass() {
    // The case where the file is a java file is covered by above test cases; but the java class file case is not, due to difficulties in
    // mocking super class logic. Instead, we can test resolution here directly.
    PsiClass viewClass = ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)() ->
      PositionManagerImpl.findClass(
        myAndroidProjectRule.getProject(),
        "android.view.View",
        mockDebugProcessImpl.getSearchScope(),
        true));
    assertThat(viewClass).isNotNull();

    assertThat(myPositionManager.getRelPathForJavaSource(myAndroidProjectRule.getProject(), viewClass.getContainingFile())).isEqualTo(
      "android/view/View.java");
  }

  @Test
  public void getRelPathForJavaSource_unknownFileType() {
    PsiFile file = mock(PsiFile.class);
    when(file.getFileType()).thenReturn(UnknownFileType.INSTANCE);

    assertThat(myPositionManager.getRelPathForJavaSource(myAndroidProjectRule.getProject(), file)).isNull();
  }

  @Test
  public void myXDebugSessionListener_sessionStopped() {
    VirtualFile mockVirtualFile = mock(VirtualFile.class);

    FileEditorManager mockFileEditorManager = mock(FileEditorManager.class);
    ComponentStack componentStack = new ComponentStack(myAndroidProjectRule.getProject());
    componentStack.registerComponentInstance(FileEditorManager.class, mockFileEditorManager);

    MyXDebugSessionListener listener = new MyXDebugSessionListener(mockVirtualFile, myAndroidProjectRule.getProject());
    listener.sessionStopped();

    verify(mockFileEditorManager).closeFile(mockVirtualFile);

    componentStack.restore();
  }
}
