/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers;

import com.intellij.psi.PsiClass;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

/**
 * Tests for {@link ViewEditorImpl}.
 */
public class ViewEditorImplTest extends AndroidTestCase {

  public void testIsDisplayable() {
    @Language("JAVA")
    String restrictText =
      "package android.support.annotation;\n" +
      "\n" +
      "import static java.lang.annotation.ElementType.ANNOTATION_TYPE;\n" +
      "import static java.lang.annotation.ElementType.CONSTRUCTOR;\n" +
      "import static java.lang.annotation.ElementType.FIELD;\n" +
      "import static java.lang.annotation.ElementType.METHOD;\n" +
      "import static java.lang.annotation.ElementType.PACKAGE;\n" +
      "import static java.lang.annotation.ElementType.TYPE;\n" +
      "import static java.lang.annotation.RetentionPolicy.CLASS;\n" +
      "\n" +
      "import java.lang.annotation.Retention;\n" +
      "import java.lang.annotation.Target;\n" +
      "\n" +
      "@Retention(CLASS)\n" +
      "@Target({ANNOTATION_TYPE,TYPE,METHOD,CONSTRUCTOR,FIELD,PACKAGE})\n" +
      "public @interface RestrictTo {\n" +
      "\n" +
      "    Scope[] value();\n" +
      "\n" +
      "    enum Scope {\n" +
      "        LIBRARY,\n" +
      "        LIBRARY_GROUP,\n" +
      "        @Deprecated\n" +
      "        GROUP_ID,\n" +
      "        TESTS,\n" +
      "        SUBCLASSES,\n" +
      "    }\n" +
      "}";

    @Language("JAVA")
    String hiddenText =
      "package p1.p2;\n" +
      "\n" +
      "import android.content.Context;\n" +
      "import android.support.annotation.RestrictTo;\n" +
      "import android.widget.ImageView;\n" +
      "\n" +
      "@RestrictTo(RestrictTo.Scope.SUBCLASSES)\n" +
      "public class HiddenImageView extends ImageView {\n" +
      "    public HiddenImageView(Context context) {\n" +
      "        super(context);\n" +
      "    }\n" +
      "}";

    @Language("JAVA")
    String visibleText =
      "package p1.p2;\n" +
      "\n" +
      "import android.content.Context;\n" +
      "import android.widget.ImageView;\n" +
      "\n" +
      "public class VisibleImageView extends ImageView {\n" +
      "    public VisibleImageView(Context context) {\n" +
      "        super(context);\n" +
      "    }\n" +
      "}";

    myFixture.addClass(restrictText);
    PsiClass hiddenClass = myFixture.addClass(hiddenText);
    PsiClass visibleClass = myFixture.addClass(visibleText);
    assertTrue(ViewEditorImpl.isRestricted(hiddenClass));
    assertFalse(ViewEditorImpl.isRestricted(visibleClass));
  }
}
