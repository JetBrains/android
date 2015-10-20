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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModel;
import com.android.tools.idea.gradle.dsl.parser.android.AndroidPsiElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorPsiElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsPsiElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtPsiElement;
import com.google.common.base.Splitter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.psi.util.PsiTreeUtil.*;

/**
 * Generic parser to parse .gradle files.
 *
 * <p>It parses any general application statements or assigned statements in the .gradle file directly and stores them as key value pairs
 * in the {@link GradleBuildModel}. For every closure block section like {@code android{}}, it will create block elements like
 * {@link AndroidModel}. See {@link #getElement(List, GradlePropertiesPsiElement)} for all the block elements currently supported
 * by this parser.
 */
public final class GradleDslParser {
  public static boolean parse(@NotNull GroovyPsiElement groovyPsiElement, @NotNull GradlePsiFile gradlePsiFile) {
    if (groovyPsiElement instanceof GrMethodCallExpression) {
      return parse((GrMethodCallExpression)groovyPsiElement, (GradlePropertiesPsiElement)gradlePsiFile);
    } else if (groovyPsiElement instanceof GrAssignmentExpression) {
      return parse((GrAssignmentExpression)groovyPsiElement, (GradlePropertiesPsiElement)gradlePsiFile);
    } else if (groovyPsiElement instanceof GrApplicationStatement) {
      return parse((GrApplicationStatement)groovyPsiElement, (GradlePropertiesPsiElement)gradlePsiFile);
    }
    return false;
  }

  private static boolean parse(@NotNull GrMethodCallExpression expression, @NotNull GradlePropertiesPsiElement gradlePsiElement) {
    GrReferenceExpression referenceExpression = findChildOfType(expression, GrReferenceExpression.class);
    if (referenceExpression == null) {
      return false;
    }

    GrClosableBlock[] closureArguments = expression.getClosureArguments();
    if (closureArguments.length == 0) {
      return false;
    }

    GrClosableBlock closableBlock = closureArguments[0];
    String blockName = referenceExpression.getText();
    if (isEmpty(blockName) ) {
      return false;
    }

    List<String> nameSegments = Splitter.on('.').splitToList(blockName);
    GradlePropertiesPsiElement blockElement = getElement(nameSegments, gradlePsiElement);
    if (blockElement == null) {
      return false;
    }

    blockElement.setGroovyPsiElement(closableBlock);
    parse(closableBlock, blockElement);
    return true;
  }

  private static void parse(@NotNull GrClosableBlock closure, @NotNull final GradlePropertiesPsiElement blockElement) {
    closure.acceptChildren(new GroovyElementVisitor() {
      @Override
      public void visitMethodCallExpression(GrMethodCallExpression methodCallExpression) {
        parse(methodCallExpression, blockElement);
      }

      @Override
      public void visitApplicationStatement(GrApplicationStatement applicationStatement) {
        parse(applicationStatement, blockElement);
      }

      @Override
      public void visitAssignmentExpression(GrAssignmentExpression expression) {
        parse(expression, blockElement);
      }
    });

  }

  private static boolean parse(@NotNull GrApplicationStatement statement, @NotNull GradlePropertiesPsiElement blockElement) {
    GrReferenceExpression referenceExpression = getChildOfType(statement, GrReferenceExpression.class);
    if (referenceExpression == null) {
      return false;
    }

    GrCommandArgumentList argumentList = getNextSiblingOfType(referenceExpression, GrCommandArgumentList.class);
    if (argumentList == null) {
      return false;
    }

    GroovyPsiElement[] arguments = argumentList.getAllArguments();
    if (arguments.length == 0) {
      return false;
    }

    String name = referenceExpression.getText();
    if (isEmpty(name)) {
      return false;
    }

    List<String> nameSegments = Splitter.on('.').splitToList(name);
    if (nameSegments.size() > 1) {
      GradlePropertiesPsiElement nestedElement = getElement(nameSegments.subList(0, nameSegments.size() - 1), blockElement);
      if (nestedElement != null) {
        blockElement = nestedElement;
      } else {
        return false;
      }
    }

    String propertyName = nameSegments.get(nameSegments.size() - 1);
    GradlePsiElement propertyElement = null;
    if (arguments.length == 1) {
      GroovyPsiElement element = arguments[0];
      if (element instanceof GrLiteral) { // ex: compileSdkVersion 23
        propertyElement = new GradlePsiLiteral(blockElement, argumentList, propertyName, (GrLiteral)element);
      }
      else if (element instanceof GrNamedArgument) { // ex: manifestPlaceholders activityLabel:"defaultName"
        GrNamedArgument namedArgument = (GrNamedArgument)element;
        propertyElement = new GradlePsiLiteralMap(blockElement, argumentList, propertyName, namedArgument);
      }
    }
    else {
      if (arguments[0] instanceof GrLiteral) { // ex: proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
        GrLiteral[] literals = new GrLiteral[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
          literals[i] = (GrLiteral)arguments[i];
        }
        propertyElement = new GradlePsiLiteralList(blockElement, argumentList, propertyName, literals);
      }
      else if (arguments[0] instanceof GrNamedArgument) {
        // ex: manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
        GrNamedArgument[] namedArguments = new GrNamedArgument[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
          namedArguments[i] = (GrNamedArgument)arguments[i];
        }
        propertyElement = new GradlePsiLiteralMap(blockElement, argumentList, propertyName, namedArguments);
      }
    }
    if (propertyElement == null) {
      return false;
    }

    blockElement.addPsiElement(propertyName, propertyElement);
    return true;
  }

