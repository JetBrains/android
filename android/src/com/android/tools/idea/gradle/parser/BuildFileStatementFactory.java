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
package com.android.tools.idea.gradle.parser;

import com.google.common.collect.ImmutableList;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner;

import java.util.Collections;
import java.util.List;

public abstract class BuildFileStatementFactory extends ValueFactory<BuildFileStatement> {
  @Override
  public void setValues(@NotNull GrStatementOwner closure, @NotNull List<BuildFileStatement> statements, @Nullable KeyFilter filter) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(closure.getProject());
    closure = (GrStatementOwner)closure.replace(factory.createClosureFromText("{\n}"));
    for (BuildFileStatement statement : statements) {
      PsiElement lastElement = null;
      for (PsiElement element : statement.getGroovyElements(factory)) {
        closure.addBefore(element, closure.getLastChild());
        lastElement = element;
      }
      // Make sure that statements are separated by newlines.
      if (lastElement != null && !lastElement.getText().endsWith("\n")) {
        closure.addBefore(factory.createLineTerminator("\n"), closure.getLastChild());
      }
    }
    GradleGroovyFile.reformatClosure(closure);
  }

  @Override
  protected void setValue(@NotNull GrStatementOwner closure, @NotNull BuildFileStatement value, @Nullable KeyFilter filter) {
  }

  @NotNull
  protected static List<BuildFileStatement> getUnparseableStatements(@NotNull PsiElement element) {
    // At the moment we only deal with comments or complete Groovy statements. Anything else we drop on the floor.
    if (element instanceof PsiComment || element instanceof GrStatement) {
      return ImmutableList.of((BuildFileStatement)new UnparseableStatement(element));
    } else {
      return Collections.emptyList();
    }
  }
}
