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
public abstract class GradlePsiElement {
  @Nullable protected final GradlePsiElement myParent;

  @NotNull protected final String myName;

  @Nullable private GroovyPsiElement myGroovyPsiElement;

  private volatile boolean myModified;

  protected GradlePsiElement(@Nullable GradlePsiElement parent, @Nullable GroovyPsiElement groovyPsiElement, @NotNull String name) {
    myParent = parent;
    myGroovyPsiElement = groovyPsiElement;
    myName = name;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public GroovyPsiElement getGroovyPsiElement() {
    return myGroovyPsiElement;
  }

  public void setGroovyPsiElement(@Nullable GroovyPsiElement groovyPsiElement) {
    myGroovyPsiElement = groovyPsiElement;
  }

  /**
   * Creates the {@link GroovyPsiElement} by adding this element to the .gradle file.
   *
   * <p>It creates a new {@link GroovyPsiElement} only when {@link #getGroovyPsiElement()} return {@code null}.
   *
   * <p>Returns the final {@link GroovyPsiElement} corresponds to this element or {@code null} when failed to create the
   * {@link GroovyPsiElement}.
   */
  @Nullable
  public GroovyPsiElement create() {
    GroovyPsiElement psiElement = getGroovyPsiElement();
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
        setGroovyPsiElement(closableBlock);
      }
    } else {
      if (addedElement instanceof GrApplicationStatement) {
        setGroovyPsiElement((GrApplicationStatement)addedElement);
      }
    }
    PsiElement lineTerminator = factory.createLineTerminator(1);
    parentPsiElement.addAfter(lineTerminator, addedElement);
    return getGroovyPsiElement();
  }

  /**
   * Deletes this element and all it's children from the .gradle file.
   */
  protected void delete() {
    for (GradlePsiElement element : getChildren()) {
      element.delete();
    }
    GroovyPsiElement psiElement = getGroovyPsiElement();
    if (psiElement != null && psiElement.isValid()) {
      psiElement.delete();
    }
    setGroovyPsiElement(null);
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
  protected abstract Collection<GradlePsiElement> getChildren();

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
