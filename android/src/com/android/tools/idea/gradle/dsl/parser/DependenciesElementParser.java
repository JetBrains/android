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

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collection;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.psi.util.PsiTreeUtil.*;

/**
 * Parses the "dependencies" block in a build.gradle file.
 */
class DependenciesElementParser implements GradleDslElementParser {
  @Override
  public boolean parse(@NotNull GroovyPsiElement e, @NotNull GradleBuildFile buildFile) {
    if (e instanceof GrMethodCallExpression) {
      GrMethodCallExpression expression = (GrMethodCallExpression)e;
      GrReferenceExpression childExpression = findChildOfType(expression, GrReferenceExpression.class);
      if (childExpression != null && "dependencies".equals(childExpression.getText())) {
        GrArgumentList argumentList = getNextSiblingOfType(childExpression, GrArgumentList.class);
        if (argumentList != null && argumentList.getAllArguments().length == 0) {
          GrClosableBlock closableBlock = getNextSiblingOfType(argumentList, GrClosableBlock.class);
          if (closableBlock != null) {
            parse(closableBlock, buildFile);
            return true;
          }
        }
      }
    }
    return false;
  }

  private static void parse(@NotNull GrClosableBlock closure, @NotNull GradleBuildFile buildFile) {
    DependenciesElement dependencies = new DependenciesElement(closure);
    Collection<GrApplicationStatement> statements = findChildrenOfType(closure, GrApplicationStatement.class);
    for (GrApplicationStatement statement : statements) {
      List<DependencyElement> dependencyList = parseDependencies(statement);
      dependencies.add(dependencyList);
    }
    buildFile.add(dependencies);
  }

  @NotNull
  private static List<DependencyElement> parseDependencies(@NotNull GrApplicationStatement statement) {
    List<DependencyElement> dependencies = Lists.newArrayList();

    GrReferenceExpression configurationNameExpression = getChildOfType(statement, GrReferenceExpression.class);
    if (configurationNameExpression != null) {
      String configurationName = configurationNameExpression.getText();
      if (isNotEmpty(configurationName)) {
        GrCommandArgumentList argumentList = getNextSiblingOfType(configurationNameExpression, GrCommandArgumentList.class);
        if (argumentList != null) {
          GroovyPsiElement[] arguments = argumentList.getAllArguments();
          int argumentCount = arguments.length;
          if (argumentCount > 0) {
            GroovyPsiElement argument = arguments[0];
            if (argument instanceof GrLiteral) {
              // "Compact" notation
              dependencies.addAll(parseExternalDependenciesWithCompactNotation(configurationName, arguments));
            }
            else if (argument instanceof GrNamedArgument) {
              // "Map" notation
              dependencies.addAll(parseExternalDependenciesWithMapNotation(configurationName, arguments));
            }
          }
        }
      }
    }
    return dependencies;
  }

  @NotNull
  private static List<DependencyElement> parseExternalDependenciesWithCompactNotation(@NotNull String configurationName,
                                                                                      @NotNull GroovyPsiElement[] arguments) {
    List<DependencyElement> dependencies = Lists.newArrayList();
    for (GroovyPsiElement argument : arguments) {
      if (argument instanceof GrLiteral) {
        GrLiteral literal = (GrLiteral)argument;
        DependencyElement dependency = ExternalDependencyElement.withCompactNotation(configurationName, literal);
        if (dependency != null) {
          dependencies.add(dependency);
        }
      }
    }
    return dependencies;
  }

  @NotNull
  private static List<DependencyElement> parseExternalDependenciesWithMapNotation(@NotNull String configurationName,
                                                                                  @NotNull GroovyPsiElement[] arguments) {
    List<DependencyElement> dependencies = Lists.newArrayList();
    List<GrNamedArgument> namedArguments = Lists.newArrayList();
    for (GroovyPsiElement argument : arguments) {
      if (argument instanceof GrNamedArgument) {
        namedArguments.add((GrNamedArgument)argument);
      }
    }
    DependencyElement dependency = ExternalDependencyElement.withMapNotation(configurationName, namedArguments);
    if (dependency != null) {
      dependencies.add(dependency);
    }
    return dependencies;
  }
}
