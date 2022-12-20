/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.tools.idea.databinding.module.LayoutBindingModuleCache;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.AndroidTestUtils;
import com.google.common.collect.Lists;
import com.intellij.facet.FacetManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameterList;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import java.util.List;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests symbol resolution in data binding expressions in layout XML files. The code being tested is located in
 * {@link org.jetbrains.android.dom.converters.DataBindingConverter} and
 * {@link com.android.tools.idea.lang.databinding.DataBindingXmlReferenceContributor}.
 */
@RunWith(Parameterized.class)
public class AndroidDataBindingTest {
  private static final String SAMPLE_CLASS_QNAME = "p1.p2.SampleClass";

  @NotNull
  @Rule
  public final AndroidProjectRule myProjectRule = AndroidProjectRule.withSdk().initAndroid(true);

  @Rule
  public final EdtRule myEdtRule = new EdtRule();

  @NotNull
  private final DataBindingMode myDataBindingMode;

  @Parameters(name = "{0}")
  public static List<DataBindingMode> getModes() {
    return Lists.newArrayList(DataBindingMode.SUPPORT, DataBindingMode.ANDROIDX);
  }

  public AndroidDataBindingTest(@NotNull DataBindingMode mode) {
    myDataBindingMode = mode;
  }

  @Before
  public void setUp() {
    JavaCodeInsightTestFixture fixture = getFixture();

    fixture.setTestDataPath(TestDataPaths.TEST_DATA_ROOT + "/databinding");
    fixture.copyFileToProject(SdkConstants.FN_ANDROID_MANIFEST_XML);

    AndroidFacet androidFacet = FacetManager.getInstance(myProjectRule.getModule()).getFacetByType(AndroidFacet.ID);
    LayoutBindingModuleCache.getInstance(androidFacet).setDataBindingMode(myDataBindingMode);
  }

  /**
   * Expose the underlying project rule fixture directly.
   *
   * We know that the underlying fixture is a {@link JavaCodeInsightTestFixture} because our
   * {@link AndroidProjectRule} is initialized to use the disk.
   *
   * In some cases, using the specific subclass provides us with additional methods we can
   * use to inspect the state of our parsed files. In other cases, it's just fewer characters
   * to type.
   */
  private JavaCodeInsightTestFixture getFixture() {
    return ((JavaCodeInsightTestFixture)myProjectRule.getFixture());
  }

  private void copyLayout(String name) {
    getFixture().copyFileToProject("res/layout/" + name + ".xml");
  }

  private void copyClass(String qName) {
    String asPath = qName.replace(".", "/");
    getFixture().copyFileToProject("src/" + asPath + ".java");
  }

