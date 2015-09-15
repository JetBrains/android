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

import com.android.tools.idea.gradle.dsl.android.AndroidElement;
import com.android.tools.idea.gradle.dsl.android.ProductFlavorElement;
import com.android.tools.idea.gradle.dsl.android.ProductFlavorsElement;
import com.android.tools.idea.gradle.dsl.ext.ExtModel;
import com.google.common.base.Splitter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
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
 * {@link AndroidElement}. See {@link #getElement(List, GradleDslPropertiesElement)} for all the block elements currently supported
 * by this parser.
 */
public class GradleDslParser implements GradleDslElementParser {
  @Override
  public boolean parse(@NotNull GroovyPsiElement e, @NotNull GradleBuildModel buildModel) {
    if (e instanceof GrMethodCallExpression) {
      return parse((GrMethodCallExpression)e, (GradleDslPropertiesElement)buildModel);
    } else if (e instanceof GrAssignmentExpression) {
      return parse((GrAssignmentExpression)e, (GradleDslPropertiesElement)buildModel);
    } else if (e instanceof GrApplicationStatement) {
      return parse((GrApplicationStatement)e, (GradleDslPropertiesElement)buildModel);
    }
    return false;
  }

  private static boolean parse(@NotNull GrMethodCallExpression expression, @NotNull GradleDslPropertiesElement androidElement) {
    GrReferenceExpression referenceExpression = findChildOfType(expression, GrReferenceExpression.class);
    if (referenceExpression == null) {
      return false;
    }

    GrArgumentList argumentList = getNextSiblingOfType(referenceExpression, GrArgumentList.class);
    if (argumentList == null || argumentList.getAllArguments().length > 0) {
      return false;
    }

    GrClosableBlock closableBlock = getNextSiblingOfType(argumentList, GrClosableBlock.class);
    if (closableBlock == null) {
      return false;
    }

    String blockName = referenceExpression.getText();
    if (isEmpty(blockName) ) {
      return false;
    }

    List<String> nameSegments = Splitter.on('.').splitToList(blockName);
    GradleDslPropertiesElement blockElement = getElement(nameSegments, androidElement);
    if (blockElement == null) {
      return false;
    }

    blockElement.setBlockElement(closableBlock);
    parse(closableBlock, blockElement);
    return true;
  }

  private static void parse(@NotNull GrClosableBlock closure, @NotNull final GradleDslPropertiesElement blockElement) {
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

  private static boolean parse(@NotNull GrApplicationStatement statement, @NotNull GradleDslPropertiesElement blockElement) {
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
      GradleDslPropertiesElement nestedElement = getElement(nameSegments.subList(0, nameSegments.size() - 1), blockElement);
      if (nestedElement != null) {
        blockElement = nestedElement;
      } else {
        return false;
      }
    }

    String propertyName = nameSegments.get(nameSegments.size() - 1);
    GradleDslElement propertyElement = null;
    if (arguments.length == 1) {
      GroovyPsiElement element = arguments[0];
      if (element instanceof GrLiteral) { // ex: compileSdkVersion 23
        propertyElement = new LiteralElement(blockElement, propertyName, (GrLiteral)element);
      }
      else if (element instanceof GrNamedArgument) { // ex: manifestPlaceholders activityLabel:"defaultName"
        GrNamedArgument namedArgument = (GrNamedArgument)element;
        propertyElement = new MapElement(blockElement, propertyName, namedArgument);
      }
    }
    else {
      if (arguments[0] instanceof GrLiteral) { // ex: proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
        GrLiteral[] literals = new GrLiteral[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
          literals[i] = (GrLiteral)arguments[i];
        }
        propertyElement = new ListElement(blockElement, propertyName, literals);
      }
      else if (arguments[0] instanceof GrNamedArgument) {
        // ex: manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
        GrNamedArgument[] namedArguments = new GrNamedArgument[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
          namedArguments[i] = (GrNamedArgument)arguments[i];
        }
        propertyElement = new MapElement(blockElement, propertyName, namedArguments);
      }
    }
    if (propertyElement == null) {
      return false;
    }

    blockElement.addProperty(propertyName, propertyElement);
    return true;
  }

  private static boolean parse(@NotNull GrAssignmentExpression assignment, @NotNull GradleDslPropertiesElement blockElement) {
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
      GradleDslPropertiesElement nestedElement = getElement(nameSegments.subList(0, nameSegments.size() - 1), blockElement);
      if (nestedElement != null) {
        blockElement = nestedElement;
      } else {
        return false;
      }
    }

    String propertyName = nameSegments.get(nameSegments.size() - 1);
    GradleDslElement propertyElement = null;
    GrExpression right = assignment.getRValue();
    if (right instanceof GrLiteral) { // ex: compileSdkVersion = "android-23"
      propertyElement = new LiteralElement(blockElement, propertyName, (GrLiteral)right);
    }
    else if (right instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)right;
      if (listOrMap.isMap()) { // ex: manifestPlaceholders = [activityLabel1:"defaultName1", activityLabel2:"defaultName2"]
        propertyElement = new MapElement(blockElement, propertyName, listOrMap);
      }
      else { // ex: proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
        propertyElement = new ListElement(blockElement, propertyName, listOrMap);
      }
    }
    if (propertyElement == null) {
      return false;
    }

    blockElement.setProperty(propertyName, propertyElement);
    return true;
  }

  private static GradleDslPropertiesElement getElement(@NotNull List<String> qualifiedName,
                                                       @NotNull GradleDslPropertiesElement parentElement) {
    GradleDslPropertiesElement resultElement = parentElement;
    for (String nestedElementName : qualifiedName) {
      GradleDslElement element = resultElement.getPropertyElement(nestedElementName);
      if (element == null) {
        GradleDslPropertiesElement newElement;
        if (resultElement instanceof GradleBuildModel) {
          if (ExtModel.NAME.equals(nestedElementName)) {
            newElement = new ExtModel(resultElement);
          }
          else if (AndroidElement.NAME.equals(nestedElementName)) {
            newElement = new AndroidElement(resultElement);
          } else {
            return null;
          }
        }
        else if (resultElement instanceof AndroidElement) {
          if ("defaultConfig".equals(nestedElementName)) {
            newElement = new ProductFlavorElement(resultElement, nestedElementName);
          }
          else if (ProductFlavorsElement.NAME.equals(nestedElementName)) {
            newElement = new ProductFlavorsElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof ProductFlavorsElement) {
          newElement = new ProductFlavorElement(resultElement, nestedElementName);
        }
        else if (resultElement instanceof ProductFlavorElement
                 && ("manifestPlaceholders".equals(nestedElementName) || "testInstrumentationRunnerArguments".equals(nestedElementName))){
          newElement = new MapElement(resultElement, nestedElementName);
        }
        else {
          return null;
        }
        resultElement.setProperty(nestedElementName, newElement);
        resultElement = newElement;
      }
      else if (element instanceof GradleDslPropertiesElement) {
        resultElement = (GradleDslPropertiesElement) element;
      }
      else {
        return null;
      }
    }
    return resultElement;
  }
}
