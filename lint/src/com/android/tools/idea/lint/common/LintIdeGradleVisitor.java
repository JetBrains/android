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
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.GradleVisitor;
import com.android.tools.lint.detector.api.DefaultPosition;
import com.android.tools.lint.detector.api.GradleContext;
import com.android.tools.lint.detector.api.GradleScanner;
import com.android.tools.lint.detector.api.Location;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
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
  private static List<String> getClosureNames(@NonNull GrClosableBlock closure) {
    ArrayList<String> result = new ArrayList<>(2);
    if (closure.getParent() instanceof GrMethodCall) {
      GrMethodCall parent = (GrMethodCall)closure.getParent();
      if (parent.getInvokedExpression() instanceof GrReferenceExpression) {
        GrReferenceExpression invokedExpression = (GrReferenceExpression)(parent.getInvokedExpression());
        if (invokedExpression.getReferenceName() != null) {
          result.add(invokedExpression.getReferenceName());
          if (invokedExpression.isQualified()) {
            GrExpression qualifierExpression = invokedExpression.getQualifierExpression();
            if (qualifierExpression instanceof GrReferenceExpression) {
              GrReferenceExpression qualifierReferenceExpression = (GrReferenceExpression)qualifierExpression;
              if (qualifierReferenceExpression.getReferenceName() != null) {
                result.add(qualifierReferenceExpression.getReferenceName());
              }
            }
          }
          else {
            GrClosableBlock parentClosableBlock = PsiTreeUtil.getParentOfType(closure, GrClosableBlock.class, true);
            if (parentClosableBlock != null && parentClosableBlock.getParent() instanceof GrMethodCall) {
              GrMethodCall parent2 = (GrMethodCall)parentClosableBlock.getParent();
              if (parent2.getInvokedExpression() instanceof GrReferenceExpression) {
                GrReferenceExpression parent2InvokedExpression = (GrReferenceExpression)(parent2.getInvokedExpression());
                if (parent2InvokedExpression.getReferenceName() != null) {
                  result.add(parent2InvokedExpression.getReferenceName());
                }
              }
            }
          }
        }
      }
    }

    return result;
  }

  private static List<String> getReferenceExpressionNames(GrReferenceExpression referenceExpression) {
    ArrayList<String> result = new ArrayList<>(3);
    // We need at most three strings: the property name and two parent qualifiers (for parent and parentParent in GradleDetector calls).
    if (referenceExpression.getReferenceName() != null) {
      result.add(referenceExpression.getReferenceName());
      if (referenceExpression.isQualified() && referenceExpression.getQualifierExpression() instanceof GrReferenceExpression) {
        GrReferenceExpression qualifierReferenceExpression = (GrReferenceExpression)referenceExpression.getQualifierExpression();
        if (qualifierReferenceExpression.getReferenceName() != null) {
          result.add(qualifierReferenceExpression.getReferenceName());
          if (qualifierReferenceExpression.isQualified() &&
              qualifierReferenceExpression.getQualifierExpression() instanceof GrReferenceExpression) {
            GrReferenceExpression qualifierQualifierReferenceExpression =
              (GrReferenceExpression)qualifierReferenceExpression.getQualifierExpression();
            if (qualifierQualifierReferenceExpression.getReferenceName() != null) {
              result.add(qualifierQualifierReferenceExpression.getReferenceName());
            }
          }
        }
      }
    }
    return result;
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
            List<String> closureNames = getClosureNames(closure);
            if (closureNames.size() != 0) {
              for (PsiElement element : closure.getChildren()) {
                if (element instanceof GrApplicationStatement) {
                  GrApplicationStatement call = (GrApplicationStatement)element;
                  GrExpression propertyExpression = call.getInvokedExpression();
                  GrCommandArgumentList argumentList = call.getArgumentList();
                  if (propertyExpression instanceof GrReferenceExpression) {
                    GrReferenceExpression propertyRef = (GrReferenceExpression)propertyExpression;
                    List<String> names = getReferenceExpressionNames(propertyRef);
                    names.addAll(closureNames);
                    String property = names.get(0);
                    String parentName = names.size() > 1 ? names.get(1) : null;
                    String parentParentName = names.size() > 2 ? names.get(2) : null;
                    //noinspection ConstantConditions
                    if (property != null && parentName != null && argumentList != null) {
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
                    List<String> names = getReferenceExpressionNames(propertyRef);
                    names.addAll(closureNames);
                    String property = names.get(0);
                    String parentName = names.size() > 1 ? names.get(1) : null;
                    String parentParentName = names.size() > 2 ? names.get(2) : null;
                    if (property != null && parentName != null) {
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
                        List<String> unnamedArguments = new ArrayList<>();
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
                    List<String> names = getReferenceExpressionNames(propertyRef);
                    names.addAll(closureNames);
                    String property = names.get(0);
                    String parentName = names.size() > 1 ? names.get(1) : null;
                    String parentParentName = names.size() > 2 ? names.get(2) : null;
                    if (property != null && parentName != null) {
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
            List<String> parentNames = block != null ? getClosureNames(block) : new ArrayList<>(0);
            String statementName = applicationStatement.getInvokedExpression().getText();
            Map<String, String> namedArguments = Maps.newHashMap();
            List<String> unnamedArguments = new ArrayList<>();
            extractMethodCallArguments(applicationStatement, unnamedArguments, namedArguments);
            if (parentNames.size() == 0 && unnamedArguments.size() == 1 && namedArguments.isEmpty()) {
              // This might be a top-level application statement for Dsl property assignment with embedded hierarchy
              GrExpression invokedExpression = applicationStatement.getInvokedExpression();
              if (invokedExpression instanceof GrReferenceExpression) {
                GrReferenceExpression referenceExpression = (GrReferenceExpression) invokedExpression;
                List<String> names = getReferenceExpressionNames(referenceExpression);
                String name = names.size() > 0 ? names.get(0) : null;
                String parentName = names.size() > 1 ? names.get(1) : ""; // empty-string parent convention for top-level properties
                String parentParentName = names.size() > 2 ? names.get(2) : null;
                if (name != null) {
                  String value = unnamedArguments.get(0);
                  GrCommandArgumentList argumentList = applicationStatement.getArgumentList();
                  for (GradleScanner detector : detectors) {
                    detector.checkDslPropertyAssignment(context, name, value, parentName, parentParentName,
                                                        invokedExpression, argumentList, applicationStatement);
                  }
                }
              }
            }
            String parentName = parentNames.size() > 0 ? parentNames.get(0) : null;
            String parentParentName = parentNames.size() > 1 ? parentNames.get(1) : null;
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
                List<String> names = getReferenceExpressionNames(lvalueRef);
                String name = names.size() > 0 ? names.get(0) : null;
                String parentName = names.size() > 1 ? names.get(1) : ""; // empty-string parent convention for top-level properties
                String parentParentName = names.size() > 2 ? names.get(2) : null;
                GrExpression rvalue = expression.getRValue();
                if (name != null && rvalue != null) {
                  String value = rvalue.getText();
                  for (GradleScanner detector : detectors) {
                    detector.checkDslPropertyAssignment(context, name, value, parentName,
                                                        parentParentName, lvalue, rvalue, expression);
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
