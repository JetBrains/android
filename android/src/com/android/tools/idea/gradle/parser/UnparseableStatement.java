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

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;

import java.util.List;

/**
 * This class represents a line of Groovy code we weren't able to parse into a more meaningful type. It preserves the original Groovy
 * source code such that we can write it out later and preserve its original content.
 */
public class UnparseableStatement extends BuildFileStatement {
  private final String myGroovy;
  private final boolean myComment;
  private final Project myProject;

  public UnparseableStatement(String groovy, Project project) {
    myGroovy = groovy;
    myProject = project;
    // TODO: Deal with block-style comments, i.e. /* ... */
    myComment = groovy.startsWith("//");
  }

  public UnparseableStatement(PsiElement element) {
    myGroovy = element.getText();
    myProject = element.getProject();
    myComment = element instanceof PsiComment;
  }

  @Override
  public List<PsiElement> getGroovyElements(GroovyPsiElementFactory factory) {
    if (myComment) {
      PsiElementFactory psiFactory = JavaPsiFacade.getInstance(myProject).getElementFactory();
      return ImmutableList.of(
        factory.createLineTerminator("\n"),
        psiFactory.createCommentFromText(myGroovy, null),
        factory.createLineTerminator("\n"));
    }
    return ImmutableList.of((PsiElement)factory.createStatementFromText(myGroovy));
  }

  @Override
  public String toString() {
    return myGroovy;
  }

  public boolean isComment() {
    return myComment;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || o.getClass() != getClass()) {
      return false;
    }

    UnparseableStatement that = (UnparseableStatement)o;
    return myComment == that.myComment && Objects.equal(myGroovy, that.myGroovy);
  }

  @Override
  public int hashCode() {
    int result = myGroovy != null ? myGroovy.hashCode() : 0;
    result = 31 * result + (myComment ? 1 : 0);
    return result;
  }
}
