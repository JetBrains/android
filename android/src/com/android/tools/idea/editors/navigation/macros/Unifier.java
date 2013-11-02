/*
 * Copyright (C) 2013 The Android Open Source Project
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
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class Unifier {
  private boolean debug = true;
  private int indent = 0;

  private String indent() {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < indent; i++) {
      b.append("  ");
    }
    return b.toString();
  }

  @Nullable
  public Map<String, PsiElement> unify(PsiElement template, PsiElement candidate) {
    Matcher myMatcher = new Matcher(candidate);
    template.accept(myMatcher);
    return myMatcher.getBindings();
  }

  private class Matcher extends JavaElementVisitor {
    Map<String, PsiElement> bindings = new HashMap<String, PsiElement>();
    Map<String, String> parameterBindings = new HashMap<String, String>();
    private boolean valid = true;
    private PsiElement candidate;

    private Matcher(PsiElement candidate) {
      this.candidate = candidate;
    }

    private boolean equals(PsiIdentifier identifier1, PsiElement identifier2) {
      return identifier2 instanceof PsiIdentifier && identifier1.getText().equals(identifier2.getText());
    }

    @Override
    public void visitParameter(PsiParameter parameter) {
      String name = parameter.getName();
      if (parameterBindings.get(name) != null) {
        assert false;
      }
      parameterBindings.put(name, ((PsiParameter) candidate).getName());
    }

    private boolean isBound(String text) {
      return text.startsWith("$") || parameterBindings.containsKey(text);
    }

    @Override
    public void visitIdentifier(PsiIdentifier identifier) {
      String text = identifier.getText();
      if (isBound(text)) {
        bindings.put(text, candidate);
      }
      else {
        if (!equals(identifier, candidate)) {
          valid = false;
        }
      }
    }

    /** @see JavaElementVisitor#visitReferenceExpression(PsiReferenceExpression) */
    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      String text = expression.getText();
      if (isBound(text) && !text.contains(".")) {
        bindings.put(text, candidate);
      }
      else {
        visitElement(expression);
      }
    }

    @Override
    public void visitElement(PsiElement template) {
      if (template.getClass() != candidate.getClass()) {
        if (debug) System.out.println(indent() + template + " != " + candidate);
        valid = false;
        return;
      }
      indent++;
      PsiElement tmp = candidate;

      if (debug) System.out.println(indent() + template + " : " + candidate);

      PsiElement child = template.getFirstChild();
      candidate = candidate.getFirstChild();
      while (valid && (child != null) && (candidate != null)) {
        child.accept(this);
        child = child.getNextSibling();
        candidate = candidate.getNextSibling();
      }

      candidate = tmp;
      indent--;
    }

    @Nullable
    private Map<String, PsiElement> getBindings() {
      return valid ? bindings : null;
    }
  }
}
