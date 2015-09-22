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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.List;
import java.util.Set;

import static com.android.tools.idea.gradle.dsl.parser.PsiElements.findClosableBlock;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.psi.util.PsiTreeUtil.*;

public class DependenciesModel extends GradleDslModel {
  @Nullable private PsiFile myPsiFile;
  @Nullable private GrClosableBlock myPsiElement;

  @NotNull private final List<ExternalDependencyModel> myExternalDependencies = Lists.newArrayList();
  @NotNull private final List<ModuleDependencyModel> myModuleDependencies = Lists.newArrayList();

  @NotNull private final Set<NewExternalDependency> myNewExternalDependencies = Sets.newLinkedHashSet();

  DependenciesModel(@NotNull GradleDslModel parent) {
    super(parent);
  }

  @Override
  protected void apply() {
    for (ExternalDependencyModel dependency : myExternalDependencies) {
      dependency.applyChanges();
    }
    for (ModuleDependencyModel dependency : myModuleDependencies) {
      dependency.applyChanges();
    }
    applyNewExternalDependencies();
  }

  private void applyNewExternalDependencies() {
    for (NewExternalDependency newExternalDependency : myNewExternalDependencies) {
      apply(newExternalDependency);
    }
    myNewExternalDependencies.clear();
  }

  private void apply(@NotNull NewExternalDependency dependency) {
    assert myPsiFile != null;

    String configurationName = dependency.configurationName;
    String compactNotation = dependency.getCompactNotation();

    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(myPsiFile.getProject());
    if (myPsiElement == null) {
      // There are no dependency blocks. Add one.
      // We need to add line separators, otherwise reformatting won't work.
      String lineSeparator = SystemProperties.getLineSeparator();
      String text = "dependencies {" + lineSeparator + configurationName + " '" + compactNotation + "'" + lineSeparator +  "}";
      GrExpression expression = factory.createExpressionFromText(text);

      PsiElement added = myPsiFile.add(expression);
      assert added instanceof GrMethodCallExpression;
      parse((GrMethodCallExpression)added);
      CodeStyleManager.getInstance(myPsiFile.getProject()).reformat(myPsiElement);
      return;
    }

    GrStatement statement = factory.createStatementFromText(configurationName + " '" + compactNotation + "'");
    GrStatement added = myPsiElement.addStatementBefore(statement, null);
    assert added instanceof GrApplicationStatement;
    parseExternalOrModuleDependency((GrApplicationStatement)added);
    CodeStyleManager.getInstance(myPsiFile.getProject()).reformat(added);
  }

  @NotNull
  public ImmutableList<ExternalDependencyModel> getExternalDependencies() {
    return ImmutableList.copyOf(myExternalDependencies);
  }

  @NotNull
  public ImmutableList<ModuleDependencyModel> getModuleDependencies() {
    return ImmutableList.copyOf(myModuleDependencies);
  }

  public void add(@NotNull NewExternalDependency dependency) {
    myNewExternalDependencies.add(dependency);
    setModified(true);
  }

  boolean parse(@NotNull GrMethodCallExpression methodCallExpression) {
    setPsiElement(findClosableBlock(methodCallExpression, "dependencies"));
    if (myPsiElement == null) {
      return false;
    }
    GrMethodCall[] methodCalls = getChildrenOfType(myPsiElement, GrMethodCall.class);
    if (methodCalls == null) {
      return false;
    }
    for (GrMethodCall methodCall : methodCalls) {
      if (methodCall instanceof GrMethodCallExpression) {
        parseExternalDependency((GrMethodCallExpression)methodCall);
        continue;
      }
      if (methodCall instanceof GrApplicationStatement) {
        parseExternalOrModuleDependency((GrApplicationStatement)methodCall);
      }
    }
    return true;
  }

