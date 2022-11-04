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
package com.android.tools.idea.lint;

import com.android.tools.idea.lint.common.AndroidQuickfixContexts;
import com.android.tools.idea.lint.quickFixes.AddTargetVersionCheckQuickFix;
import com.android.tools.lint.detector.api.ApiConstraint;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.JavaRecursiveElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.android.facet.AndroidFacet;

import static com.android.tools.lint.detector.api.ExtensionSdk.ANDROID_SDK_ID;
import static com.google.common.truth.Truth.assertThat;

public class AddTargetVersionCheckQuickFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNotApplicableInJavaModules() {
    PsiFile file = myFixture.configureByText("X.java", "" +
                                                       "package com.example;\n" +
                                                       "\n" +
                                                       "import java.io.FileReader;\n" +
                                                       "import java.io.IOException;\n" +
                                                       "import java.util.Properties;\n" +
                                                       "\n" +
                                                       "public class X {\n" +
                                                       "  public static void foo() throws IOException {\n" +
                                                       "    FileReader reader=new FileReader(\"../local.properties\");\n" +
                                                       "    Properties props=new Properties();\n" +
                                                       "    props.load(reader);\n" +
                                                       "    reader.close();\n" +
                                                       "  }\n" +
                                                       "}");
    Ref<PsiExpression> first = new Ref<>();
    file.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitExpression(PsiExpression expression) {
        if (first.isNull()) {
          first.set(expression);
        }
        super.visitExpression(expression);
      }
    });
    PsiExpression expression = first.get();
    assertThat(expression).isNotNull();
    AddTargetVersionCheckQuickFix fix = new AddTargetVersionCheckQuickFix(9, ANDROID_SDK_ID, ApiConstraint.ALL);
    assertThat(fix.getName()).isEqualTo("Surround with if (VERSION.SDK_INT >= VERSION_CODES.GINGERBREAD) { ... }");
    assertThat(AndroidFacet.getInstance(expression)).isNull();
    // Regression test for https://code.google.com/p/android/issues/detail?id=228481 :
    // Ensure that no VERSION.SDK_INT check is offered.
    assertThat(fix.isApplicable(expression, expression, AndroidQuickfixContexts.EditorContext.TYPE)).isFalse();
  }

  // There are actual functional tests of the operation of this quickfix in AndroidLintTest, such as AndroidLintTest#testApiCheck1f
}