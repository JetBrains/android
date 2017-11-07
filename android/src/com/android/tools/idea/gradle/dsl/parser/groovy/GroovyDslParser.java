/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.groovy;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl;
import com.android.tools.idea.gradle.dsl.model.android.AndroidModelImpl;
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser;
import com.android.tools.idea.gradle.dsl.parser.android.*;
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.ExternalNativeBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.CMakeOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.productFlavors.externalNativeBuild.NdkBuildOptionsDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceDirectoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.sourceSets.SourceFileDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.splits.DensityDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement;
import com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependencyConfigurationDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.*;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.files.GradleDslFile;
import com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import com.android.tools.idea.gradle.dsl.parser.settings.ProjectPropertiesDslElement;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement.AAPT_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.AdbOptionsDslElement.ADB_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.AndroidDslElement.ANDROID_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.BuildTypesDslElement.BUILD_TYPES_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement.DATA_BINDING_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.DexOptionsDslElement.DEX_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.ExternalNativeBuildDslElement.EXTERNAL_NATIVE_BUILD_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.LintOptionsDslElement.LINT_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.PackagingOptionsDslElement.PACKAGING_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.ProductFlavorsDslElement.PRODUCT_FLAVORS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.SigningConfigsDslElement.SIGNING_CONFIGS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.SourceSetsDslElement.SOURCE_SETS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.SplitsDslElement.SPLITS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.TestOptionsDslElement.TEST_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.CMakeDslElement.CMAKE_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.externalNativeBuild.NdkBuildDslElement.NDK_BUILD_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.productFlavors.NdkOptionsDslElement.NDK_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement.ABI_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.splits.DensityDslElement.DENSITY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement.LANGUAGE_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.android.testOptions.UnitTestsDslElement.UNIT_TESTS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement.APPLY_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement.BUILDSCRIPT_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.build.SubProjectsDslElement.SUBPROJECTS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.DEPENDENCIES_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.elements.BaseCompileOptionsDslElement.COMPILE_OPTIONS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.FlatDirRepositoryDslElement.FLAT_DIR_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement.CREDENTIALS_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.JCENTER_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement.MAVEN_BLOCK_NAME;
import static com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement.REPOSITORIES_BLOCK_NAME;
import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.psi.util.PsiTreeUtil.*;


/**
 * Generic parser to parse .gradle files.
 *
 * <p>It parses any general application statements or assigned statements in the .gradle file directly and stores them as key value pairs
 * in the {@link GradleBuildModelImpl}. For every closure block section like {@code android{}}, it will create block elements like
 * {@link AndroidModelImpl}. See {@link #getBlockElement(List, GradlePropertiesDslElement)} for all the block elements currently supported
 * by this parser.
 */
public class GroovyDslParser implements GradleDslParser {
  @NotNull private final GroovyFile myPsiFile;
  @NotNull private final GradleDslFile myDslFile;

  public GroovyDslParser(@NotNull GroovyFile file, @NotNull GradleDslFile dslFile) {
    myPsiFile = file;
    myDslFile = dslFile;
  }

