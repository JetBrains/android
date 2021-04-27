/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common;

import static com.android.SdkConstants.ATTR_MIN_SDK_VERSION;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.GradleVisitor;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.GradleContext;
import com.android.tools.lint.detector.api.GradleScanner;
import com.android.tools.lint.detector.api.Location;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

public class LintIdeGradleVisitor extends GradleVisitor {
  @Nullable
  private static String getClosureName(@NonNull GrClosableBlock closure) {
    if (closure.getParent() instanceof GrMethodCall) {
      GrMethodCall parent = (GrMethodCall)closure.getParent();
      if (parent.getInvokedExpression() instanceof GrReferenceExpression) {
        GrReferenceExpression invokedExpression = (GrReferenceExpression)(parent.getInvokedExpression());
        if (invokedExpression.getDotToken() == null) {
          return invokedExpression.getReferenceName();
        }
      }
    }

    return null;
  }

  private static void extractMethodCallArguments(GrMethodCall methodCall, List<String> unnamed, Map<String, String> named) {
    GrArgumentList argumentList = methodCall.getArgumentList();
    for (GroovyPsiElement groovyPsiElement : argumentList.getAllArguments()) {
      if (groovyPsiElement instanceof GrNamedArgument) {
        GrNamedArgument namedArgument = (GrNamedArgument)groovyPsiElement;
        GrExpression expression = namedArgument.getExpression();
        if (!(expression instanceof GrLiteral)) {
          continue;
        }
        Object value = ((GrLiteral)expression).getValue();
        if (value == null) {
          continue;
        }
        named.put(namedArgument.getLabelName(), value.toString());
      }
      else if (groovyPsiElement instanceof GrExpression) {
        unnamed.add(groovyPsiElement.getText());
      }
    }
  }