  private static boolean parse(@NotNull GrAssignmentExpression assignment, @NotNull GradlePropertiesPsiElement blockElement) {
    PsiElement operationToken = assignment.getOperationToken();
    if (!operationToken.getText().equals("=")) {
      return false; // TODO: Add support for other operators like +=.
    }

    GrExpression left = assignment.getLValue();
    String name = left.getText();
    if (isEmpty(name)) {
      return false;
    }

    List<String> nameSegments = Splitter.on('.').splitToList(name);
    if (nameSegments.size() > 1) {
      GradlePropertiesPsiElement nestedElement = getElement(nameSegments.subList(0, nameSegments.size() - 1), blockElement);
      if (nestedElement != null) {
        blockElement = nestedElement;
      } else {
        return false;
      }
    }

    String propertyName = nameSegments.get(nameSegments.size() - 1);
    GradlePsiElement propertyElement = null;
    GrExpression right = assignment.getRValue();
    if (right instanceof GrLiteral) { // ex: compileSdkVersion = "android-23"
      propertyElement = new GradlePsiLiteral(blockElement, assignment, propertyName, (GrLiteral)right);
    }
    else if (right instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)right;
      if (listOrMap.isMap()) { // ex: manifestPlaceholders = [activityLabel1:"defaultName1", activityLabel2:"defaultName2"]
        propertyElement = new GradlePsiLiteralMap(blockElement, propertyName, listOrMap);
      }
      else { // ex: proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
        propertyElement = new GradlePsiLiteralList(blockElement, propertyName, listOrMap);
      }
    }
    if (propertyElement == null) {
      return false;
    }

    blockElement.setPsiElement(propertyName, propertyElement);
    return true;
  }

  private static GradlePropertiesPsiElement getElement(@NotNull List<String> qualifiedName,
                                                       @NotNull GradlePropertiesPsiElement parentElement) {
    GradlePropertiesPsiElement resultElement = parentElement;
    for (String nestedElementName : qualifiedName) {
      GradlePsiElement element = resultElement.getPropertyElement(nestedElementName);
      if (element == null) {
        GradlePropertiesPsiElement newElement;
        if (resultElement instanceof GradlePsiFile) {
          if (ExtPsiElement.NAME.equals(nestedElementName)) {
            newElement = new ExtPsiElement(resultElement);
          }
          else if (AndroidPsiElement.NAME.equals(nestedElementName)) {
            newElement = new AndroidPsiElement(resultElement);
          } else {
            return null;
          }
        }
        else if (resultElement instanceof AndroidPsiElement) {
          if ("defaultConfig".equals(nestedElementName)) {
            newElement = new ProductFlavorPsiElement(resultElement, nestedElementName);
          }
          else if (ProductFlavorsPsiElement.NAME.equals(nestedElementName)) {
            newElement = new ProductFlavorsPsiElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof ProductFlavorsPsiElement) {
          newElement = new ProductFlavorPsiElement(resultElement, nestedElementName);
        }
        else if (resultElement instanceof ProductFlavorPsiElement
                 && ("manifestPlaceholders".equals(nestedElementName) || "testInstrumentationRunnerArguments".equals(nestedElementName))){
          newElement = new GradlePsiLiteralMap(resultElement, nestedElementName);
        }
        else {
          return null;
        }
        resultElement.setPsiElement(nestedElementName, newElement);
        resultElement = newElement;
      }
      else if (element instanceof GradlePropertiesPsiElement) {
        resultElement = (GradlePropertiesPsiElement) element;
      }
      else {
        return null;
      }
    }
    return resultElement;
  }
}
