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
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
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
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyMethodCallReference;

@SuppressWarnings("PatternVariableCanBeUsed")
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
          private @Nullable GrClosableBlock getParentClosure(PsiElement element) {
            return PsiTreeUtil.getParentOfType(element, GrClosableBlock.class, true);
          }

          @Override
          public void visitVariable(@NotNull GrVariable variable) {
            String name = variable.getName();
            GrExpression initial = variable.getInitializerGroovy();
            PsiIdentifier identifier = variable.getNameIdentifier();
            if (initial != null && identifier != null) {
              String parentName = "";
              String parentParentName = null;
              GrClosableBlock closure = getParentClosure(variable);
              if (closure != null) {
                List<String> names = getClosureNames(closure);
                int size = names.size();
                if (size >= 1) {
                  parentName = names.get(0);
                  if (size >= 2) {
                    parentParentName = names.get(1);
                  }
                }
              }
              String value = initial.getText();
              for (GradleScanner detector : detectors) {
                detector.checkDslPropertyAssignment(context, name, value, parentName,
                                                    parentParentName, identifier, initial, variable);
              }
            }

            super.visitVariable(variable);
          }

          @Override
          public void visitMethodCall(@NotNull GrMethodCall call) {
            GroovyMethodCallReference reference = call.getExplicitCallReference();
            if (reference != null) {
              String methodName = reference.getMethodName();

              // Produce the parent and grand parent names.
              // When you have something like this:
              //    android {
              //       defaultConfig {
              //          compileOptions.java.level 1.7
              //          ...
              // this is equivalent to android.defaultConfig.compileOptions.java.level.
              // In our case, we're at the method call for "level", so methodName is level,
              // and we want the two closes ancestors; parent="java" and
              // parentParent="compileOptions".
              //
              // To do this we'll look at the method call; if it has a receiver which is a fully
              // qualified name (here it's "compileOptions.java.level", so we can fetch both
              // parents directly from the qualified name. If it was shorter, we may need to
              // also look at the surrounding closures to get the closure names (e.g. "android"
              // and "defaultConfig" above).
              // We then grab up to two elements from these.

              String qualifier = null;
              PsiElement firstChild = call.getFirstChild();
              if (firstChild instanceof GrReferenceExpression) {
                String s = ((GrReferenceExpression)firstChild).getQualifiedReferenceName();
                if (s != null) {
                  int index = s.lastIndexOf('.');
                  if (index != -1) {
                    qualifier = s.substring(0, index);
                  }
                }
              }
              String parent = null;
              String parentParent = null;
              if (qualifier != null) {
                int index = qualifier.lastIndexOf('.');
                if (index != -1) {
                  parent = qualifier.substring(index + 1);
                  parentParent = qualifier.substring(0, index);
                  index = parentParent.lastIndexOf('.');
                  if (index != -1) {
                    parentParent = parentParent.substring(index + 1);
                  }
                } else {
                  parent = qualifier;
                }
              }
              if (parentParent == null) {
                // Didn't get both parent and parentParent from qualified name in call; look at surrounding closures
                // for one or two names.
                GrClosableBlock closure = getParentClosure(call);
                if (closure != null) {
                  List<String> names = getClosureNames(closure);
                  int size = names.size();
                  if (parent != null) {
                    if (size >= 1) {
                      parentParent = names.get(0);
                    }
                  } else if (size >= 1) {
                    parent = names.get(0);
                    if (size >= 2) {
                      parentParent = names.get(1);
                    }
                  }
                }
              }

              Map<String, String> namedArguments = Maps.newHashMap();
              List<String> unnamedArguments = new ArrayList<>();
              extractMethodCallArguments(call, unnamedArguments, namedArguments);

              // PSI Groovy isn't treating the closure as a parameter like it is in Kotlin
              PsiElement child = call.getLastChild();
              if (child instanceof GrClosableBlock) {
                unnamedArguments.add(child.getText());
              }

              for (GradleScanner detector : detectors) {
                detector.checkMethodCall(context, methodName, parent, parentParent, namedArguments,
                                         unnamedArguments, call);
              }
            }

            super.visitMethodCall(call);
          }

          @Override
          public void visitClosure(@NotNull GrClosableBlock closure) {
            List<String> closureNames = getClosureNames(closure);
            if (!closureNames.isEmpty()) {
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
            Map<String, String> namedArguments = Maps.newHashMap();
            List<String> unnamedArguments = new ArrayList<>();
            extractMethodCallArguments(applicationStatement, unnamedArguments, namedArguments);
            if (parentNames.isEmpty() && unnamedArguments.size() == 1 && namedArguments.isEmpty()) {
              // This might be a top-level application statement for Dsl property assignment with embedded hierarchy
              GrExpression invokedExpression = applicationStatement.getInvokedExpression();
              if (invokedExpression instanceof GrReferenceExpression) {
                GrReferenceExpression referenceExpression = (GrReferenceExpression) invokedExpression;
                List<String> names = getReferenceExpressionNames(referenceExpression);
                String name = !names.isEmpty() ? names.get(0) : null;
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
                String name = !names.isEmpty() ? names.get(0) : null;
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
