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
package com.android.tools.idea.gradle.dsl.parser.java;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslReference;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.gradle.dsl.model.java.JavaModel.SOURCE_COMPATIBILITY_FIELD;
import static com.android.tools.idea.gradle.dsl.model.java.JavaModel.TARGET_COMPATIBILITY_FIELD;
import static com.android.tools.idea.gradle.dsl.parser.java.LanguageLevelUtil.convertToGradleString;
import static com.android.tools.idea.gradle.dsl.parser.java.LanguageLevelUtil.parseFromGradleString;

public class JavaVersionDslElement extends GradleDslElement {
  private GradleDslElement myInternalVersionElement;
  @Nullable private LanguageLevel myUnsavedValue;

  public JavaVersionDslElement(@Nullable JavaDslElement parent, @NotNull GradleDslElement dslElement, @NotNull String name) {
    super(parent, null, name);
    assert dslElement instanceof GradleDslLiteral || dslElement instanceof GradleDslReference;
    assert dslElement.getPsiElement() instanceof GrAssignmentExpression;
    myInternalVersionElement = dslElement;
  }

  public JavaVersionDslElement(@Nullable JavaDslElement parent, @NotNull String name) {
    super(parent, null, name);
  }

  @Override
  public GrAssignmentExpression getPsiElement() {
    if (myInternalVersionElement != null) {
      return (GrAssignmentExpression)myInternalVersionElement.getPsiElement();
    }
    return null;
  }

  @Override
  public void setPsiElement(@Nullable GroovyPsiElement psiElement) {
    // This class just co-ordinates different notations of Java versions and doesn't represent any real elements on the file.
  }

  @NotNull
  @Override
  protected Collection<GradleDslElement> getChildren() {
    return Collections.emptyList();
  }

  @Nullable
  public LanguageLevel getVersion() {
    if (myUnsavedValue != null) {
      return myUnsavedValue;
    }
    String text = getVersionText();
    if (text != null) {
      return parseFromGradleString(text);
    }
    return null;
  }

  @Nullable
  private String getVersionText() {
    GradleDslLiteral valueLiteral = null;

    if (myInternalVersionElement instanceof GradleDslReference) {
      GradleDslReference referenceElement = (GradleDslReference)myInternalVersionElement;
      valueLiteral = referenceElement.getResolvedValue(GradleDslLiteral.class);
      if (valueLiteral == null) {
        String resolvedReference = referenceElement.getResolvedValue(String.class);
        if (resolvedReference != null) {
          return resolvedReference;
        }
        else {
          return referenceElement.getReferenceText();
        }
      }
    }

    if (myInternalVersionElement instanceof GradleDslLiteral) {
      valueLiteral = (GradleDslLiteral)myInternalVersionElement;
    }

    if (valueLiteral != null) {
      GrLiteral literal = valueLiteral.getLiteral();
      if (literal != null) {
        return literal.getText();
      }
    }

    return null;
  }

  /**
   * Sets new Java version while keeping the original version format.
   */
  public void setVersion(@NotNull LanguageLevel languageLevel) {
    myUnsavedValue = languageLevel;
    setModified(true);
  }

  @Override
  protected void apply() {
    GrAssignmentExpression assignmentExpression = getPsiElement();

    if (myUnsavedValue == null || assignmentExpression == null) {
      return;
    }

    String groovyString = convertToGradleString(myUnsavedValue, getVersionText());
    GrExpression expression = GroovyPsiElementFactory.getInstance(assignmentExpression.getProject()).createExpressionFromText(groovyString);

    GrExpression rValue = assignmentExpression.getRValue();
    if (rValue != null) {
      rValue.replace(expression);
    }
    else {
      assignmentExpression.addAfter(expression, assignmentExpression.getOperationToken());
    }
  }

  @Override
  protected void reset() {
    myUnsavedValue = null;
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    GroovyPsiElement psiElement = getPsiElement();
    if (psiElement != null) {
      return psiElement;
    }

    JavaDslElement java = (JavaDslElement)myParent;
    assert java != null;
    GroovyPsiElement javaPsiElement = java.create();
    assert javaPsiElement != null;
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(javaPsiElement.getProject());

    // Tries to create the new element close to targetCompatibility or sourceCompatibility, if neither of them exists, create it at the
    // end of the file.
    // Also, tries to copy the value from targetCompatibility or sourceCompatibility if possible to keep consistency.
    JavaVersionDslElement anchor = null;

    if (SOURCE_COMPATIBILITY_FIELD.equals(myName)) {
      anchor = java.getProperty(TARGET_COMPATIBILITY_FIELD, JavaVersionDslElement.class);
    }
    else if (TARGET_COMPATIBILITY_FIELD.equals(myName)) {
      anchor = java.getProperty(SOURCE_COMPATIBILITY_FIELD, JavaVersionDslElement.class);
    }

    PsiElement anchorPsiElement = null;
    String anchorText = null;
    if (anchor != null) {
      anchorPsiElement = anchor.getPsiElement();
      anchorText = anchor.getVersionText();
    }

    if (anchorPsiElement == null) {
      anchorPsiElement = javaPsiElement.getLastChild();
    }

    if (anchorText == null) {
      anchorText = "1.6";
    }

    GrAssignmentExpression expression = (GrAssignmentExpression)factory.createExpressionFromText(myName + " = " + anchorText);
    GrAssignmentExpression added = (GrAssignmentExpression)javaPsiElement.addAfter(expression, anchorPsiElement);

    GrExpression right = added.getRValue();
    if (right instanceof GrLiteral) {
      myInternalVersionElement = new GradleDslLiteral(java, added, myName, (GrLiteral)right);
    }
    else if (right instanceof GrReferenceExpression) {
      myInternalVersionElement = new GradleDslReference(java, added, myName, (GrReferenceExpression)right);
    }
    return getPsiElement();
  }

  @Override
  protected void delete() {
    GrAssignmentExpression psiElement = getPsiElement();
    if (psiElement != null) {
      psiElement.delete();
    }
  }
}