  @Override
  public void parse() {
    myPsiFile.acceptChildren(new GroovyPsiElementVisitor(new GroovyElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull GrMethodCallExpression e) {
        process(e);
      }

      @Override
      public void visitAssignmentExpression(@NotNull GrAssignmentExpression e) {
        process(e);
      }

      @Override
      public void visitApplicationStatement(@NotNull GrApplicationStatement e) {
        process(e);
      }

      @Override
      public void visitVariableDeclaration(@NotNull GrVariableDeclaration e) {
        process(e);
      }

      void process(GroovyPsiElement e) {
        if (!parse(e, myDslFile)) {
          Logger.getInstance(this.getClass()).info("GroovyDslParser failed at parsing file: " + myPsiFile.getName());
        }
      }
    }));
  }

  private static boolean parse(@NotNull PsiElement psiElement, @NotNull GradleDslFile gradleDslFile) {
    if (psiElement instanceof GrMethodCallExpression) {
      return parse((GrMethodCallExpression)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    else if (psiElement instanceof GrAssignmentExpression) {
      return parse((GrAssignmentExpression)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    else if (psiElement instanceof GrApplicationStatement) {
      return parse((GrApplicationStatement)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    else if (psiElement instanceof GrVariableDeclaration) {
      return parse((GrVariableDeclaration)psiElement, (GradlePropertiesDslElement)gradleDslFile);
    }
    return false;
  }

  private static boolean parse(@NotNull GrMethodCallExpression expression, @NotNull GradlePropertiesDslElement dslElement) {
    GrReferenceExpression referenceExpression = findChildOfType(expression, GrReferenceExpression.class);
    if (referenceExpression == null) {
      return false;
    }

    String name = referenceExpression.getText();
    if (isEmpty(name)) {
      return false;
    }

    List<String> nameSegments = Splitter.on('.').splitToList(name);
    if (nameSegments.size() > 1) {
      dslElement = getBlockElement(nameSegments.subList(0, (nameSegments.size() - 1)), dslElement);
      name = nameSegments.get(nameSegments.size() - 1);
    }

    if (dslElement == null) {
      return false;
    }

    GrClosableBlock[] closureArguments = expression.getClosureArguments();
    GrArgumentList argumentList = expression.getArgumentList();
    if (argumentList.getAllArguments().length > 0) {
      // This element is a method call with arguments and an optional closure associated with it.
      // ex: compile("dependency") {}
      GradleDslExpression methodCall = getMethodCall(dslElement, expression, name, argumentList);
      if (closureArguments.length > 0) {
        methodCall.setParsedClosureElement(getClosureElement(methodCall, closureArguments[0], name));
      }
      dslElement.addParsedElement(name, methodCall);
      return true;
    }

    if (argumentList.getAllArguments().length == 0 && closureArguments.length == 0) {
      // This element is a pure method call, i.e a method call with no arguments and no closure arguments.
      // ex: jcenter()
      dslElement.addParsedElement(name, new GradleDslMethodCall(dslElement, expression, name));
      return true;
    }

    // Now this element is pure block element, i.e a method call with no argument but just a closure argument. So, here just process the
    // closure and treat it as a block element.
    // ex: android {}
    GrClosableBlock closableBlock = closureArguments[0];
    List<GradlePropertiesDslElement> blockElements = Lists.newArrayList(); // The block elements this closure needs to be applied.

    if (dslElement instanceof GradleDslFile && name.equals("allprojects")) {
      // The "allprojects" closure needs to be applied to this project and all it's sub projects.
      blockElements.add(dslElement);
      // After applying the allprojects closure to this project, process it as subprojects section to also pass the same properties to
      // subprojects.
      name = "subprojects";
    }

    GradlePropertiesDslElement blockElement = getBlockElement(ImmutableList.of(name), dslElement);
    if (blockElement != null) {
      blockElement.setPsiElement(closableBlock);
      blockElements.add(blockElement);
    }

    if (blockElements.isEmpty()) {
      return false;
    }
    for (GradlePropertiesDslElement element : blockElements) {
      parse(closableBlock, element);
    }
    return true;
  }

  private static void parse(@NotNull GrClosableBlock closure, @NotNull final GradlePropertiesDslElement blockElement) {
    closure.acceptChildren(new GroovyElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCallExpression) {
        parse(methodCallExpression, blockElement);
      }

      @Override
      public void visitApplicationStatement(@NotNull GrApplicationStatement applicationStatement) {
        parse(applicationStatement, blockElement);
      }

      @Override
      public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
        parse(expression, blockElement);
      }

      @Override
      public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
        parse(variableDeclaration, blockElement);
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
      }
      else {
        return false;
      }
    }

    String propertyName = nameSegments.get(nameSegments.size() - 1);
    // TODO: This code highly restricts the arguments allowed in an application statement. Fix this.
    GradleDslElement propertyElement = null;
    if (arguments[0] instanceof GrExpression) { // ex: proguardFiles 'proguard-android.txt', 'proguard-rules.pro'
      List<GrExpression> expressions = new ArrayList<>(arguments.length);
      for (GroovyPsiElement element : arguments) {
        // We need to make sure all of these are GrExpressions, there can be multiple types.
        // We currently can't handle different argument types.
        if (element instanceof GrExpression) {
          expressions.add((GrExpression)element);
        }
      }
      if (expressions.size() == 1) {
        propertyElement = getExpressionElement(blockElement, argumentList, propertyName, expressions.get(0));
      }
      else {
        propertyElement = getExpressionList(blockElement, argumentList, propertyName, expressions);
      }
    }
    else if (arguments[0] instanceof GrNamedArgument) {
      // ex: manifestPlaceholders activityLabel1:"defaultName1", activityLabel2:"defaultName2"
      List<GrNamedArgument> namedArguments = new ArrayList<>(arguments.length);
      for (GroovyPsiElement element : arguments) {
        // We need to make sure all of these are GrNamedArgument, there can be multiple types.
        // We currently can't handle different argument types.
        if (element instanceof GrNamedArgument) {
          namedArguments.add((GrNamedArgument)element);
        }
      }
      propertyElement = getExpressionMap(blockElement, argumentList, propertyName, namedArguments);
    }
    if (propertyElement == null) {
      return false;
    }

    GroovyPsiElement lastArgument = arguments[arguments.length - 1];
    if (lastArgument instanceof GrClosableBlock) {
      propertyElement.setParsedClosureElement(getClosureElement(propertyElement, (GrClosableBlock)lastArgument, name));
    }

    blockElement.addParsedElement(propertyName, propertyElement);
    return true;
  }

  @Nullable
  private static GradleDslElement createExpressionElement(@NotNull GradleDslElement parent,
                                                          @NotNull GroovyPsiElement psiElement,
                                                          @NotNull String name,
                                                          @Nullable GrExpression expression) {
    if (expression == null) {
      return null;
    }

    GradleDslElement propertyElement;
    if (expression instanceof GrListOrMap) {
      GrListOrMap listOrMap = (GrListOrMap)expression;
      if (listOrMap.isMap()) { // ex: manifestPlaceholders = [activityLabel1:"defaultName1", activityLabel2:"defaultName2"]
        propertyElement = getExpressionMap(parent, listOrMap, name, Arrays.asList(listOrMap.getNamedArguments()));
      }
      else { // ex: proguardFiles = ['proguard-android.txt', 'proguard-rules.pro']
        propertyElement = getExpressionList(parent, listOrMap, name, Arrays.asList(listOrMap.getInitializers()));
      }
    }
    else {
      propertyElement = getExpressionElement(parent, psiElement, name, expression);
    }

    return propertyElement;
  }

  private static boolean parse(@NotNull GrVariableDeclaration declaration, @NotNull GradlePropertiesDslElement blockElement) {
    if (declaration.getVariables().length == 0) {
      return false;
    }

    for (GrVariable variable : declaration.getVariables()) {
      if (variable == null) {
        return false;
      }

      GradleDslElement variableElement =
        createExpressionElement(blockElement, declaration, variable.getName(), variable.getInitializerGroovy());
      if (variableElement == null) {
        return false;
      }

      blockElement.setParsedVariable(variable.getName(), variableElement);
    }
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
      }
      else {
        return false;
      }
    }

    String propertyName = nameSegments.get(nameSegments.size() - 1).trim();
    GrExpression right = assignment.getRValue();
    if (right == null) {
      return false;
    }

    GradleDslElement propertyElement = createExpressionElement(blockElement, assignment, propertyName, right);
    if (propertyElement == null) {
      return false;
    }

    blockElement.setParsedElement(propertyName, propertyElement);
    return true;
  }

  @Nullable
  private static GradleDslExpression getExpressionElement(@NotNull GradleDslElement parentElement,
                                                          @NotNull GroovyPsiElement psiElement,
                                                          @NotNull String propertyName,
                                                          @NotNull GrExpression propertyExpression) {
    if (propertyExpression instanceof GrLiteral) { // ex: compileSdkVersion 23 or compileSdkVersion = "android-23"
      return new GradleDslLiteral(parentElement, psiElement, propertyName, (GrLiteral)propertyExpression);
    }

    if (propertyExpression instanceof GrReferenceExpression) { // ex: compileSdkVersion SDK_VERSION or sourceCompatibility = VERSION_1_5
      return new GradleDslReference(parentElement, psiElement, propertyName, (GrReferenceExpression)propertyExpression);
    }

    if (propertyExpression instanceof GrMethodCallExpression) { // ex: compile project("someProject")
      GrMethodCallExpression methodCall = (GrMethodCallExpression)propertyExpression;
      GrReferenceExpression callReferenceExpression = getChildOfType(methodCall, GrReferenceExpression.class);
      if (callReferenceExpression != null) {
        String referenceName = callReferenceExpression.getText();
        if (!isEmpty(referenceName)) {
          GrArgumentList argumentList = methodCall.getArgumentList();
          if (argumentList.getAllArguments().length > 0) {
            return getMethodCall(parentElement, methodCall, referenceName, argumentList);
          }
          else {
            return new GradleDslMethodCall(parentElement, methodCall, referenceName);
          }
        }
      }
    }

    if (propertyExpression instanceof GrNewExpression) {
      GrNewExpression newExpression = (GrNewExpression)propertyExpression;
      GrCodeReferenceElement referenceElement = newExpression.getReferenceElement();
      if (referenceElement != null) {
        String objectName = referenceElement.getText();
        if (!isEmpty(objectName)) {
          GrArgumentList argumentList = newExpression.getArgumentList();
          if (argumentList != null) {
            if (argumentList.getAllArguments().length > 0) {
              return getNewExpression(parentElement, newExpression, objectName, argumentList);
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private static GradleDslMethodCall getMethodCall(@NotNull GradleDslElement parentElement,
                                                   @NotNull GrMethodCallExpression psiElement,
                                                   @NotNull String propertyName,
                                                   @NotNull GrArgumentList argumentList) {
    GradleDslMethodCall methodCall = new GradleDslMethodCall(parentElement, psiElement, propertyName);

    for (GrExpression expression : argumentList.getExpressionArguments()) {
      if (expression instanceof GrListOrMap) {
        GrListOrMap listOrMap = (GrListOrMap)expression;
        if (listOrMap.isMap()) {
          methodCall
            .addParsedExpressionMap(getExpressionMap(methodCall, expression, propertyName, Arrays.asList(listOrMap.getNamedArguments())));
        }
        else {
          for (GrExpression grExpression : listOrMap.getInitializers()) {
            GradleDslExpression dslExpression = getExpressionElement(methodCall, expression, propertyName, grExpression);
            if (dslExpression != null) {
              methodCall.addParsedExpression(dslExpression);
            }
          }
        }
      }
      else if (expression instanceof GrClosableBlock) {
        methodCall.setParsedClosureElement(getClosureElement(methodCall, (GrClosableBlock)expression, propertyName));
      }
      else {
        GradleDslExpression dslExpression = getExpressionElement(methodCall, expression, propertyName, expression);
        if (dslExpression != null) {
          methodCall.addParsedExpression(dslExpression);
        }
      }
    }

    GrNamedArgument[] namedArguments = argumentList.getNamedArguments();
    if (namedArguments.length > 0) {
      methodCall.addParsedExpressionMap(getExpressionMap(methodCall, argumentList, propertyName, Arrays.asList(namedArguments)));
    }

    return methodCall;
  }

  @NotNull
  private static GradleDslNewExpression getNewExpression(@NotNull GradleDslElement parentElement,
                                                         @NotNull GrNewExpression psiElement,
                                                         @NotNull String propertyName,
                                                         @NotNull GrArgumentList argumentList) {
    GradleDslNewExpression newExpression = new GradleDslNewExpression(parentElement, psiElement, propertyName);

    for (GrExpression expression : argumentList.getExpressionArguments()) {
      if (expression instanceof GrListOrMap) {
        GrListOrMap listOrMap = (GrListOrMap)expression;
        if (!listOrMap.isMap()) {
          for (GrExpression grExpression : listOrMap.getInitializers()) {
            GradleDslExpression dslExpression = getExpressionElement(newExpression, expression, propertyName, grExpression);
            if (dslExpression != null) {
              newExpression.addParsedExpression(dslExpression);
            }
          }
        }
      }
      else {
        GradleDslExpression dslExpression = getExpressionElement(newExpression, expression, propertyName, expression);
        if (dslExpression != null) {
          newExpression.addParsedExpression(dslExpression);
        }
      }
    }

    return newExpression;
  }

  @NotNull
  private static GradleDslExpressionList getExpressionList(@NotNull GradleDslElement parentElement,
                                                           @NotNull GroovyPsiElement listPsiElement, // GrArgumentList or GrListOrMap
                                                           @NotNull String propertyName,
                                                           @NotNull List<GrExpression> propertyExpressions) {
    GradleDslExpressionList expressionList = new GradleDslExpressionList(parentElement, listPsiElement, propertyName);
    for (GrExpression expression : propertyExpressions) {
      GradleDslExpression expressionElement = getExpressionElement(expressionList, listPsiElement, propertyName, expression);
      if (expressionElement != null) {
        expressionList.addParsedExpression(expressionElement);
      }
    }
    return expressionList;
  }

  @NotNull
  private static GradleDslExpressionMap getExpressionMap(@NotNull GradleDslElement parentElement,
                                                         @NotNull GroovyPsiElement mapPsiElement, // GrArgumentList or GrListOrMap
                                                         @NotNull String propertyName,
                                                         @NotNull List<GrNamedArgument> namedArguments) {
    GradleDslExpressionMap expressionMap = new GradleDslExpressionMap(parentElement, mapPsiElement, propertyName);
    for (GrNamedArgument namedArgument : namedArguments) {
      String argName = namedArgument.getLabelName();
      if (isEmpty(argName)) {
        continue;
      }
      GrExpression valueExpression = namedArgument.getExpression();
      if (valueExpression == null) {
        continue;
      }
      GradleDslElement valueElement = getExpressionElement(expressionMap, mapPsiElement, argName, valueExpression);
      if (valueElement == null && valueExpression instanceof GrListOrMap) {
        GrListOrMap listOrMap = (GrListOrMap)valueExpression;
        if (listOrMap.isMap()) {
          valueElement = getExpressionMap(expressionMap, listOrMap, argName, Arrays.asList(listOrMap.getNamedArguments()));
        }
        else { // ex: flatDir name: "libs", dirs: ["libs1", "libs2"]
          valueElement = getExpressionList(expressionMap, listOrMap, argName, Arrays.asList(listOrMap.getInitializers()));
        }
      }
      if (valueElement != null) {
        expressionMap.setParsedElement(argName, valueElement);
      }
    }
    return expressionMap;
  }

  @NotNull
  private static GradleDslClosure getClosureElement(@NotNull GradleDslElement parentElement,
                                                    @NotNull GrClosableBlock closableBlock,
                                                    @NotNull String propertyName) {
    GradleDslClosure closureElement;
    if (parentElement.getParent() instanceof DependenciesDslElement) {
      closureElement = new DependencyConfigurationDslElement(parentElement, closableBlock, propertyName);
    }
    else {
      closureElement = new GradleDslClosure(parentElement, closableBlock, propertyName);
    }
    parse(closableBlock, closureElement);
    return closureElement;
  }

  private static GradlePropertiesDslElement getBlockElement(@NotNull List<String> qualifiedName,
                                                            @NotNull GradlePropertiesDslElement parentElement) {
    GradlePropertiesDslElement resultElement = parentElement;
    for (String nestedElementName : qualifiedName) {
      nestedElementName = nestedElementName.trim();
      GradleDslElement element = resultElement.getPropertyElement(nestedElementName);
      if (element == null) {
        GradlePropertiesDslElement newElement;
        if (resultElement instanceof GradleDslFile || resultElement instanceof SubProjectsDslElement) {
          if (APPLY_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ApplyDslElement(resultElement);
          }
          else if (EXT_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ExtDslElement(resultElement);
          }
          else if (ANDROID_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new AndroidDslElement(resultElement);
          }
          else if (DEPENDENCIES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DependenciesDslElement(resultElement);
          }
          else if (SUBPROJECTS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new SubProjectsDslElement(resultElement);
          }
          else if (BUILDSCRIPT_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new BuildScriptDslElement(resultElement);
          }
          else if (REPOSITORIES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new RepositoriesDslElement(resultElement);
          }
          else {
            String projectKey = ProjectPropertiesDslElement.getStandardProjectKey(nestedElementName);
            if (projectKey != null) {
              nestedElementName = projectKey;
              newElement = new ProjectPropertiesDslElement(resultElement, nestedElementName);
            }
            else {
              return null;
            }
          }
        }
        else if (resultElement instanceof BuildScriptDslElement) {
          if (DEPENDENCIES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DependenciesDslElement(resultElement);
          }
          else if (REPOSITORIES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new RepositoriesDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof RepositoriesDslElement) {
          if (MAVEN_BLOCK_NAME.equals(nestedElementName) || JCENTER_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new MavenRepositoryDslElement(resultElement, nestedElementName);
          }
          else if (FLAT_DIR_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new FlatDirRepositoryDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof MavenRepositoryDslElement) {
          if (CREDENTIALS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new MavenCredentialsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof AndroidDslElement) {
          if ("defaultConfig".equals(nestedElementName)) {
            newElement = new ProductFlavorDslElement(resultElement, nestedElementName);
          }
          else if (PRODUCT_FLAVORS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ProductFlavorsDslElement(resultElement);
          }
          else if (BUILD_TYPES_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new BuildTypesDslElement(resultElement);
          }
          else if (COMPILE_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new CompileOptionsDslElement(resultElement);
          }
          else if (EXTERNAL_NATIVE_BUILD_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ExternalNativeBuildDslElement(resultElement);
          }
          else if (SIGNING_CONFIGS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new SigningConfigsDslElement(resultElement);
          }
          else if (SOURCE_SETS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new SourceSetsDslElement(resultElement);
          }
          else if (AAPT_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new AaptOptionsDslElement(resultElement);
          }
          else if (ADB_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new AdbOptionsDslElement(resultElement);
          }
          else if (DATA_BINDING_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DataBindingDslElement(resultElement);
          }
          else if (DEX_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DexOptionsDslElement(resultElement);
          }
          else if (LINT_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new LintOptionsDslElement(resultElement);
          }
          else if (PACKAGING_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new PackagingOptionsDslElement(resultElement);
          }
          else if (SPLITS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new SplitsDslElement(resultElement);
          }
          else if (TEST_OPTIONS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new TestOptionsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof ExternalNativeBuildDslElement) {
          if (CMAKE_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new CMakeDslElement(resultElement);
          }
          else if (NDK_BUILD_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new NdkBuildDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof ProductFlavorsDslElement) {
          newElement = new ProductFlavorDslElement(resultElement, nestedElementName);
        }
        else if (resultElement instanceof ProductFlavorDslElement) {
          if ("manifestPlaceholders".equals(nestedElementName) || "testInstrumentationRunnerArguments".equals(nestedElementName)) {
            newElement = new GradleDslExpressionMap(resultElement, nestedElementName);
          }
          else if (EXTERNAL_NATIVE_BUILD_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new ExternalNativeBuildOptionsDslElement(resultElement);
          }
          else if (NDK_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new NdkOptionsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof BuildTypesDslElement) {
          newElement = new BuildTypeDslElement(resultElement, nestedElementName);
        }
        else if (resultElement instanceof BuildTypeDslElement && "manifestPlaceholders".equals(nestedElementName)) {
          newElement = new GradleDslExpressionMap(resultElement, nestedElementName);
        }
        else if (resultElement instanceof SigningConfigsDslElement) {
          newElement = new SigningConfigDslElement(resultElement, nestedElementName);
        }
        else if (resultElement instanceof SourceSetsDslElement) {
          newElement = new SourceSetDslElement(resultElement, nestedElementName);
        }
        else if (resultElement instanceof SourceSetDslElement) {
          if ("manifest".equals(nestedElementName)) {
            newElement = new SourceFileDslElement(resultElement, nestedElementName);
          }
          else {
            newElement = new SourceDirectoryDslElement(resultElement, nestedElementName);
          }
        }
        else if (resultElement instanceof ExternalNativeBuildOptionsDslElement) {
          if (CMAKE_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new CMakeOptionsDslElement(resultElement);
          }
          else if (NDK_BUILD_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new NdkBuildOptionsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof SplitsDslElement) {
          if (ABI_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new AbiDslElement(resultElement);
          }
          else if (DENSITY_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new DensityDslElement(resultElement);
          }
          else if (LANGUAGE_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new LanguageDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else if (resultElement instanceof TestOptionsDslElement) {
          if (UNIT_TESTS_BLOCK_NAME.equals(nestedElementName)) {
            newElement = new UnitTestsDslElement(resultElement);
          }
          else {
            return null;
          }
        }
        else {
          return null;
        }
        resultElement.setParsedElement(nestedElementName, newElement);
        resultElement = newElement;
      }
      else if (element instanceof GradlePropertiesDslElement) {
        resultElement = (GradlePropertiesDslElement)element;
      }
      else {
        return null;
      }
    }
    return resultElement;
  }
}
