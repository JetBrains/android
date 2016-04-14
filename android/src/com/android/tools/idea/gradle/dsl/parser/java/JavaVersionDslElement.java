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

import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.SOURCE_COMPATIBILITY_ATTRIBUTE_NAME;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.TARGET_COMPATIBILITY_ATTRIBUTE_NAME;
import static com.android.tools.idea.gradle.dsl.parser.java.LanguageLevelUtil.convertToGradleString;
import static com.android.tools.idea.gradle.dsl.parser.java.LanguageLevelUtil.parseFromGradleString;

public class JavaVersionDslElement extends GradleDslElement {
  private GradleDslExpression myInternalVersionElement;
  private boolean myUseAssignment;
  @Nullable private LanguageLevel myUnsavedValue;

  public JavaVersionDslElement(@Nullable GradleDslElement parent, @NotNull GradleDslExpression dslElement, @NotNull String name) {
    super(parent, null, name);
    assert dslElement instanceof GradleDslLiteral || dslElement instanceof GradleDslReference;
    if (dslElement.getPsiElement() instanceof GrAssignmentExpression) {
      myUseAssignment = true;
    }
    myInternalVersionElement = dslElement;
  }

  public JavaVersionDslElement(@Nullable GradleDslElement parent, @NotNull String name, @NotNull boolean useAssignment) {
    super(parent, null, name);
    myUseAssignment = useAssignment;
  }

  @Override
  public GrExpression getPsiElement() {
    if (myInternalVersionElement != null) {
      GroovyPsiElement psiElement = myInternalVersionElement.getPsiElement();
      if (psiElement instanceof GrCommandArgumentList) {
        return (GrExpression)psiElement.getParent();
      }
      return (GrExpression)psiElement;
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
      valueLiteral = referenceElement.getValue(GradleDslLiteral.class);
      if (valueLiteral == null) {
        String resolvedReference = referenceElement.getValue(String.class);
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
    GrExpression psiElement = getPsiElement();

    if (myUnsavedValue == null || psiElement == null) {
      return;
    }

    String groovyString = convertToGradleString(myUnsavedValue, getVersionText());
    GrExpression newVersionPsi = GroovyPsiElementFactory.getInstance(psiElement.getProject()).createExpressionFromText(groovyString);

    GrExpression oldVersionPsi;
    if (myUseAssignment) {
      oldVersionPsi = ((GrAssignmentExpression)psiElement).getRValue();
    }
    else {
      oldVersionPsi = ((GrApplicationStatement)psiElement).getExpressionArguments()[0];
    }

    assert oldVersionPsi != null;
    oldVersionPsi.replace(newVersionPsi);
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

    GradlePropertiesDslElement parent = (GradlePropertiesDslElement)myParent;
    assert parent != null;
    GroovyPsiElement javaPsiElement = parent.create();
    assert javaPsiElement != null;
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(javaPsiElement.getProject());

    // Tries to create the new element close to targetCompatibility or sourceCompatibility, if neither of them exists, create it at the
    // end of the file.
    // Also, tries to copy the value from targetCompatibility or sourceCompatibility if possible to keep consistency.
    JavaVersionDslElement anchor = null;

    if (SOURCE_COMPATIBILITY_ATTRIBUTE_NAME.equals(myName)) {
      anchor = parent.getProperty(TARGET_COMPATIBILITY_ATTRIBUTE_NAME, JavaVersionDslElement.class);
    }
    else if (TARGET_COMPATIBILITY_ATTRIBUTE_NAME.equals(myName)) {
      anchor = parent.getProperty(SOURCE_COMPATIBILITY_ATTRIBUTE_NAME, JavaVersionDslElement.class);
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

    GrExpression newExpressionPsi;
    GroovyPsiElement valuePsi;
    if (myUseAssignment) {
      GrExpression expression = factory.createExpressionFromText(myName + " = " + anchorText);
      newExpressionPsi = (GrExpression)javaPsiElement.addBefore(expression, anchorPsiElement);
      valuePsi = ((GrAssignmentExpression)newExpressionPsi).getRValue();
    } else {
      GrExpression expression = factory.createExpressionFromText(myName + " " + anchorText);
      newExpressionPsi = (GrExpression)javaPsiElement.addBefore(expression, anchorPsiElement);
      valuePsi = ((GrApplicationStatement)newExpressionPsi).getExpressionArguments()[0];
    }

    if (valuePsi instanceof GrLiteral) {
      myInternalVersionElement = new GradleDslLiteral(parent, newExpressionPsi, myName, (GrLiteral)valuePsi);
    }
    else if (valuePsi instanceof GrReferenceExpression) {
      myInternalVersionElement = new GradleDslReference(parent, newExpressionPsi, myName, (GrReferenceExpression)valuePsi);
    }
    return getPsiElement();
  }

  @Override
  protected void delete() {
    GrExpression psiElement = getPsiElement();
    if (psiElement != null) {
      PsiElement parent = psiElement.getParent();
      psiElement.delete();
      deleteIfEmpty(parent);
    }
  }
}
