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
package com.android.tools.idea.editors.navigation;

import com.android.tools.idea.editors.navigation.macros.Unifier;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.android.AndroidTestCase;

import java.util.Map;

public class UnifierTest extends AndroidTestCase {

  public void testUnifier() throws Exception {
    final Project project = this.getProject();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiElementFactory elementFactory = facade.getElementFactory();

    final PsiClass psiClass = elementFactory.createClass("Dummy");
    final PsiExpression expression = elementFactory.createExpressionFromText("20 + 22", psiClass);
    final PsiMethod template = elementFactory.createMethodFromText(
      "void macro(int $x, int $y) {" +
      "    $x + $y;" +
      "}", psiClass);

    Unifier.DEBUG = true;
    final Map<String, PsiElement> result = Unifier.match(template, expression);
    assertNotNull(result);

    final PsiElement x = result.get("$x");
    final PsiElement y = result.get("$y");

    assertInstanceOf(x, PsiLiteral.class);
    assertInstanceOf(y, PsiLiteral.class);

    assertEquals(((PsiLiteral) x).getValue(), Integer.valueOf(20));
    assertEquals(((PsiLiteral) y).getValue(), Integer.valueOf(22));
  }
}
