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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.java.PsiIdentifierImpl;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class Unifier {
  private static final Logger LOG = Logger.getInstance("#" + Unifier.class.getName());
  public static final PsiElement UNBOUND = new PsiIdentifierImpl("<Unbound>");
  public static final String STATEMENT_SENTINEL = "$"; // A stand-in method name used so that wild cards can form statements as well as expressions - e.g. $f.$()
  private static boolean DEBUG = false;
  private int indent = 0;

  private String indent() {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < indent; i++) {
      b.append("  ");
    }
    return b.toString();
  }

  @Nullable
  public Map<String, PsiElement> unify(PsiMethod macro, PsiElement candidate) {
    return unify(macro.getParameterList(), macro.getBody(), candidate);
  }

  @Nullable
  public Map<String, PsiElement> unify(PsiParameterList parameterList, PsiElement body, PsiElement candidate) {
    Matcher myMatcher = new Matcher(candidate);
    for (PsiParameter parameter : parameterList.getParameters()) {
      myMatcher.bindings.put(parameter.getName(), UNBOUND);
    }
    body.accept(myMatcher);
    Map<String, PsiElement> bindings = myMatcher.getBindings();
    if (DEBUG) System.out.println("bindings = " + bindings);
    return bindings;
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
      parameterBindings.put(name, ((PsiParameter)candidate).getName());
    }

    private boolean isBindable(String text) {
      return bindings.containsKey(text) || parameterBindings.containsKey(text);
    }

    @Override
    public void visitIdentifier(PsiIdentifier identifier) {
      String text = identifier.getText();
      if (isBindable(text)) {
        bindings.put(text, candidate);
      }
      else {
        if (!equals(identifier, candidate)) {
          valid = false;
        }
      }
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      if (expression.getFirstChild().getLastChild().getText().equals(STATEMENT_SENTINEL)) {
        bindings.put(expression.getFirstChild().getFirstChild().getText(), candidate);
      }
      else {
        super.visitMethodCallExpression(expression);
      }
    }

    /**
     * @see JavaElementVisitor#visitReferenceExpression(PsiReferenceExpression)
     */
    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
      String text = expression.getText();
      if (isBindable(text)) {
        bindings.put(text, candidate);
      }
      else {
        visitReferenceElement(expression);
      }
    }

    @Override
    public void visitElement(PsiElement template) {
      if (template.getClass() != candidate.getClass()) {
        if (DEBUG) System.out.println(indent() + template + " != " + candidate);
        valid = false;
        return;
      }
      indent++;
      PsiElement tmp = candidate;

      if (DEBUG) System.out.println(indent() + template + " : " + candidate);

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