  private void parseExternalDependency(@NotNull GrMethodCallExpression expression) {
    GrReferenceExpression configurationNameExpression = getChildOfType(expression, GrReferenceExpression.class);
    if (configurationNameExpression == null) {
      return;
    }

    String configurationName = configurationNameExpression.getText();
    if (isEmpty(configurationName)) {
      return;
    }

    GrArgumentList argumentList = getNextSiblingOfType(configurationNameExpression, GrArgumentList.class);
    if (argumentList == null) {
      return;
    }

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
        ExternalDependencyModel dependency = ExternalDependencyModel.withMapNotation(this, configurationName, namedArgs);
        if (dependency != null) {
          myExternalDependencies.add(dependency);
        }
      }
    }
  }

  private void parseExternalOrModuleDependency(@NotNull GrApplicationStatement statement) {
    GrReferenceExpression configurationNameExpression = getChildOfType(statement, GrReferenceExpression.class);
    if (configurationNameExpression == null) {
      return;
    }

    String configurationName = configurationNameExpression.getText();
    if (isEmpty(configurationName)) {
      return;
    }

    GrCommandArgumentList argumentList = getNextSiblingOfType(configurationNameExpression, GrCommandArgumentList.class);
    if (argumentList == null) {
      return;
    }

    GroovyPsiElement[] arguments = argumentList.getAllArguments();
    int argumentCount = arguments.length;
    if (argumentCount == 0) {
      return;
    }

    GroovyPsiElement first = arguments[0];
    if (first instanceof GrLiteral) {
      // "Compact" notation
      for (GroovyPsiElement argument : arguments) {
        if (argument instanceof GrLiteral) {
          GrLiteral literal = (GrLiteral)argument;
          ExternalDependencyModel dependency = ExternalDependencyModel.withCompactNotation(this, configurationName, literal);
          if (dependency != null) {
            myExternalDependencies.add(dependency);
          }
        }
      }
      return;
    }

    if (first instanceof GrNamedArgument) {
      // "Map" notation
      List<GrNamedArgument> namedArguments = Lists.newArrayList();
      for (GroovyPsiElement argument : arguments) {
        if (argument instanceof GrNamedArgument) {
          namedArguments.add((GrNamedArgument)argument);
        }
      }
      if (namedArguments.isEmpty()) {
        return;
      }
      GrNamedArgument[] namedArgumentArray = namedArguments.toArray(new GrNamedArgument[namedArguments.size()]);
      ExternalDependencyModel dependency = ExternalDependencyModel.withMapNotation(this, configurationName, namedArgumentArray);
      if (dependency != null) {
        myExternalDependencies.add(dependency);
      }
      return;

    }

    if (first instanceof GrMethodCallExpression) {
      GrMethodCallExpression expression = (GrMethodCallExpression)first;
      GrReferenceExpression referenceExpression = getChildOfType(expression, GrReferenceExpression.class);
      if (referenceExpression == null) {
        return;
      }
      String referenceName = referenceExpression.getText();
      if ("project".equals(referenceName)) {
        parseModuleDependency(configurationName, (GrMethodCallExpression)first);
      }
    }
  }

  private void parseModuleDependency(@NotNull String configurationName, @NotNull GrMethodCallExpression expression) {
    GrArgumentList argumentList = expression.getArgumentList();
    GroovyPsiElement[] arguments = argumentList.getAllArguments();
    if (arguments.length == 1 && arguments[0] instanceof GrLiteral) {
      ModuleDependencyModel moduleDependency = ModuleDependencyModel.withCompactNotation(this, configurationName, (GrLiteral)arguments[0]);
      if (moduleDependency != null) {
        myModuleDependencies.add(moduleDependency);
      }
      return;
    }
    ModuleDependencyModel moduleDependency = ModuleDependencyModel.withMapNotation(this, configurationName, argumentList);
    if (moduleDependency != null) {
      myModuleDependencies.add(moduleDependency);
    }
  }

  @Nullable
  public GrClosableBlock getPsiElement() {
    return myPsiElement;
  }

  private void setPsiElement(@Nullable GrClosableBlock psiElement) {
    myPsiElement = psiElement;
  }

  void setPsiFile(@Nullable PsiFile psiFile) {
    myPsiFile = psiFile;
  }
}
