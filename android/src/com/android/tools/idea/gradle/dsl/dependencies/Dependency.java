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
package com.android.tools.idea.gradle.dsl.dependencies;

import com.android.tools.idea.gradle.dsl.dependencies.external.ExternalDependency;
import com.android.tools.idea.gradle.dsl.parser.GradleDslElement;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.psi.util.PsiTreeUtil.getChildOfType;

public abstract class Dependency extends GradleDslElement {
  @NotNull private final Dependencies myParent;

  @NotNull private final String myConfigurationName;

  protected Dependency(@NotNull Dependencies parent, @NotNull String configurationName) {
    super(parent);
    myParent = parent;
    myConfigurationName = configurationName;
  }

  @NotNull
  public String configurationName() {
    return myConfigurationName;
  }

  @NotNull
  protected Dependencies getParent() {
    return myParent;
  }

  @NotNull
  static List<Dependency> parse(@NotNull Dependencies parent, @NotNull GrMethodCall methodCall) {
    if (methodCall instanceof GrMethodCallExpression) {
      return parse(parent, (GrMethodCallExpression)methodCall);
    }
    if (methodCall instanceof GrApplicationStatement) {
      return parse(parent, (GrApplicationStatement)methodCall);
    }
    return Collections.emptyList();
  }

  @NotNull
  private static List<Dependency> parse(@NotNull Dependencies parent, @NotNull GrMethodCallExpression expression) {
    GrReferenceExpression configurationNameExpression = findValidConfigurationNameExpression(expression);
    if (configurationNameExpression == null) {
      return Collections.emptyList();
    }

    GrArgumentList argumentList = expression.getArgumentList();

    List<Dependency> dependencies = Lists.newArrayList();
    for (GroovyPsiElement arg : argumentList.getAllArguments()) {
      if (!(arg instanceof GrListOrMap)) {
        continue;
      }
      GrListOrMap listOrMap = (GrListOrMap)arg;
      if (!listOrMap.isMap()) {
        continue;
      }
      GrNamedArgument[] namedArgs = listOrMap.getNamedArguments();
      if (namedArgs.length > 0) {
        List<Dependency> parsed = ExternalDependency.parse(parent, configurationNameExpression, namedArgs);
        dependencies.addAll(parsed);
      }
    }
    return dependencies;
  }

  @NotNull
  private static List<Dependency> parse(@NotNull Dependencies parent, @NotNull GrApplicationStatement statement) {
    GrReferenceExpression configurationNameExpression = findValidConfigurationNameExpression(statement);
    if (configurationNameExpression == null) {
      return Collections.emptyList();
    }

    GrCommandArgumentList argumentList = statement.getArgumentList();

    List<Dependency> dependencies = ExternalDependency.parse(parent, configurationNameExpression, argumentList.getAllArguments());
    if (!dependencies.isEmpty()) {
      return dependencies;
    }
    GroovyPsiElement[] arguments = argumentList.getAllArguments();
    int argumentCount = arguments.length;
    if (argumentCount == 0) {
      return Collections.emptyList();
    }

    GroovyPsiElement first = arguments[0];
    if (first instanceof GrMethodCallExpression) {
      GrMethodCallExpression expression = (GrMethodCallExpression)first;
      GrReferenceExpression referenceExpression = getChildOfType(expression, GrReferenceExpression.class);
      if (referenceExpression == null) {
        return Collections.emptyList();
      }
      String referenceName = referenceExpression.getText();
      if ("project".equals(referenceName)) {
        Dependency dependency = parseModuleDependency(parent, configurationNameExpression.getText(), (GrMethodCallExpression)first);
        if (dependency != null) {
          return Collections.singletonList(dependency);
        }
      }
    }
    return Collections.emptyList();
  }

  @Nullable
  private static GrReferenceExpression findValidConfigurationNameExpression(@NotNull GrMethodCall methodCall) {
    GrReferenceExpression found = getChildOfType(methodCall, GrReferenceExpression.class);
    if (found != null && isNotEmpty(found.getText())) {
      return found;
    }
    return null;
  }

  @Nullable
  private static Dependency parseModuleDependency(@NotNull Dependencies parent, @NotNull String configurationName, @NotNull GrMethodCallExpression expression) {
    GrArgumentList argumentList = expression.getArgumentList();
    GroovyPsiElement[] arguments = argumentList.getAllArguments();
    if (arguments.length == 1 && arguments[0] instanceof GrLiteral) {
      ModuleDependency moduleDependency = ModuleDependency.withCompactNotation(parent, configurationName, (GrLiteral)arguments[0]);
      if (moduleDependency != null) {
        return moduleDependency;
      }
      return null;
    }
    ModuleDependency moduleDependency = ModuleDependency.withMapNotation(parent, configurationName, argumentList);
    if (moduleDependency != null) {
      return moduleDependency;
    }
    return null;
  }
}
