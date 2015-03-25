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

import com.android.tools.idea.editors.navigation.macros.Analyser;
import com.android.tools.idea.editors.navigation.macros.CodeTemplate;
import com.android.tools.idea.editors.navigation.macros.MultiMatch;
import com.intellij.psi.*;
import org.jetbrains.android.AndroidTestCase;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class AnalyserTest extends AndroidTestCase {
  public static final String TEMPLATE =
    "void macro(StringBuilder $builder, Object $obj) {" +
    "  $builder.append($obj);" +
    "}";

  private PsiElementFactory myElementFactory = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myElementFactory = JavaPsiFacade.getInstance(getProject()).getElementFactory();
  }

  public void testMultiStatement() {
    final PsiMethod method = myElementFactory.createMethodFromText(
      "void doSomething() {" +
      "  StringBuilder builder = new StringBuilder();" +
      "  builder.append(\"a string\");" +
      "  builder.append(20);" +
      "}" , null);

    final PsiMethod template = myElementFactory.createMethodFromText(TEMPLATE, null);

    final List<MultiMatch.Bindings<PsiElement>> bindingsList = Analyser.search(method, new MultiMatch(CodeTemplate.fromMethod(template)));
    assertEquals(bindingsList.size(), 2);

    final List expectedMatches = Arrays.asList("a string", 20);
    final Iterator iterator = expectedMatches.iterator();

    for (MultiMatch.Bindings<PsiElement> bindings : bindingsList) {
      assertNotNull(bindings.get("$builder"));

      final PsiElement obj = bindings.get("$obj");
      final Object got = assertInstanceOf(obj, PsiLiteralExpression.class).getValue();

      final Object expected = iterator.next();
      assertEquals(got, expected);
    }
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
    myElementFactory = null;
  }
}
