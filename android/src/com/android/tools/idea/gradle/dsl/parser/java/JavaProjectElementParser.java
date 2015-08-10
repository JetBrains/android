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

import com.android.tools.idea.gradle.dsl.parser.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.parser.GradleDslElementParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

public class JavaProjectElementParser implements GradleDslElementParser {
  @Override
  public boolean parse(@NotNull GroovyPsiElement psi, @NotNull GradleBuildModel buildModel) {
    JavaProjectElement element = buildModel.getExtendedDslElement(JavaProjectElement.class);
    if (element == null) {
      // TODO: only create element when it sees apply plugin "java"
      GroovyFile psiFile = buildModel.getPsiFile();
      if (psiFile == null) {
        return false;
      }
      element = new JavaProjectElement(psiFile);
      buildModel.addExtendedDslElement(element);
    }
    if (psi instanceof GrAssignmentExpression) {
      GrAssignmentExpression assignmentExpression = (GrAssignmentExpression)psi;
      String lValueText = assignmentExpression.getLValue().getText();
      if (lValueText.equals(JavaProjectElement.SOURCE_COMPATIBILITY_FIELD)) {
        GrExpression rValue = assignmentExpression.getRValue();
        if (rValue != null) {
          element.setSourceCompatibility(new JavaVersionElement(rValue));
        }
        return true;
      }
      else if (lValueText.equals(JavaProjectElement.TARGET_COMPATIBILITY_FIELD)) {
        GrExpression rValue = assignmentExpression.getRValue();
        if (rValue != null) {
          element.setTargetCompatibility(new JavaVersionElement(rValue));
        }
        return true;
      }
    }
    // TODO: handle following case:
    // tasks.withType(JavaCompile) {
    // sourceCompatibility = "1.6"
    // targetCompatibility = "1.6"
    return false;
  }
}
