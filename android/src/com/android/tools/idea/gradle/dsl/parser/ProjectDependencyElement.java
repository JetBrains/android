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
package com.android.tools.idea.gradle.dsl.parser;

import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteralContainer;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.getPathSegments;

/**
 * A Gradle project dependency. There are two notations supported for declaring a project dependency:
 * <pre> configurationName project("gradlePath") </pre> and
 * <pre> configurationName project(path: "gradlePath", configuration: "configuration") </pre>
 */
public class ProjectDependencyElement implements DependencyElement {
  @NotNull String myConfigurationName;
  @NotNull GrLiteralContainer myGradlePathElement;
  /**
   * Not null if the dependency uses map notation and configuration argument is specified. e.g.
   * compile project(path: "gradlePath", configuration: "configuration")
   */
  @Nullable GrLiteralContainer myTargetConfigurationElement;

  /**
   * Creates project dependency from the literal form, e.g. compile project("gradlePath");
   */
  @Nullable
  public static ProjectDependencyElement withCompactNotation(@NotNull String configurationName,
                                                             @NotNull GrLiteralContainer projectNamePsiElement) {
    if (projectNamePsiElement.getValue() == null) {
      return null;
    }
    return new ProjectDependencyElement(configurationName, projectNamePsiElement, null);
  }

  /**
   * Creates project dependency from map notation, e.g.
   * compile project(path: "gradlePath", configuration: "xxx") or
   * compile project(path: "gradlePath")
   */
  @Nullable
  public static ProjectDependencyElement withMapNotation(@NotNull String configurationName, @NotNull GrArgumentList argumentList) {
    GrLiteral projectNamePsiElement = findNamedArgumentLiteralValue(argumentList, "path");
    if (projectNamePsiElement == null || projectNamePsiElement.getValue() == null) {
      return null;
    }
    return new ProjectDependencyElement(configurationName, projectNamePsiElement,
                                        findNamedArgumentLiteralValue(argumentList, "configuration"));
  }

  @Nullable
  private static GrLiteral findNamedArgumentLiteralValue(@NotNull GrArgumentList argumentList, @NotNull String label) {
    GrNamedArgument namedArgument = argumentList.findNamedArgument(label);
    if (namedArgument == null) {
      return null;
    }
    if (namedArgument.getExpression() instanceof GrLiteral) {
      return (GrLiteral)namedArgument.getExpression();
    }
    return null;
  }

  private ProjectDependencyElement(@NotNull String configurationName,
                                   @NotNull GrLiteralContainer projectNamePsiElement,
                                   @Nullable GrLiteralContainer targetConfigurationElement) {
    myConfigurationName = configurationName;
    myGradlePathElement = projectNamePsiElement;
    myTargetConfigurationElement = targetConfigurationElement;
  }

  @NotNull
  public String getConfigurationName() {
    return myConfigurationName;
  }

  @NotNull
  public String getGradlePath() {
    Object literalValue = myGradlePathElement.getValue();
    assert literalValue != null;
    return literalValue.toString();
  }

  @NotNull
  public String getName() {
    List<String> pathSegment = getPathSegments(getGradlePath());
    return pathSegment.get(pathSegment.size() - 1);
  }

  @Nullable
  public String getTargetConfigurationName() {
    if (myTargetConfigurationElement == null) {
      return null;
    }
    Object value = myTargetConfigurationElement.getValue();
    if (value == null) {
      return null;
    }
    return value.toString();
  }

  public void setName(@NotNull String newName) {
    ApplicationManager.getApplication().assertWriteAccessAllowed();
    myGradlePathElement = myGradlePathElement.updateText(myGradlePathElement.getText().replace(getName(), newName));
  }
}
