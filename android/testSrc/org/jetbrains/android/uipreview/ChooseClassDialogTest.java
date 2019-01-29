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
package org.jetbrains.android.uipreview;

import com.intellij.psi.PsiClass;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.AndroidTestCase;

import java.util.function.Predicate;

import static com.android.SdkConstants.*;

public class ChooseClassDialogTest extends AndroidTestCase {
  public void testIsPublicAndUnRestricted() {
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
    String protectedView =
      "package p1.p2;\n" +
      "\n" +
      "import android.content.Context;\n" +
      "import android.widget.ImageView;\n" +
      "\n" +
      "class ProtectedImageView extends ImageView {\n" +
      "    public ProtectedImageView(Context context) {\n" +
      "        super(context);\n" +
      "    }\n" +
      "}";

    @Language("JAVA")
    String restrictedView =
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
    String view =
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
    Predicate<PsiClass> isPublicAndUnRestricted = ChooseClassDialog.getIsPublicAndUnrestrictedFilter();
    assertFalse(isPublicAndUnRestricted.test(myFixture.addClass(protectedView)));
    assertFalse(isPublicAndUnRestricted.test(myFixture.addClass(restrictedView)));
    assertTrue(isPublicAndUnRestricted.test(myFixture.addClass(view)));
  }

  public void testIsUserDefined() {
    Predicate<String> isUserDefined = ChooseClassDialog.getIsUserDefinedFilter();
    assertFalse(isUserDefined.test(FQCN_IMAGE_VIEW));
    assertFalse(isUserDefined.test("android.view.ViewStub"));
    assertFalse(isUserDefined.test("android.webkit.WebView"));
    assertFalse(isUserDefined.test(CLASS_AD_VIEW));
    assertFalse(isUserDefined.test(CLASS_CONSTRAINT_LAYOUT.oldName()));
    assertFalse(isUserDefined.test(CLASS_CONSTRAINT_LAYOUT.newName()));
    assertTrue(isUserDefined.test("p1.p2.CustomImageView"));
  }
}
