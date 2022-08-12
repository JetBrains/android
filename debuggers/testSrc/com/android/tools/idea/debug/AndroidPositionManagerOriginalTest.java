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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.testing.Sdks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.request.ClassPrepareRequest;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

public class AndroidPositionManagerOriginalTest extends AndroidTestCase {

  // The name of the top class or interface.
  private static final String TOP_CLASS_NAME = "p1.p2.Foo";

  // The name of an inner class that does not contain any tested source position. It is used to make sure we do not incorrectly consider an
  // inner class that is not related to the breakpoint position that is set up.
  private static final String INNER_CLASS_NAME = TOP_CLASS_NAME + "$Inner";

  // The name of an inner class that would be the "companion" class to support desugaring. This is the class that will contain the actual
  // code at execution time. Therefore this is the type where the breakpoint position will be set up.
  // Note: the name of the synthesized class does not matter. But it has to be an inner class.
  private static final String SYNTHESIZED_CLASS_NAME = TOP_CLASS_NAME + "$DesugaringCompanion";

  private DebugProcess mockProcess;
  private AndroidPositionManagerOriginal myPositionManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockProcess = mock(DebugProcessImpl.class);
    myPositionManager = new AndroidPositionManagerOriginal((DebugProcessImpl)mockProcess);
  }

  public void testGetPsiByLocationWithNull() {
    assertNull(myPositionManager.getPsiFileByLocation(null, null));
  }

  public void testGetSourceForPsiFileWithNonAndroid() {
    Project project = getProject();

    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "class Foo {\n" +
                  "  \n" +
                  "  private void Bar() {\n" +
                  "    int test = 2;\n" +
                  "  }\n" +
                  "}";

    PsiFile file = myFixture.addFileToProject("src/p1/Foo.java", text);
    //Not an android sdk file so it returns null
    assertNull(myPositionManager.getApiSpecificPsi(project, file, new AndroidVersion(24, null)));
  }

  public void testGetSourceForPsiFileWithAndroidFile() {
    Project project = getProject();

    // need to add the sdk to JdkTable for getSourceFolder(version) to return the source folder
    ApplicationManager.getApplication().runWriteAction(() -> ProjectJdkTable.getInstance().addJdk(Sdks.createLatestAndroidSdk()));

    // get reference to the file through the class
    PsiClass cls = JavaPsiFacade.getInstance(project).findClass("android.view.View", GlobalSearchScope.allScope(project));
    PsiElement element = cls.getNavigationElement();
    PsiFile file = element.getContainingFile();

    // call the method and should get source
    checkSourceForApiVersion(project, file, 24);
    checkSourceForApiVersion(project, file, 25);
  }

  private void checkSourceForApiVersion(Project project, PsiFile file, int version) {
    PsiFile sourceFile = myPositionManager.getApiSpecificPsi(project, file, new AndroidVersion(version, null));
    assertNotNull(sourceFile);
    assertTrue(sourceFile.getVirtualFile().getPath().endsWith(String.format("sources/android-%1$d/android/view/View.java", version)));
  }

  /** Create a SourcePosition from a one-based line number. */
  private SourcePosition createSourcePositionForOneBasedLineNumber(PsiFile psiFile, int line) {
    assert line > 0;
    // SourcePositions are zero-based. Therefore, we need to adjust the line number accordingly.
    return SourcePosition.createFromLine(psiFile, line - 1);
  }


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

    PsiFile file = myFixture.addFileToProject("src/p1/p2/Foo.java", text);
    assertNotNull(file);
    SourcePosition position = createSourcePositionForOneBasedLineNumber(file, 5);
    runTestDesugaringSupportWhenDesugaringIsRequired(position, false);
  }

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

    PsiFile file = myFixture.addFileToProject("src/p1/p2/Foo.java", text);
    assertNotNull(file);
    SourcePosition position = createSourcePositionForOneBasedLineNumber(file, 5);
    runTestDesugaringSupportWhenDesugaringIsRequired(position, false);
  }

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

    PsiFile file = myFixture.addFileToProject("src/p1/p2/Foo.java", text);
    assertNotNull(file);
    SourcePosition position = createSourcePositionForOneBasedLineNumber(file, 5);
    runTestDesugaringSupportWhenDesugaringIsRequired(position, true);
  }

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

    PsiFile file = myFixture.addFileToProject("src/p1/p2/Foo.java", text);
    assertNotNull(file);
    SourcePosition position = createSourcePositionForOneBasedLineNumber(file, 5);
    runTestDesugaringSupportWhenDesugaringIsRequired(position, true);
  }

  public void testGetAcceptedFileTypes_acceptsJavaFiles() {
    Set<? extends FileType> acceptedFileTypes = myPositionManager.getAcceptedFileTypes();

    assertEquals(1, acceptedFileTypes.size());
    assertTrue(acceptedFileTypes.contains(JavaFileType.INSTANCE));
  }

  private void runTestDesugaringSupportWhenDesugaringIsRequired(@NotNull SourcePosition position, boolean isDesugaringRequired)
    throws NoDataException {
    // Mock the VirtualMachine proxy to manage tested types.
    VirtualMachineProxyImpl vmProxy = mock(VirtualMachineProxyImpl.class);
    when(mockProcess.getVirtualMachineProxy()).thenReturn(vmProxy);
    Map<String, ReferenceType> typesMap = mockReferenceTypes(vmProxy, TOP_CLASS_NAME, INNER_CLASS_NAME, SYNTHESIZED_CLASS_NAME);

    // Mock the RequestManager for the class prepare requests.
    RequestManagerImpl mockRequestManager = mock(RequestManagerImpl.class);
    when(mockProcess.getRequestsManager()).thenReturn(mockRequestManager);

    // Attach current project to the mocked debug process.
    when(mockProcess.getProject()).thenReturn(getProject());

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
    assertNotNull(typesWithPosition);
    if (isDesugaringRequired) {
      // If desugaring may happen, both interface and its companion class should be returned.
      assertEquals(2, typesWithPosition.size());
      assertTrue(typesWithPosition.contains(topClass));
      assertTrue(typesWithPosition.contains(desugarCompanionClass));
    }
    else {
      // Without desugaring, the interface is the only class that contains the source position.
      assertEquals(1, typesWithPosition.size());
      assertTrue(typesWithPosition.contains(topClass));
    }

    // Mock class prepare requests.
    ClassPrepareRequest topClassPrepareRequest = mock(ClassPrepareRequest.class, "CPR:" + TOP_CLASS_NAME);
    ClassPrepareRequest allInnerClassesPrepareRequest = mock(ClassPrepareRequest.class, "CPR:all inner classes");
    when(mockRequestManager.createClassPrepareRequest(notNull(), eq(TOP_CLASS_NAME))).thenReturn(topClassPrepareRequest);
    when(mockRequestManager.createClassPrepareRequest(notNull(), eq(TOP_CLASS_NAME + "$*"))).thenReturn(allInnerClassesPrepareRequest);
    ClassPrepareRequestor mockRequestor = mock(ClassPrepareRequestor.class);
    List<ClassPrepareRequest> classPrepareRequests = myPositionManager.createPrepareRequests(mockRequestor, position);
    assertNotNull(classPrepareRequests);
    if (isDesugaringRequired) {
      // If desugaring is required, we also create a class prepare request for all inner types of the interface so that we can find
      // the source position in the companion class (which is one of the inner classes).
      assertEquals(2, classPrepareRequests.size());
      assertTrue(classPrepareRequests.contains(topClassPrepareRequest));
      assertTrue(classPrepareRequests.contains(allInnerClassesPrepareRequest));
    }
    else {
      assertEquals(1, classPrepareRequests.size());
      assertTrue(classPrepareRequests.contains(topClassPrepareRequest));
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
}