  @Override
  public void visitBuildScript(@NotNull GradleContext context, @NotNull List<? extends GradleScanner> detectors) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        final PsiFile psiFile = LintIdeUtilsKt.getPsiFile(context);
        if (!(psiFile instanceof GroovyFile)) {
          return;
        }
        GroovyFile groovyFile = (GroovyFile)psiFile;
        groovyFile.accept(new GroovyRecursiveElementVisitor() {
          @Override
          public void visitClosure(@NotNull GrClosableBlock closure) {
            String parentName = getClosureName(closure);
            String parentParentName = null;
            if (parentName != null) {
              GrClosableBlock block = PsiTreeUtil.getParentOfType(closure, GrClosableBlock.class, true);
              if (block != null) {
                parentParentName = getClosureName(block);
              }
            }
            if (parentName != null) {
              for (PsiElement element : closure.getChildren()) {
                if (element instanceof GrApplicationStatement) {
                  GrApplicationStatement call = (GrApplicationStatement)element;
                  GrExpression propertyExpression = call.getInvokedExpression();
                  GrCommandArgumentList argumentList = call.getArgumentList();
                  if (propertyExpression instanceof GrReferenceExpression) {
                    GrReferenceExpression propertyRef = (GrReferenceExpression)propertyExpression;
                    String property = propertyRef.getReferenceName();
                    //noinspection ConstantConditions
                    if (property != null && argumentList != null) {
                      String value = argumentList.getText();
                      for (GradleScanner detector : detectors) {
                        detector
                          .checkDslPropertyAssignment(context, property, value, parentName, parentParentName, propertyRef, argumentList,
                                                      call);
                      }
                    }
                  }
                }
                else if (element instanceof GrMethodCallExpression) {
                  GrMethodCallExpression assignment = (GrMethodCallExpression)element;
                  GrExpression lValue = assignment.getInvokedExpression();
                  if (lValue instanceof GrReferenceExpression) {
                    GrReferenceExpression propertyRef = (GrReferenceExpression)lValue;
                    String property = propertyRef.getReferenceName();
                    if (property != null) {
                      GrExpression[] list = assignment.getArgumentList().getExpressionArguments();
                      if (list.length == 1) {
                        GrExpression rValue = list[0];
                        String value = rValue.getText();
                        for (GradleScanner detector : detectors) {
                          detector
                            .checkDslPropertyAssignment(context, property, value, parentName, parentParentName, lValue, rValue, assignment);
                        }
                      }
                      else {
                        // the case above - a 1-arg method call within a closure - is usually (though not always) a Dsl property
                        // assignment.  All other method calls (0 or 2+ arguments) are not, so check it as a method call.
                        Map<String, String> namedArguments = Maps.newHashMap();
                        List<String> unnamedArguments = Lists.newArrayList();
                        extractMethodCallArguments(assignment, unnamedArguments, namedArguments);
                        for (GradleScanner detector : detectors) {
                          detector
                            .checkMethodCall(context, property, parentName, parentParentName, namedArguments, unnamedArguments, assignment);
                        }
                      }
                    }
                  }
                }
                else if (element instanceof GrAssignmentExpression) {
                  GrAssignmentExpression assignment = (GrAssignmentExpression)element;
                  GrExpression lValue = assignment.getLValue();
                  if (lValue instanceof GrReferenceExpression) {
                    GrReferenceExpression propertyRef = (GrReferenceExpression)lValue;
                    String property = propertyRef.getReferenceName();
                    if (property != null) {
                      GrExpression qualifierExpression = propertyRef.getQualifierExpression();
                      if (qualifierExpression instanceof GrReferenceExpression) {
                        GrReferenceExpression qualifierRef = (GrReferenceExpression)qualifierExpression;
                        String qualifier = qualifierRef.getReferenceName();
                        if (qualifier != null) {
                          parentParentName = parentName;
                          parentName = qualifier;
                        }
                      }
                      GrExpression rValue = assignment.getRValue();
                      if (rValue != null) {
                        String value = rValue.getText();
                        for (GradleScanner detector : detectors) {
                          detector
                            .checkDslPropertyAssignment(context, property, value, parentName, parentParentName, lValue, rValue, assignment);
                        }

                        // As of 0.11 you can't use assignment for these two properties. This is handled here rather
                        // than up in GradleDetector for a couple of reasons: The project won't compile with that
                        // error, so gradle from the command line won't get invoked. Second, we want to do some unusual
                        // things with the positions here (map between two nodes), and the property abstraction we
                        // pass to GradleDetector doesn't distinguish between assignments and DSL method calls, so just
                        // handle it here.
                        if (!parentName.equals("ext") &&
                            (property.equals(ATTR_MIN_SDK_VERSION) || property.equals(ATTR_TARGET_SDK_VERSION))) {
                          int lValueEnd = lValue.getTextRange().getEndOffset();
                          int rValueStart = rValue.getTextRange().getStartOffset();
                          assert lValueEnd <= rValueStart;
                          DefaultPosition startPosition = new DefaultPosition(-1, -1, lValueEnd);
                          DefaultPosition endPosition = new DefaultPosition(-1, -1, rValueStart);
                          Location location = Location.create(context.file, startPosition, endPosition);
                          String message = String.format("Do not use assignment with the %1$s property (remove the '=')", property);
                          context.report(GradleDetector.IDE_SUPPORT, location, message, null);
                        }
                      }
                    }
                  }
                }
              }
            }
            super.visitClosure(closure);
          }

          @Override
          public void visitApplicationStatement(@NotNull GrApplicationStatement applicationStatement) {
            GrClosableBlock block = PsiTreeUtil.getParentOfType(applicationStatement, GrClosableBlock.class, true);
            String parentName = block != null ? getClosureName(block) : null;
            String parentParentName = null;
            if (parentName != null) {
              GrClosableBlock outerBlock = PsiTreeUtil.getParentOfType(block, GrClosableBlock.class, true);
              parentParentName = outerBlock != null ? getClosureName(outerBlock) : null;
            }
            String statementName = applicationStatement.getInvokedExpression().getText();
            Map<String, String> namedArguments = Maps.newHashMap();
            List<String> unnamedArguments = Lists.newArrayList();
            extractMethodCallArguments(applicationStatement, unnamedArguments, namedArguments);
            if (parentName == null && unnamedArguments.size() == 1 && namedArguments.isEmpty()) {
              // This might be a top-level application statement for Dsl property assignment with embedded hierarchy
              GrExpression invokedExpression = applicationStatement.getInvokedExpression();
              if (invokedExpression instanceof GrReferenceExpression) {
                GrReferenceExpression referenceExpression = (GrReferenceExpression) invokedExpression;
                GrExpression qualifierExpression = referenceExpression.getQualifierExpression();
                String qualifierName = "";
                if (qualifierExpression != null) {
                  qualifierName = qualifierExpression.getText();
                }
                String name = referenceExpression.getReferenceName();
                if (name != null) {
                  String value = unnamedArguments.get(0);
                  GrCommandArgumentList argumentList = applicationStatement.getArgumentList();
                  for (GradleScanner detector : detectors) {
                    detector.checkDslPropertyAssignment(context, name, value, qualifierName, null,
                                                        invokedExpression, argumentList, applicationStatement);
                  }
                }
              }
            }
            for (GradleScanner detector : detectors) {
              detector.checkMethodCall(context, statementName, parentName, parentParentName, namedArguments, unnamedArguments,
                                       applicationStatement);
            }
            super.visitApplicationStatement(applicationStatement);
          }

          @Override
          public void visitAssignmentExpression(@NotNull GrAssignmentExpression expression) {
            GrClosableBlock block = PsiTreeUtil.getParentOfType(expression, GrClosableBlock.class, true);
            // if block is not null, we will handle assignments in visitClosure()
            if (block == null) {
              GrExpression lvalue = expression.getLValue();
              if (lvalue instanceof GrReferenceExpression) {
                GrReferenceExpression lvalueRef = (GrReferenceExpression) lvalue;
                GrExpression qualifierExpression = lvalueRef.getQualifierExpression();
                String qualifierName = "";
                if (qualifierExpression != null) {
                  qualifierName = qualifierExpression.getText();
                }
                String name = lvalueRef.getReferenceName();
                GrExpression rvalue = expression.getRValue();
                if (rvalue != null) {
                  String value = rvalue.getText();
                  for (GradleScanner detector : detectors) {
                    detector.checkDslPropertyAssignment(context, name, value, qualifierName,
                                                        null, lvalue, rvalue, expression);
                  }
                }
              }
            }
            super.visitAssignmentExpression(expression);
          }
        });
      }
    });
  }

  @Override
  public int getStartOffset(@NotNull GradleContext context, @NotNull Object cookie) {
    int startOffset = super.getStartOffset(context, cookie);
    if (startOffset != -1) {
      return startOffset;
    }

    PsiElement element = (PsiElement)cookie;
    TextRange textRange = element.getTextRange();
    return textRange.getStartOffset();
  }

  @NotNull
  @Override
  public Location createLocation(@NotNull GradleContext context, @NotNull Object cookie) {
    PsiElement element = (PsiElement)cookie;
    TextRange textRange = element.getTextRange();
    int start = textRange.getStartOffset();
    int end = textRange.getEndOffset();
    return Location.create(context.file, new DefaultPosition(-1, -1, start), new DefaultPosition(-1, -1, end)).withSource(element);
  }
}
