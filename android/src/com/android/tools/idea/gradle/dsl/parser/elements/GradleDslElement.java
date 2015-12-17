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
package com.android.tools.idea.gradle.dsl.parser.elements;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collection;

/**
 * Provide Gradle specific abstraction over a {@link GroovyPsiElement}.
 */
public abstract class GradleDslElement {
  @Nullable protected final GradleDslElement myParent;

  @NotNull protected final String myName;

  @Nullable private GroovyPsiElement myPsiElement;

  private volatile boolean myModified;

  protected GradleDslElement(@Nullable GradleDslElement parent, @Nullable GroovyPsiElement psiElement, @NotNull String name) {
    myParent = parent;
    myPsiElement = psiElement;
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public GroovyPsiElement getPsiElement() {
    return myPsiElement;
  }

  public void setPsiElement(@Nullable GroovyPsiElement psiElement) {
    myPsiElement = psiElement;
  }

  /**
   * Creates the {@link GroovyPsiElement} by adding this element to the .gradle file.
   *
   * <p>It creates a new {@link GroovyPsiElement} only when {@link #getPsiElement()} return {@code null}.
   *
   * <p>Returns the final {@link GroovyPsiElement} corresponds to this element or {@code null} when failed to create the
   * {@link GroovyPsiElement}.
   */
  @Nullable
  public GroovyPsiElement create() {
    GroovyPsiElement psiElement = getPsiElement();
    if (psiElement != null) {
      return psiElement;
    }

    if (myParent == null) {
      return null;
    }

    GroovyPsiElement parentPsiElement = myParent.create();
    if (parentPsiElement == null) {
      return null;
    }
    Project project = parentPsiElement.getProject();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);

    String statementText = isBlockElement() ? myName + " {\n}\n" : myName + " \"abc\", \"xyz\"";
    GrStatement statement = factory.createStatementFromText(statementText);
    if (statement instanceof GrApplicationStatement) {
      // Workaround to create an application statement.
      ((GrApplicationStatement)statement).getArgumentList().delete();
    }
    PsiElement addedElement = parentPsiElement.addBefore(statement, parentPsiElement.getLastChild());
    if (isBlockElement()) {
      GrClosableBlock closableBlock = getClosableBlock(addedElement);
      if (closableBlock != null) {
        setPsiElement(closableBlock);
      }
    } else {
      if (addedElement instanceof GrApplicationStatement) {
        setPsiElement((GrApplicationStatement)addedElement);
      }
    }
    PsiElement lineTerminator = factory.createLineTerminator(1);
    parentPsiElement.addAfter(lineTerminator, addedElement);
    return getPsiElement();
  }

  /**
   * Deletes this element and all it's children from the .gradle file.
   */
  protected void delete() {
    for (GradleDslElement element : getChildren()) {
      element.delete();
    }
    GroovyPsiElement psiElement = getPsiElement();
    if (psiElement != null && psiElement.isValid()) {
      psiElement.delete();
    }
    setPsiElement(null);
  }

  protected void setModified(boolean modified) {
    myModified = modified;
    if (myParent != null && modified) {
      myParent.setModified(true);
    }
  }

  public boolean isModified() {
    return myModified;
  }

  /**
   * Returns {@code true} if this element represents a {@link GrClosableBlock} element (Ex. android, productFlavors, dependencies etc.),
   * {@code false} otherwise.
   */
  protected boolean isBlockElement() {
    return false;
  }

  @NotNull
  protected abstract Collection<GradleDslElement> getChildren();

  public final void applyChanges() {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    apply();
    setModified(false);
  }

  protected abstract void apply();

  public final void resetState() {
    reset();
    setModified(false);
  }

  protected abstract void reset();

  @Nullable
  private static GrClosableBlock getClosableBlock(PsiElement element) {
    if (!(element instanceof GrMethodCallExpression)) {
      return null;
    }

    GrClosableBlock[] closureArguments = ((GrMethodCallExpression)element).getClosureArguments();
    if (closureArguments.length > 0) {
      return closureArguments[0];
    }

    return null;
  }
}
