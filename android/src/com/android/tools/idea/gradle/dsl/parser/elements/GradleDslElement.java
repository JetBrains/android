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

import com.android.tools.idea.gradle.dsl.parser.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.GradleResolvedVariable;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameterList;

import java.util.Collection;
import java.util.List;

/**
 * Provide Gradle specific abstraction over a {@link GroovyPsiElement}.
 */
public abstract class GradleDslElement {
  @NotNull protected final String myName;

  @Nullable protected GradleDslElement myParent;

  @NotNull private final String myQualifiedName;
  @NotNull private final GradleDslFile myDslFile;

  @Nullable private GroovyPsiElement myPsiElement;

  private volatile boolean myModified;

  /**
   * Creates an in stance of a {@link GradleDslElement}
   *
   * @param parent the parent {@link GradleDslElement} of this element. The parent element should always be a not-null value except if this
   *               element is the root element, i.e a {@link GradleDslFile}.
   * @param psiElement the {@link GroovyPsiElement} of this dsl element.
   * @param name the name of this element.
   */
  protected GradleDslElement(@Nullable GradleDslElement parent, @Nullable GroovyPsiElement psiElement, @NotNull String name) {
    assert parent != null || this instanceof GradleDslFile;

    myParent = parent;
    myPsiElement = psiElement;
    myName = name;

    if (parent == null || parent instanceof GradleDslFile) {
      myQualifiedName = name;
    }
    else {
      myQualifiedName = parent.myQualifiedName + "." + name;
    }

    if (parent == null) {
      myDslFile = (GradleDslFile)this;
    }
    else {
      myDslFile = parent.myDslFile;
    }
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Nullable
  public GradleDslElement getParent() {
    return myParent;
  }

  @Nullable
  public GroovyPsiElement getPsiElement() {
    return myPsiElement;
  }

  public void setPsiElement(@Nullable GroovyPsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @NotNull
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @NotNull
  public GradleDslFile getDslFile() {
    return myDslFile;
  }

  @NotNull
  public List<GradleResolvedVariable> getResolvedVariables() {
    ImmutableList.Builder<GradleResolvedVariable> resultBuilder = ImmutableList.builder();
    for (GradleDslElement child : getChildren()) {
      resultBuilder.addAll(child.getResolvedVariables());
    }
    return resultBuilder.build();
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

    if (isNewEmptyBlockElement()) {
      return null; // Avoid creation of an empty block statement.
    }

    String statementText = myName + (isBlockElement() ? " {\n}\n" : " \"abc\", \"xyz\"");
    GrStatement statement = factory.createStatementFromText(statementText);
    if (statement instanceof GrApplicationStatement) {
      // Workaround to create an application statement.
      ((GrApplicationStatement)statement).getArgumentList().delete();
    }
    PsiElement lineTerminator = factory.createLineTerminator(1);
    PsiElement addedElement;
    if (parentPsiElement instanceof GroovyFile) {
      addedElement = parentPsiElement.addAfter(statement, parentPsiElement.getLastChild());
      parentPsiElement.addBefore(lineTerminator, addedElement);
    }
    else {
      addedElement = parentPsiElement.addBefore(statement, parentPsiElement.getLastChild());
      parentPsiElement.addAfter(lineTerminator, addedElement);
    }
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
    return getPsiElement();
  }

  private boolean isNewEmptyBlockElement() {
    if (getPsiElement() != null) {
      return false;
    }

    if (!isBlockElement()) {
      return false;
    }

    Collection<GradleDslElement> children = getChildren();
    if (children.isEmpty()) {
      return true;
    }

    for (GradleDslElement child : children) {
      if (!child.isNewEmptyBlockElement()) {
        return false;
      }
    }

    return true;
  }

  /**
   * Deletes this element and all it's children from the .gradle file.
   */
  protected void delete() {
    for (GradleDslElement element : getChildren()) {
      element.delete();
    }

    GroovyPsiElement psiElement = getPsiElement();
    if(psiElement == null || !psiElement.isValid()) {
      return;
    }

    PsiElement parent = psiElement.getParent();
    psiElement.delete();

    if (parent != null) {
      deleteIfEmpty(parent);
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

  protected static void deleteIfEmpty(@Nullable PsiElement element) {
    if(element == null || !element.isValid()) {
      return;
    }

    PsiElement parent = element.getParent();

    if (element instanceof GrAssignmentExpression) {
      if (((GrAssignmentExpression)element).getRValue() == null) {
        element.delete();
      }
    }
    else if (element instanceof GrApplicationStatement) {
      if (((GrApplicationStatement)element).getArgumentList() == null) {
        element.delete();
      }
    }
    else if (element instanceof GrClosableBlock) {
      final Boolean[] isEmpty = new Boolean[]{true};
      ((GrClosableBlock)element).acceptChildren(new GroovyElementVisitor() {
        @Override
        public void visitElement(GroovyPsiElement child) {
          if (child instanceof GrParameterList) {
            if (((GrParameterList)child).getParameters().length == 0) {
              return; // Ignore the empty parameter list.
            }
          }
          isEmpty[0] = false;
        }
      });
      if (isEmpty[0]) {
        element.delete();
      }
    }
    else if (element instanceof GrMethodCallExpression) {
      GrMethodCallExpression call = ((GrMethodCallExpression)element);
      GrArgumentList argumentList = call.getArgumentList();
      GrClosableBlock[] closureArguments = call.getClosureArguments();
      if ((argumentList == null || argumentList.getAllArguments().length == 0)
          && closureArguments.length == 0) {
        element.delete();
      }
    }
    else if (element instanceof GrCommandArgumentList) {
      GrCommandArgumentList commandArgumentList = (GrCommandArgumentList)element;
      if (commandArgumentList.getAllArguments().length == 0) {
        commandArgumentList.delete();
      }
    }
    else if (element instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)element;
      if ((listOrMap.isMap() && listOrMap.getNamedArguments().length == 0)
          || (!listOrMap.isMap() && listOrMap.getInitializers().length == 0) ) {
        listOrMap.delete();
      }
    }

    if (!element.isValid()) { // If this element is deleted, also delete the parent if it is empty.
      deleteIfEmpty(parent);
    }
  }
}
