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

import com.android.tools.idea.gradle.dsl.model.java.JavaModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import java.util.Collection;
import java.util.Collections;

import static com.android.tools.idea.gradle.dsl.parser.java.LanguageLevelUtil.convertToGradleString;
import static com.android.tools.idea.gradle.dsl.parser.java.LanguageLevelUtil.parseFromGradleString;

public class JavaVersionDslElement extends GradleDslElement {
  @Nullable private String myText; // Cached myText of the element
  @Nullable private LanguageLevel myUnsavedValue;

  public JavaVersionDslElement(@Nullable GradleDslElement parent, @Nullable GrExpression psiElement, @NotNull String name) {
    super(parent, psiElement, name);
    if (psiElement != null) {
      myText = psiElement.getText();
    }
  }

  public JavaVersionDslElement(@Nullable GradleDslElement parent, @NotNull String name) {
    this(parent, null, name);
  }

  @Override
  public void setPsiElement(@Nullable GroovyPsiElement psiElement) {
    super.setPsiElement(psiElement);
    if (psiElement != null) {
      myText = psiElement.getText();
    }
  }

  @Override
  public GrExpression getPsiElement() {
    return (GrExpression)super.getPsiElement();
  }

  @NotNull
  @Override
  protected Collection<GradleDslElement> getChildren() {
    return Collections.emptyList();
  }

  @Nullable
  public LanguageLevel getVersion() {
    if (myText != null) {
      return parseFromGradleString(myText);
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
    if (myUnsavedValue == null || getPsiElement() == null) {
      return;
    }
    String groovyString = convertToGradleString(myUnsavedValue, myText);
    GrExpression expression = GroovyPsiElementFactory.getInstance(getPsiElement().getProject()).createExpressionFromText(groovyString);
    setPsiElement((GrExpression)getPsiElement().replace(expression));
  }

  @Override
  protected void reset() {
    myUnsavedValue = null;
  }

  @Override
  @Nullable
  public GroovyPsiElement create() {
    JavaDslElement java = (JavaDslElement)myParent;
    assert java != null;
    GroovyPsiElement javaPsiElement = java.create();
    assert javaPsiElement != null;
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(javaPsiElement.getProject());

    // Tries to create the new element close to targetCompatibility or sourceCompatibility, if neither of them exists, create it in the
    // end of the file.
    // Also, tries to copy the value from targetCompatibility or sourceCompatibility if possible to keep consistency.
    PsiElement anchor = null;
    GrExpression value = null;
    JavaVersionDslElement javaVersionElement = java.getProperty(JavaModel.SOURCE_COMPATIBILITY_FIELD, JavaVersionDslElement.class);
    if (javaVersionElement != null) {
      anchor = javaVersionElement.getPsiElement();
      value = javaVersionElement.getPsiElement();
    }
    if (anchor == null) {
      javaVersionElement = java.getProperty(JavaModel.TARGET_COMPATIBILITY_FIELD, JavaVersionDslElement.class);
      if (javaVersionElement != null) {
        anchor = javaVersionElement.getPsiElement();
        value = javaVersionElement.getPsiElement();
      }
    }
    if (anchor == null) {
      anchor = javaPsiElement.getLastChild();
      value = factory.createLiteralFromValue("1.6");
    }

    GrAssignmentExpression expression = (GrAssignmentExpression)factory.createExpressionFromText(myName + " = " + value.getText());
    GrAssignmentExpression added = (GrAssignmentExpression)javaPsiElement.addAfter(expression, anchor == null ? null : anchor.getParent());
    setPsiElement(added.getRValue());
    return getPsiElement();
  }

  @Override
  protected void delete() {
    PsiElement psiElement = getPsiElement();
    if (psiElement == null) {
      return;
    }
    if (psiElement.getParent() != null) {
      psiElement.getParent().delete();
    }
  }
}
