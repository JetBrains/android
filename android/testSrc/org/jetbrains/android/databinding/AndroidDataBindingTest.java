/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.jetbrains.android.databinding;

import com.android.SdkConstants;
import com.android.tools.idea.databinding.ModuleDataBinding;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import org.jetbrains.android.AndroidTestCase;

/**
 * Tests symbol resolution in data binding expressions in layout XML files. The code being tested is located in
 * {@link org.jetbrains.android.dom.converters.DataBindingConverter} and
 * {@link com.android.tools.idea.lang.databinding.DataBindingXmlReferenceContributor}.
 */
public class AndroidDataBindingTest extends AndroidTestCase {
  private static final String DUMMY_CLASS_QNAME = "p1.p2.DummyClass";

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myFixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML, SdkConstants.FN_ANDROID_MANIFEST_XML);
    myFixture.setTestDataPath(myFixture.getTestDataPath() + "/databinding");
    ModuleDataBinding.getInstance(myFacet).setEnabled(true);
  }

  private VirtualFile copyLayout(String name) {
    return myFixture.copyFileToProject("res/layout/" + name + ".xml");
  }

  private VirtualFile copyClass(String qName) {
    String asPath = qName.replace(".", "/");
    return myFixture.copyFileToProject("src/" + asPath + ".java");
  }

  private static void assertMethod(PsiClass aClass, String name, String returnType, String... parameters) {
    PsiMethod[] methods = aClass.findMethodsByName(name, true);
    assertEquals(1, methods.length);
    PsiMethod method = methods[0];
    assertNotNull(method.getReturnType());
    assertEquals(returnType, method.getReturnType().getCanonicalText());
    PsiParameterList parameterList = method.getParameterList();
    assertEquals(parameters.length, parameterList.getParametersCount());
    for (String parameterQName : parameters) {
      assertEquals(parameterQName, parameterList.getParameters()[0].getType().getCanonicalText());
    }
  }

  public void testSimpleVariableResolution() {
    copyLayout("basic_binding");
    copyClass(DUMMY_CLASS_QNAME);

    PsiClass aClass = myFixture.findClass("p1.p2.databinding.BasicBindingBinding");
    assertNotNull(aClass);
    assertNotNull(aClass.findFieldByName("view1", false));
    assertMethod(aClass, "setDummy", "void", DUMMY_CLASS_QNAME);
    assertMethod(aClass, "getDummy", DUMMY_CLASS_QNAME);
  }

  /**
   * Tests symbol resolution in the scenario described in https://issuetracker.google.com/65467760.
   */
  public void testPropertyResolution() {
    copyClass("p1.p2.ClassWithBindableProperty");
    myFixture.configureByFile("res/layout/data_binding_property_reference.xml");
    PsiElement element = myFixture.getElementAtCaret();
    assertTrue(element instanceof PsiMethod);
    assertEquals("getProperty", ((PsiMethod)element).getName());
  }

  public void testImportResolution() {
    copyLayout("import_variable");
    copyClass(DUMMY_CLASS_QNAME);

    PsiClass aClass = myFixture.findClass("p1.p2.databinding.ImportVariableBinding");
    assertNotNull(aClass);

    assertMethod(aClass, "setDummy", "void", DUMMY_CLASS_QNAME);
    assertMethod(aClass, "getDummy", DUMMY_CLASS_QNAME);

    assertMethod(aClass, "setDummyList", "void", "java.util.List<" + DUMMY_CLASS_QNAME + ">");
    assertMethod(aClass, "getDummyList", "java.util.List<" + DUMMY_CLASS_QNAME + ">");

    assertMethod(aClass, "setDummyMap", "void", "java.util.Map<java.lang.String," + DUMMY_CLASS_QNAME + ">");
    assertMethod(aClass, "getDummyMap", "java.util.Map<java.lang.String," + DUMMY_CLASS_QNAME + ">");

    assertMethod(aClass, "setDummyArray", "void", DUMMY_CLASS_QNAME + "[]");
    assertMethod(aClass, "getDummyArray", DUMMY_CLASS_QNAME + "[]");

    assertMethod(aClass, "setDummyMultiDimArray", "void", DUMMY_CLASS_QNAME + "[][][]");
    assertMethod(aClass, "getDummyMultiDimArray", DUMMY_CLASS_QNAME + "[][][]");
  }

  public void testImportAliasResolution() {
    copyLayout("import_via_alias");
    copyClass(DUMMY_CLASS_QNAME);

    PsiClass aClass = myFixture.findClass("p1.p2.databinding.ImportViaAliasBinding");
    assertNotNull(aClass);

    assertMethod(aClass, "setDummy", "void", DUMMY_CLASS_QNAME);
    assertMethod(aClass, "getDummy", DUMMY_CLASS_QNAME);

    assertMethod(aClass, "setDummyList", "void", "java.util.List<" + DUMMY_CLASS_QNAME + ">");
    assertMethod(aClass, "getDummyList", "java.util.List<" + DUMMY_CLASS_QNAME + ">");

    assertMethod(aClass, "setDummyMap", "void", "java.util.Map<java.lang.String," + DUMMY_CLASS_QNAME + ">");
    assertMethod(aClass, "getDummyMap", "java.util.Map<java.lang.String," + DUMMY_CLASS_QNAME + ">");

    assertMethod(aClass, "setDummyMap2", "void", "java.util.Map<" + DUMMY_CLASS_QNAME + ",java.lang.String>");
    assertMethod(aClass, "getDummyMap2", "java.util.Map<" + DUMMY_CLASS_QNAME + ",java.lang.String>");

    assertMethod(aClass, "setDummyArray", "void", DUMMY_CLASS_QNAME + "[]");
    assertMethod(aClass, "getDummyArray", DUMMY_CLASS_QNAME + "[]");

    assertMethod(aClass, "setDummyMultiDimArray", "void", DUMMY_CLASS_QNAME + "[][][]");
    assertMethod(aClass, "getDummyMultiDimArray", DUMMY_CLASS_QNAME + "[][][]");
  }
}