  private void copyClass(String qName, String targetQName) {
    String source = qName.replace(".", "/");
    String dest = targetQName.replace(".", "/");
    getFixture().copyFileToProject("src/" + source + ".java", "src/" + dest + ".java");
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

  @Test
  @RunsInEdt
  public void testSimpleVariableResolution() {
    copyLayout("basic_binding");
    copyClass(SAMPLE_CLASS_QNAME);

    PsiClass context = getFixture().findClass(SAMPLE_CLASS_QNAME);
    PsiClass aClass = AndroidTestUtils.findClass(getFixture(), "p1.p2.databinding.BasicBindingBinding", context);
    assertNotNull(aClass);

    assertNotNull(aClass.findFieldByName("view1", false));
    assertMethod(aClass, "setSample", "void", SAMPLE_CLASS_QNAME);
    assertMethod(aClass, "getSample", SAMPLE_CLASS_QNAME);
  }

  /**
   * Tests symbol resolution in the scenario described in https://issuetracker.google.com/65467760.
   */
  @Test
  @RunsInEdt
  public void testPropertyResolution() {
    if (myDataBindingMode == DataBindingMode.SUPPORT) {
      copyClass("p1.p2.ClassWithBindableProperty");
    } else {
      copyClass("p1.p2.ClassWithBindableProperty_androidx", "p1.p2.ClassWithBindableProperty");
    }
    getFixture().configureByFile("res/layout/data_binding_property_reference.xml");

    PsiElement element = getFixture().getElementAtCaret();
    assertTrue(element instanceof PsiMethod);
    assertEquals("getProperty", ((PsiMethod)element).getName());
  }

  @Test
  @RunsInEdt
  public void testImportResolution() {
    copyLayout("import_variable");
    copyClass(SAMPLE_CLASS_QNAME);

    PsiClass context = getFixture().findClass(SAMPLE_CLASS_QNAME);
    PsiClass aClass = AndroidTestUtils.findClass(getFixture(), "p1.p2.databinding.ImportVariableBinding", context);
    assertNotNull(aClass);

    assertMethod(aClass, "setSample", "void", SAMPLE_CLASS_QNAME);
    assertMethod(aClass, "getSample", SAMPLE_CLASS_QNAME);

    assertMethod(aClass, "setSampleList", "void", "java.util.List<" + SAMPLE_CLASS_QNAME + ">");
    assertMethod(aClass, "getSampleList", "java.util.List<" + SAMPLE_CLASS_QNAME + ">");

    assertMethod(aClass, "setSampleMap", "void", "java.util.Map<java.lang.String," + SAMPLE_CLASS_QNAME + ">");
    assertMethod(aClass, "getSampleMap", "java.util.Map<java.lang.String," + SAMPLE_CLASS_QNAME + ">");

    assertMethod(aClass, "setSampleArray", "void", SAMPLE_CLASS_QNAME + "[]");
    assertMethod(aClass, "getSampleArray", SAMPLE_CLASS_QNAME + "[]");

    assertMethod(aClass, "setSampleMultiDimArray", "void", SAMPLE_CLASS_QNAME + "[][][]");
    assertMethod(aClass, "getSampleMultiDimArray", SAMPLE_CLASS_QNAME + "[][][]");
  }

  @Test
  @RunsInEdt
  public void testImportAliasResolution() {
    copyLayout("import_via_alias");
    copyClass(SAMPLE_CLASS_QNAME);

    PsiClass context = getFixture().findClass(SAMPLE_CLASS_QNAME);
    PsiClass aClass = AndroidTestUtils.findClass(getFixture(), "p1.p2.databinding.ImportViaAliasBinding", context);
    assertNotNull(aClass);

    assertMethod(aClass, "setSample", "void", SAMPLE_CLASS_QNAME);
    assertMethod(aClass, "getSample", SAMPLE_CLASS_QNAME);

    assertMethod(aClass, "setSampleList", "void", "java.util.List<" + SAMPLE_CLASS_QNAME + ">");
    assertMethod(aClass, "getSampleList", "java.util.List<" + SAMPLE_CLASS_QNAME + ">");

    assertMethod(aClass, "setSampleMap", "void", "java.util.Map<java.lang.String," + SAMPLE_CLASS_QNAME + ">");
    assertMethod(aClass, "getSampleMap", "java.util.Map<java.lang.String," + SAMPLE_CLASS_QNAME + ">");

    assertMethod(aClass, "setSampleMap2", "void", "java.util.Map<" + SAMPLE_CLASS_QNAME + ",java.lang.String>");
    assertMethod(aClass, "getSampleMap2", "java.util.Map<" + SAMPLE_CLASS_QNAME + ",java.lang.String>");

    assertMethod(aClass, "setSampleArray", "void", SAMPLE_CLASS_QNAME + "[]");
    assertMethod(aClass, "getSampleArray", SAMPLE_CLASS_QNAME + "[]");

    assertMethod(aClass, "setSampleMultiDimArray", "void", SAMPLE_CLASS_QNAME + "[][][]");
    assertMethod(aClass, "getSampleMultiDimArray", SAMPLE_CLASS_QNAME + "[][][]");
  }

  @Test
  @RunsInEdt
  public void testDataBindingComponentContainingFileIsNotNull() {
    copyClass(SAMPLE_CLASS_QNAME);

    PsiClass context = getFixture().findClass(SAMPLE_CLASS_QNAME);

    String dataBindingPrefix = (myDataBindingMode == DataBindingMode.SUPPORT) ? "android.databinding." : "androidx.databinding.";
    PsiClass foundClass = AndroidTestUtils.findClass(getFixture(), dataBindingPrefix + "DataBindingComponent", context);
    assertNotNull(foundClass);
    assertNotNull(foundClass.getContainingFile());
  }
}
