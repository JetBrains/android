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

import com.android.tools.idea.gradle.dsl.parser.GradleDslElement;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static com.android.tools.idea.gradle.dsl.parser.GradleLanguageLevel.convertToGradleString;
import static com.android.tools.idea.gradle.dsl.parser.GradleLanguageLevel.parseFromGradleString;

public class JavaVersionElement implements GradleDslElement {
  @NotNull private GrExpression myPsiElement;
  @NotNull private String text; // Cached text of the element

  public JavaVersionElement(@NotNull GrExpression psiElement) {
    myPsiElement = psiElement;
    text = myPsiElement.getText();
  }

  @NotNull
  public GrExpression getPsiElement() {
    return myPsiElement;
  }

  @Nullable
  public LanguageLevel getVersion() {
    return parseFromGradleString(text);
  }

  /**
   * Sets new Java version while keeping the original version format.
   */
  public void setVersion(@NotNull LanguageLevel languageLevel) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    String groovyString = convertToGradleString(languageLevel, text);
    GrExpression expression = GroovyPsiElementFactory.getInstance(myPsiElement.getProject()).createExpressionFromText(groovyString);
    myPsiElement = (GrExpression)myPsiElement.replace(expression);
    text = myPsiElement.getText();
  }
}
