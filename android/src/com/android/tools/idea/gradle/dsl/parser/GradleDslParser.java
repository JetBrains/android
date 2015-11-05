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
import com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.google.common.base.Splitter;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
 * {@link AndroidModel}. See {@link #getBlockElement(List, GradlePropertiesDslElement)} for all the block elements currently supported
 * by this parser.
 */
public final class GradleDslParser {
  public static boolean parse(@NotNull GroovyPsiElement psiElement, @NotNull GradleDslFile gradleDslFile) {
    if (psiElement instanceof GrMethodCallExpression) {
      return parse((GrMethodCallExpression)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    } else if (psiElement instanceof GrAssignmentExpression) {
      return parse((GrAssignmentExpression)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    } else if (psiElement instanceof GrApplicationStatement) {
      return parse((GrApplicationStatement)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    return false;
  }

  private static boolean parse(@NotNull GrMethodCallExpression expression, @NotNull GradlePropertiesDslElement dslElement) {
    GrReferenceExpression referenceExpression = findChildOfType(expression, GrReferenceExpression.class);
    if (referenceExpression == null) {
      return false;
    }

    String name = referenceExpression.getText();
    if (isEmpty(name) ) {
      return false;
    }

    GrArgumentList argumentList = expression.getArgumentList();
    if (argumentList.getAllArguments().length > 0) {
      dslElement.setDslElement(name, new GradleDslMethodCall(dslElement, expression, name, argumentList));
      // This element is a method call with arguments. This element may also contain a closure along with it, but as of now we do not have
      // a use case to understand closure associated with a method call with arguments. So, just process the method arguments and return.
      // ex: compile("dependency") {}
      return true;
    }

    GrClosableBlock[] closureArguments = expression.getClosureArguments();
    if (closureArguments.length == 0) {
      return false;
    }

    // Now this element is pure block element, i.e a method call with no argument but just a closure argument. So, here just process the
    // closure and treat it as a block element.
    // ex: android {}
    GrClosableBlock closableBlock = closureArguments[0];
    List<String> nameSegments = Splitter.on('.').splitToList(name);
    GradlePropertiesDslElement blockElement = getBlockElement(nameSegments, dslElement);
    if (blockElement == null) {
      return false;
    }

    blockElement.setPsiElement(closableBlock);
    parse(closableBlock, blockElement);
    return true;
  }

  private static void parse(@NotNull GrClosableBlock closure, @NotNull final GradlePropertiesDslElement blockElement) {
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

  private static boolean parse(@NotNull GrApplicationStatement statement, @NotNull GradlePropertiesDslElement blockElement) {
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
      GradlePropertiesDslElement nestedElement = getBlockElement(nameSegments.subList(0, nameSegments.size() - 1), blockElement);
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
      if (element instanceof GrExpression) {
        propertyElement = getPropertyElement(blockElement, argumentList, propertyName, (GrExpression)element);
      }
      else if (element instanceof GrNamedArgument) { // ex: manifestPlaceholders activityLabel:"defaultName"
        propertyElement = new GradleDslLiteralMap(blockElement, argumentList, propertyName, (GrNamedArgument)element);
      }
    }
    else {
      if (arguments[0] instanceof GrLiteral) { // ex: proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
        GrLiteral[] literals = new GrLiteral[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
          literals[i] = (GrLiteral)arguments[i];
        }
        propertyElement = new GradleDslLiteralList(blockElement, argumentList, propertyName, literals);
      }
      else if (arguments[0] instanceof GrNamedArgument) {
        // ex: manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
        GrNamedArgument[] namedArguments = new GrNamedArgument[arguments.length];
        for (int i = 0; i < arguments.length; i++) {
          namedArguments[i] = (GrNamedArgument)arguments[i];
        }
        propertyElement = new GradleDslLiteralMap(blockElement, argumentList, propertyName, namedArguments);
      }
    }
    if (propertyElement == null) {
      return false;
    }

    blockElement.addDslElement(propertyName, propertyElement);
    return true;
  }

  private static boolean parse(@NotNull GrAssignmentExpression assignment, @NotNull GradlePropertiesDslElement blockElement) {
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
      GradlePropertiesDslElement nestedElement = getBlockElement(nameSegments.subList(0, nameSegments.size() - 1), blockElement);
      if (nestedElement != null) {
        blockElement = nestedElement;
      } else {
        return false;
      }
    }

    String propertyName = nameSegments.get(nameSegments.size() - 1);
    GrExpression right = assignment.getRValue();
    if (right == null) {
      return false;
    }

    GradleDslElement propertyElement = getPropertyElement(blockElement, assignment, propertyName, right);
    if (propertyElement == null) {
      return false;
    }

    blockElement.setDslElement(propertyName, propertyElement);
    return true;
  }

  @Nullable
  private static GradleDslElement getPropertyElement(@NotNull GradlePropertiesDslElement parentElement,
                                                     @NotNull GroovyPsiElement psiElement,
                                                     @NotNull String propertyName,
                                                     @NotNull GrExpression propertyExpression) {
    if (propertyExpression instanceof GrLiteral) { // ex: compileSdkVersion 23 or compileSdkVersion = "android-23"
      return new GradleDslLiteral(parentElement, psiElement, propertyName, (GrLiteral)propertyExpression);
    }

    if (propertyExpression instanceof GrReferenceExpression) { // ex: compileSdkVersion SDK_VERSION or sourceCompatibility = VERSION_1_5
      return new GradleDslReference(parentElement, psiElement, propertyName, (GrReferenceExpression)propertyExpression);
    }

    if (propertyExpression instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)propertyExpression;
      if (listOrMap.isMap()) { // ex: manifestPlaceholders = [activityLabel1:"defaultName1", activityLabel2:"defaultName2"]
        return new GradleDslLiteralMap(parentElement, propertyName, listOrMap);
      }
      // ex: proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
      return new GradleDslLiteralList(parentElement, propertyName, listOrMap);
    }

    if (propertyExpression instanceof  GrMethodCallExpression) { // ex: compile project("someProject")
      GrMethodCallExpression methodCall = (GrMethodCallExpression)propertyExpression;
      GrReferenceExpression callReferenceExpression = getChildOfType(methodCall, GrReferenceExpression.class);
      if (callReferenceExpression != null) {
        String referenceName = callReferenceExpression.getText();
        if (!isEmpty(referenceName)) {
          GrArgumentList argumentList = methodCall.getArgumentList();
          if (argumentList.getAllArguments().length > 0) {
            return new GradleDslMethodCall(parentElement, methodCall, referenceName, argumentList);
          }
        }
      }
    }

    return null;
  }

  private static GradlePropertiesDslElement getBlockElement(@NotNull List<String> qualifiedName,
                                                            @NotNull GradlePropertiesDslElement parentElement) {
    GradlePropertiesDslElement resultElement = parentElement;
    for (String nestedElementName : qualifiedName) {
      GradleDslElement element = resultElement.getPropertyElement(nestedElementName);
      if (element == null) {
        GradlePropertiesDslElement newElement;
        if (resultElement instanceof GradleDslFile) {
          if (ExtDslElement.NAME.equals(nestedElementName)) {
            newElement = new ExtDslElement(resultElement);
          }
          else if (AndroidDslElement.NAME.equals(nestedElementName)) {
            newElement = new AndroidDslElement(resultElement);
          } else if (DependenciesDslElement.NAME.equals(nestedElementName)) {
            newElement = new DependenciesDslElement(resultElement);
          } else {
            return null;
          }
        }
        else if (resultElement instanceof AndroidDslElement) {
          if ("defaultConfig".equals(nestedElementName)) {
            newElement = new ProductFlavorDslElement(resultElement, nestedElementName);
          }
          else if (ProductFlavorsDslElement.NAME.equals(nestedElementName)) {
            newElement = new ProductFlavorsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof ProductFlavorsDslElement) {
          newElement = new ProductFlavorDslElement(resultElement, nestedElementName);
        }
        else if (resultElement instanceof ProductFlavorDslElement
                 && ("manifestPlaceholders".equals(nestedElementName) || "testInstrumentationRunnerArguments".equals(nestedElementName))){
          newElement = new GradleDslLiteralMap(resultElement, nestedElementName);
        }
        else {
          return null;
        }
        resultElement.setDslElement(nestedElementName, newElement);
        resultElement = newElement;
      }
      else if (element instanceof GradlePropertiesDslElement) {
        resultElement = (GradlePropertiesDslElement) element;
      }
      else {
        return null;
      }
    }
    return resultElement;
  }
}
