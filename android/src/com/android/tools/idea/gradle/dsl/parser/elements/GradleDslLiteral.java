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

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.util.GrStringUtil;

import java.util.Collection;

import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;

/**
 * Represents a {@link GrLiteral} element.
 */
public final class GradleDslLiteral extends GradleDslElement {
  @Nullable private GrLiteral myLiteral;
  @Nullable private Object myUnsavedValue;

  public GradleDslLiteral(@NotNull GradleDslElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  public GradleDslLiteral(@NotNull GradleDslElement parent,
                          @NotNull GroovyPsiElement psiElement,
                          @NotNull String name,
                          @NotNull GrLiteral literal) {
    super(parent, psiElement, name);
    myLiteral = literal;
  }

  @Nullable
  GrLiteral getLiteral() {
    return myLiteral;
  }

  @Nullable
  public Object getValue() {
    if (myUnsavedValue != null) {
      return myUnsavedValue;
    }

    if (myLiteral != null) {
      return myLiteral.getValue();
    }
    return null;
  }

  /**
   * Returns the value of type {@code clazz} when the the {@link GrLiteral} element contains the value of that type,
   * or {@code null} otherwise.
   */
  @Nullable
  public <T> T getValue(@NotNull Class<T> clazz) {
    Object value = getValue();
    if (value != null && clazz.isInstance(value)) {
      return clazz.cast(value);
    }
    return null;
  }

  public void setValue(@NotNull Object value) {
    myUnsavedValue = value;
    setModified(true);
  }

  @Override
  public String toString() {
    Object value = getValue();
    return value != null ? value.toString() : super.toString();
  }



  @Override
  @NotNull
  protected Collection<GradleDslElement> getChildren() {
    return ImmutableList.of();
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    if (!(myParent instanceof GradleDslLiteralMap)) {
      return super.create();
    }
    // This is a value in the map element we need to create a named argument for it.
    GroovyPsiElement parentPsiElement = myParent.create();
    if (parentPsiElement == null) {
      return null;
    }

    setPsiElement(parentPsiElement);
    GrLiteral newLiteral = createLiteral();
    if (newLiteral == null) {
      return null;
    }

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(newLiteral.getProject());
    GrNamedArgument namedArgument = factory.createNamedArgument(myName, newLiteral);
    PsiElement added = parentPsiElement.addAfter(namedArgument, parentPsiElement.getLastChild());
    if (added instanceof GrNamedArgument) {
      GrNamedArgument addedNameArgument = (GrNamedArgument)added;
      GrLiteral literal = getChildOfType(addedNameArgument, GrLiteral.class);
      if (literal != null) {
        myLiteral = literal;
        setModified(false);
        return getPsiElement();
      }
    }
    return null;
  }

  @Override
  protected void delete() {
    if(myLiteral == null) {
      return;
    }
    PsiElement parent = myLiteral.getParent();
    myLiteral.delete();
    if (parent instanceof GrAssignmentExpression) {
      parent.delete(); // Delete the empty assignment statement without any value.
    }
    if (parent instanceof GrCommandArgumentList) {
      deleteIfEmpty((GrCommandArgumentList)parent);
    }
    else if (parent instanceof GrListOrMap) {
      deleteIfEmpty((GrListOrMap)parent);
    }
  }

  private static void deleteIfEmpty(@NotNull GrCommandArgumentList commandArgumentList) {
    if (commandArgumentList.getAllArguments().length > 0) {
      return;
    }
    PsiElement parent = commandArgumentList.getParent();
    commandArgumentList.delete();
    if (parent instanceof GrApplicationStatement) {
      parent.delete(); // Delete the empty application statement without any arguments.
    }
  }

  private static void deleteIfEmpty(@NotNull GrListOrMap listOrMap) {
    if ((listOrMap.isMap() && listOrMap.getNamedArguments().length > 0) || (!listOrMap.isMap() && listOrMap.getInitializers().length > 0) ) {
      return;
    }
    PsiElement parent = listOrMap.getParent();
    listOrMap.delete();
    if (parent instanceof GrAssignmentExpression) {
      parent.delete(); // Delete the empty assignment statement without any arguments.
    }
  }

  @Override
  protected void apply() {
    GroovyPsiElement psiElement = getPsiElement();
    if (psiElement == null) {
      return;
    }

    GrLiteral newLiteral = createLiteral();
    if (newLiteral == null) {
      return;
    }
    if (myLiteral != null) {
      PsiElement replace = myLiteral.replace(newLiteral);
      if (replace instanceof GrLiteral) {
        myLiteral = (GrLiteral)replace;
      }
    }
    else {
      PsiElement added = psiElement.addAfter(newLiteral, psiElement.getLastChild());
      if (added instanceof GrLiteral) {
        myLiteral = (GrLiteral)added;
      }
    }
  }

  @Override
  protected void reset() {
    myUnsavedValue = null;
  }

  @Nullable
  private GrLiteral createLiteral() {
    if(myUnsavedValue == null) {
      return null;
    }

    CharSequence unsavedValueText = null;
    if (myUnsavedValue instanceof String) {
      unsavedValueText = GrStringUtil.getLiteralTextByValue((String)myUnsavedValue);
    } else if (myUnsavedValue instanceof Integer || myUnsavedValue instanceof Boolean) {
      unsavedValueText = myUnsavedValue.toString();
    }
    myUnsavedValue = null;

    if(unsavedValueText == null) {
      return null;
    }

    GroovyPsiElement psiElement = getPsiElement();
    if (psiElement == null) {
      return null;
    }

    Project project = psiElement.getProject();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    GrExpression newExpression = factory.createExpressionFromText(unsavedValueText);

    if (!(newExpression instanceof GrLiteral)) {
      return null;
    }
    return (GrLiteral)newExpression;
  }
}
