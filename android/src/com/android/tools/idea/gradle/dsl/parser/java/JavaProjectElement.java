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

import com.android.tools.idea.gradle.dsl.parser.OldGradleDslElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

/**
 * Holds the data in addition to the project element, which added by Java plugin
 */
public class JavaProjectElement implements OldGradleDslElement {
  public static String SOURCE_COMPATIBILITY_FIELD = "sourceCompatibility";
  public static String TARGET_COMPATIBILITY_FIELD = "targetCompatibility";
  @NotNull private GroovyPsiElement myPsiElement;

  @Nullable private JavaVersionElement mySourceCompatibility;
  @Nullable private JavaVersionElement myTargetCompatibility;

  public JavaProjectElement(@NotNull GroovyPsiElement psiElement) {
    myPsiElement = psiElement;
  }

  @Nullable
  public JavaVersionElement getSourceCompatibility() {
    return mySourceCompatibility;
  }

  public void setSourceCompatibility(@NotNull JavaVersionElement sourceCompatibility) {
    mySourceCompatibility = sourceCompatibility;
  }

  @NotNull
  public JavaVersionElement addSourceCompatibility() {
    if (mySourceCompatibility == null) {
      mySourceCompatibility = addLanguageLevel(SOURCE_COMPATIBILITY_FIELD);
    }
    return mySourceCompatibility;
  }

  @Nullable
  public JavaVersionElement getTargetCompatibility() {
    return myTargetCompatibility;
  }

  public void setTargetCompatibility(@NotNull JavaVersionElement targetCompatibility) {
    myTargetCompatibility = targetCompatibility;
  }

  @NotNull
  public JavaVersionElement addTargetCompatibility() {
    if (myTargetCompatibility == null) {
      myTargetCompatibility = addLanguageLevel(TARGET_COMPATIBILITY_FIELD);
    }
    return myTargetCompatibility;
  }

  @NotNull
  private JavaVersionElement addLanguageLevel(@NotNull String type) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myPsiElement.getProject());

    // Tries to create the new element close to targetCompatibility or sourceCompatibility, if neither of them exists, create it in the
    // end of the file.
    // Also, tries to copy the value from targetCompatibility or sourceCompatibility if possible to keep consistency.
    PsiElement anchor;
    GrExpression value;
    if (mySourceCompatibility != null) {
      anchor = mySourceCompatibility.getPsiElement();
      value = mySourceCompatibility.getPsiElement();
    }
    else if (myTargetCompatibility != null) {
      anchor = myTargetCompatibility.getPsiElement();
      value = myTargetCompatibility.getPsiElement();
    } else {
      anchor = myPsiElement.getLastChild();
      value = factory.createLiteralFromValue("1.6");
    }

    GrAssignmentExpression expression = (GrAssignmentExpression)factory.createExpressionFromText(type + " = " + value.getText());
    GrAssignmentExpression added = (GrAssignmentExpression)myPsiElement.addAfter(expression, anchor.getParent());

    assert added.getRValue() != null;
    return new JavaVersionElement(added.getRValue());
  }
}
