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

import com.android.tools.idea.editors.navigation.macros.CodeTemplate;
import com.android.tools.idea.editors.navigation.macros.Unifier;
import com.intellij.psi.*;
import org.jetbrains.android.AndroidTestCase;

import java.util.Map;

public class UnifierTest extends AndroidTestCase {
  private PsiElementFactory myElementFactory;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myElementFactory = JavaPsiFacade.getInstance(this.getProject()).getElementFactory();
  }

  public void testUnifierSimple() throws Exception {
    final PsiExpression expression = myElementFactory.createExpressionFromText("20 + 22", null);
    final PsiMethod template = myElementFactory.createMethodFromText(
      "void macro(int $x, int $y) {" +
      "    $x + $y;" +
      "}", null);

    final Map<String, PsiElement> result = Unifier.match(CodeTemplate.fromMethod(template), expression);
    assertNotNull(result);

    final PsiElement x = result.get("$x");
    final PsiElement y = result.get("$y");

    final Object xValue = assertInstanceOf(x, PsiLiteral.class).getValue();
    final Object yValue = assertInstanceOf(y, PsiLiteral.class).getValue();

    assertEquals(xValue, Integer.valueOf(20));
    assertEquals(yValue, Integer.valueOf(22));
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    myElementFactory = null;
  }
}
