/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.lint;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.SdkMavenRepository;
import com.android.repository.api.RemotePackage;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.progress.StudioLoggerProgressIndicator;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.client.api.LintClient;
import com.android.tools.lint.detector.api.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.android.SdkConstants.ATTR_MIN_SDK_VERSION;
import static com.android.SdkConstants.ATTR_TARGET_SDK_VERSION;

public class LintIdeGradleDetector extends GradleDetector {
  static final Implementation IMPLEMENTATION = new Implementation(
    LintIdeGradleDetector.class,
    Scope.GRADLE_SCOPE);

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

  @Override
  @Nullable
  protected GradleVersion getHighestKnownVersion(@NonNull LintClient client, @NonNull GradleCoordinate coordinate,
                                                 @Nullable Predicate<GradleVersion> filter) {
    AndroidSdkHandler sdkHandler = client.getSdk();
    if (sdkHandler == null) {
      return null;
    }
    StudioLoggerProgressIndicator logger = new StudioLoggerProgressIndicator(getClass());
    RemotePackage sdkPackage = SdkMavenRepository.findLatestRemoteVersion(coordinate, sdkHandler, filter, logger);
    if (sdkPackage != null) {
      GradleCoordinate found = SdkMavenRepository.getCoordinateFromSdkPath(sdkPackage.getPath());
      if (found != null) {
        return found.getVersion();
      }
    }

    return null;
  }

  @Override
  public void visitBuildScript(@NonNull final Context context) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        if (context instanceof JavaContext) {
          handleGradleKotlinScript((JavaContext)context);
          return;
        }

        final PsiFile psiFile = LintIdeUtils.getPsiFile(context);
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
            if (parentName != null && isInterestingBlock(parentName, parentParentName)) {
              for (PsiElement element : closure.getChildren()) {
                if (element instanceof GrApplicationStatement) {
                  GrApplicationStatement call = (GrApplicationStatement)element;
                  GrExpression propertyExpression = call.getInvokedExpression();
                  GrCommandArgumentList argumentList = call.getArgumentList();
                  if (propertyExpression instanceof GrReferenceExpression) {
                    GrReferenceExpression propertyRef = (GrReferenceExpression)propertyExpression;
                    String property = propertyRef.getReferenceName();
                    //noinspection ConstantConditions
                    if (property != null && isInterestingProperty(property, parentName, parentParentName)
                          && argumentList != null) {
                      String value = argumentList.getText();
                      checkDslPropertyAssignment(context, property, value, parentName, parentParentName, argumentList, call);
                    }
                  }
                } else if (element instanceof GrAssignmentExpression) {
                  GrAssignmentExpression assignment = (GrAssignmentExpression)element;
                  GrExpression lValue = assignment.getLValue();
                  if (lValue instanceof GrReferenceExpression) {
                    GrReferenceExpression propertyRef = (GrReferenceExpression)lValue;
                    String property = propertyRef.getReferenceName();
                    if (property != null && isInterestingProperty(property, parentName, parentParentName)) {
                      GrExpression rValue = assignment.getRValue();
                      if (rValue != null) {
                        String value = rValue.getText();
                        checkDslPropertyAssignment(context, property, value, parentName, parentParentName, rValue, assignment);

                        // As of 0.11 you can't use assignment for these two properties. This is handled here rather
                        // than up in GradleDetector for a couple of reasons: The project won't compile with that
                        // error, so gradle from the command line won't get invoked. Second, we want to do some unusual
                        // things with the positions here (map between two nodes), and the property abstraction we
                        // pass to GradleDetector doesn't distinguish between assignments and DSL method calls, so just
                        // handle it here.
                        if (property.equals(ATTR_MIN_SDK_VERSION) || property.equals(ATTR_TARGET_SDK_VERSION)) {
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
            String statementName = applicationStatement.getInvokedExpression().getText();
            if (isInterestingStatement(statementName, parentName)) {
              GrCommandArgumentList argumentList = applicationStatement.getArgumentList();
              Map<String, String> namedArguments = Maps.newHashMap();
              List<String> unnamedArguments = Lists.newArrayList();
              for (GroovyPsiElement groovyPsiElement : argumentList.getAllArguments()) {
                if (groovyPsiElement instanceof GrNamedArgument) {
                  GrNamedArgument namedArgument = (GrNamedArgument)groovyPsiElement;
                  GrExpression expression = namedArgument.getExpression();
                  if (expression == null || !(expression instanceof GrLiteral)) {
                    continue;
                  }
                  Object value = ((GrLiteral)expression).getValue();
                  if (value == null) {
                    continue;
                  }
                  namedArguments.put(namedArgument.getLabelName(), value.toString());
                } else if (groovyPsiElement instanceof GrExpression) {
                  unnamedArguments.add(groovyPsiElement.getText());
                }
              }
              checkMethodCall(context, statementName, parentName, namedArguments, unnamedArguments, applicationStatement);
            }
            super.visitApplicationStatement(applicationStatement);
          }
        });
      }
    });
  }

  @Override
  protected int getStartOffset(@NonNull Context context, @NonNull Object cookie) {
    int startOffset = super.getStartOffset(context, cookie);
    if (startOffset != -1) {
      return startOffset;
    }

    PsiElement element = (PsiElement)cookie;
    TextRange textRange = element.getTextRange();
    return textRange.getStartOffset();
  }

  @NonNull
  @Override
  protected Object getPropertyPairCookie(@NonNull Object cookie) {
    PsiElement element = (PsiElement)cookie;
    return element.getParent();
  }

  @NonNull
  @Override
  protected Object getPropertyKeyCookie(@NonNull Object cookie) {
    PsiElement element = (PsiElement)cookie;
    PsiElement parent = element.getParent();
    if (parent instanceof GrApplicationStatement) {
      GrApplicationStatement call = (GrApplicationStatement)parent;
      return call.getInvokedExpression();
    } else if (parent instanceof GrAssignmentExpression) {
      GrAssignmentExpression assignment = (GrAssignmentExpression)parent;
      return assignment.getLValue();
    }

    return super.getPropertyKeyCookie(cookie);
  }

  @Override
  protected Location createLocation(@NonNull Context context, @NonNull Object cookie) {
    Location location = super.createLocation(context, cookie);
    if (location != null) {
      return location;
    }

    PsiElement element = (PsiElement)cookie;
    TextRange textRange = element.getTextRange();
    int start = textRange.getStartOffset();
    int end = textRange.getEndOffset();
    location = Location.create(context.file, new DefaultPosition(-1, -1, start), new DefaultPosition(-1, -1, end));
    location.setSource(element);
    return location;
  }
}
