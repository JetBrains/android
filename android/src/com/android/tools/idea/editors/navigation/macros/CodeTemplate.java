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
package com.android.tools.idea.editors.navigation.macros;

import com.intellij.psi.*;

import java.util.ArrayList;
import java.util.List;

public class CodeTemplate {
  private final List<String> myParameters;
  private final PsiElement myBody;

  public CodeTemplate(List<String> parameters, PsiElement body) {
    myParameters = parameters;
    myBody = body;
  }

  public List<String> getParameters() {
    return myParameters;
  }

  public PsiElement getBody() {
    return myBody;
  }

  public static CodeTemplate fromMethod(final PsiMethod method) {
    final List<String> parameters = new ArrayList<String>();
    for (PsiParameter parameter : method.getParameterList().getParameters()) {
      parameters.add(parameter.getName());
    }

    final PsiCodeBlock methodBody = method.getBody();
    assert methodBody != null;
    final PsiStatement[] statements = methodBody.getStatements();
    assert statements.length == 1;
    final PsiElement templateBody = statements[0].getFirstChild();

    return new CodeTemplate(parameters, templateBody);
  }
}
