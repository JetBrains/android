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
package com.android.tools.idea.debug;

import com.android.SdkConstants;
import com.android.tools.lint.detector.api.ResourceEvaluator;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

import java.util.Map;

public class AndroidResolveHelperTest extends AndroidTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testColorInt() {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "public class Foo {\n" +
                  "    @android.support.annotation.ColorInt int mColor;\n" +
                  "    \n" +
                  "    public void setColor(@android.support.annotation.ColorInt int c) {\n" +
                  "        mColor = <caret>c;\n" +
                  "    }\n" +
                  "}\n";

    PsiElement element = getPsiElement(text);
    assertNotNull(element);

    PsiAnnotation annotation = AndroidResolveHelper.getAnnotationForLocal(element, "c");
    assertNotNull(annotation);
    assertEquals(ResourceEvaluator.COLOR_INT_ANNOTATION, annotation.getQualifiedName());

    annotation = AndroidResolveHelper.getAnnotationForField(element, "p1.p2.Foo", "mColor");
    assertNotNull(annotation);
    assertEquals(ResourceEvaluator.COLOR_INT_ANNOTATION, annotation.getQualifiedName());
  }

  public void testIntDefResolution() {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "import java.lang.annotation.Retention;\n" +
                  "import java.lang.annotation.RetentionPolicy;\n" +
                  "\n" +
                  "public class Foo {\n" +
                  "    public static final int INVISIBLE = 0x00000004;\n" +
                  "    public static final int VISIBLE = 0x00000000;\n" +
                  "    public static final int GONE = 0x00000008;\n" +
                  "\n" +
                  "    @android.support.annotation.IntDef({VISIBLE, INVISIBLE, GONE})\n" +
                  "    @Retention(RetentionPolicy.SOURCE)\n" +
                  "    public @interface Visibility {}\n" +
                  "\n" +
                  "    @Foo.Visibility\n" +
                  "    private int mVisibility;  \n" +
                  "\n" +
                  "    public void setVisibility(@Foo.Visibility int v) {\n" +
                  "        mVis<caret>ibility = v;\n" +
                  "    }\n" +
                  "}\n";


    PsiElement element = getPsiElement(text);
    assertNotNull(element);

    PsiAnnotation annotation = AndroidResolveHelper.getAnnotationForLocal(element, "v");
    assertNotNull(annotation);
    assertEquals(SdkConstants.INT_DEF_ANNOTATION, annotation.getQualifiedName());

    AndroidResolveHelper.IntDefResolution result = AndroidResolveHelper.resolveIntDef(annotation);
    assertFalse(result.canBeOred);

    Map<Integer, String> map = result.valuesMap;
    assertNotNull(map);
    assertEquals(3, map.size());
    assertEquals("INVISIBLE", map.get(4));
    assertEquals("VISIBLE", map.get(0));
    assertEquals("GONE", map.get(8));
  }

  public void testAnnotationInferenceFromField() {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "class Foo {\n" +
                  "  @android.support.annotation.ColorInt private int mColor = 0xFF123456;\n" +
                  "  \n" +
                  "  private void check() {\n" +
                  "    int color;\n" +
                  "    <caret>color = mColor;\n" +
                  "  }\n" +
                  "}";
    testResolution(text);
  }

  public void testAnnotationInferenceFromMethod() {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "class Foo {\n" +
                  "  @android.support.annotation.ColorInt int getColor() {\n" +
                  "    return 0x11223344;\n" +
                  "  }\n" +
                  "\n" +
                  "  private void check() {\n" +
                  "    int color;\n" +
                  "    <caret>color = getColor();\n" +
                  "  }\n" +
                  "}";
    testResolution(text);
  }

  public void testAnnotationInferenceFromVariable() {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "class Foo {\n" +
                  "  private void check() {\n" +
                  "    @android.support.annotation.ColorInt int color;\n" +
                  "    <caret>color = getColor();\n" +
                  "  }\n" +
                  "}";
    testResolution(text);
  }

  public void testAnnotationInferenceFromInitializerCall() {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "class Foo {\n" +
                  "  @android.support.annotation.ColorInt int getColor() {\n" +
                  "    return 0x11223344;\n" +
                  "  }\n" +
                  "\n" +
                  "  private void check() {\n" +
                  "    int color = getColor();\n" +
                  "    int x = <caret>color;\n" +
                  "  }\n" +
                  "}";
    testResolution(text);
  }

  public void testAnnotationInferenceFromInitializerField() {
    @Language("JAVA")
    String text = "package p1.p2;\n" +
                  "\n" +
                  "class Foo {\n" +
                  "  @android.support.annotation.ColorInt private int mColor = 0xFF123456;\n" +
                  "  \n" +
                  "  private void check() {\n" +
                  "    int color = mColor;\n" +
                  "    int x = <caret>color;\n" +
                  "  }\n" +
                  "}";
    testResolution(text);
  }

  private void testResolution(String text) {
    PsiElement element = getPsiElement(text);
    assertNotNull(element);

    PsiAnnotation annotation = AndroidResolveHelper.getAnnotationForLocal(element, "color");
    assertNotNull(annotation);
    assertEquals(ResourceEvaluator.COLOR_INT_ANNOTATION, annotation.getQualifiedName());
  }

  private PsiElement getPsiElement(String text) {
    PsiFile file = myFixture.addFileToProject("src/p1/p2/Foo.java", text);
    myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    return myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
  }
}
