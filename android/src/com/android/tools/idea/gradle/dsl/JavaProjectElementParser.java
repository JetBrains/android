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
package com.android.tools.idea.gradle.dsl;

import com.android.tools.idea.gradle.dsl.parser.GradleDslElementParser;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;

import static com.android.tools.idea.gradle.dsl.JavaElement.SOURCE_COMPATIBILITY_FIELD;
import static com.android.tools.idea.gradle.dsl.JavaElement.TARGET_COMPATIBILITY_FIELD;

// TODO move it to GradleDslProperty parser?
public class JavaProjectElementParser implements GradleDslElementParser {
  @Override
  public boolean parse(@NotNull GroovyPsiElement psi, @NotNull GradleBuildModel buildModel) {
    JavaElement element = buildModel.java();
    if (element == null) {
      element = new JavaElement(buildModel);
      buildModel.setParsedElement(JavaElement.NAME, element);
      element.setPsiElement(buildModel.getPsiElement());
    }
    if (psi instanceof GrAssignmentExpression) {
      GrAssignmentExpression assignmentExpression = (GrAssignmentExpression)psi;
      String property = assignmentExpression.getLValue().getText();
      if (SOURCE_COMPATIBILITY_FIELD.equals(property) || TARGET_COMPATIBILITY_FIELD.equals(property)) {
        GrExpression rValue = assignmentExpression.getRValue();
        if (rValue != null) {
          element.setParsedElement(property, new JavaVersionElement(element, rValue, property));
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
