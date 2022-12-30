// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.android.inspections;

import static com.android.tools.lint.detector.api.VersionChecks.SDK_INT;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.sdklib.SdkVersionInfo;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;

/**
 * Utility methods for checking whether a given element is surrounded (or preceded!) by
 * an API check using SDK_INT (or other version checking utilities such as BuildCompat#isAtLeastN)
 * <p>
 * This is a copy of {@link com.android.tools.lint.checks.VersionChecks}, but applies
 * to PSI elements instead of UAST elements.
 */
public final class VersionChecks {
  private interface ApiLevelLookup {
    int getApiLevel(@NonNull PsiElement element);
  }

  public static boolean isPrecededByVersionCheckExit(PsiElement element, int api) {
    PsiElement current = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
    if (current != null) {
      PsiElement prev = getPreviousStatement(current);
      if (prev == null) {
        //noinspection unchecked
        current = PsiTreeUtil.getParentOfType(current, PsiStatement.class, true,
                                              PsiMethod.class, PsiClass.class);
      }
      else {
        current = prev;
      }
    }
    while (current != null) {
      if (current instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)current;
        PsiStatement thenBranch = ifStatement.getThenBranch();
        PsiStatement elseBranch = ifStatement.getElseBranch();
        PsiExpression condition = ifStatement.getCondition();
        if (condition != null) {
          if (thenBranch != null) {
            Boolean ok = isVersionCheckConditional(api, condition, true, thenBranch,
                                                   null);
            //noinspection VariableNotUsedInsideIf
            if (ok != null) {
              // See if the body does an immediate return
              if (isUnconditionalReturn(thenBranch)) {
                return true;
              }
            }
          }
          if (elseBranch != null) {
            Boolean ok = isVersionCheckConditional(api, condition, false, elseBranch,
                                                   null);

            //noinspection VariableNotUsedInsideIf
            if (ok != null) {
              if (isUnconditionalReturn(elseBranch)) {
                return true;
              }
            }
          }
        }
      }
      PsiElement prev = getPreviousStatement(current);
      if (prev == null) {
        //noinspection unchecked
        current = PsiTreeUtil.getParentOfType(current, PsiStatement.class, true,
                                              PsiMethod.class, PsiClass.class);
        if (current == null) {
          return false;
        }
      }
      else {
        current = prev;
      }
    }

    return false;
  }

  private static boolean isUnconditionalReturn(PsiStatement statement) {
    if (statement instanceof PsiBlockStatement) {
      PsiBlockStatement blockStatement = (PsiBlockStatement)statement;
      PsiCodeBlock block = blockStatement.getCodeBlock();
      PsiStatement[] statements = block.getStatements();
      if (statements.length == 1 && statements[0] instanceof PsiReturnStatement) {
        return true;
      }
    }
    return statement instanceof PsiReturnStatement;
  }

  @Nullable
  public static PsiStatement getPreviousStatement(PsiElement element) {
    final PsiElement prevStatement = PsiTreeUtil.skipSiblingsBackward(element,
                                                                      PsiWhiteSpace.class, PsiComment.class);
    return prevStatement instanceof PsiStatement ? (PsiStatement)prevStatement : null;
  }

  public static boolean isWithinVersionCheckConditional(@NonNull PsiElement element, int api) {
    PsiElement current = PsiUtil.skipParenthesizedExprUp(element.getParent());
    PsiElement prev = element;
    while (current != null) {
      if (current instanceof PsiIfStatement) {
        PsiIfStatement ifStatement = (PsiIfStatement)current;
        PsiExpression condition = ifStatement.getCondition();
        if (prev != condition && condition != null) {
          boolean fromThen = prev == ifStatement.getThenBranch();
          Boolean ok = isVersionCheckConditional(api, condition, fromThen, prev, null);
          if (ok != null) {
            return ok;
          }
        }
      }
      else if (current instanceof PsiConditionalExpression) {
        PsiConditionalExpression ifStatement = (PsiConditionalExpression)current;
        PsiExpression condition = ifStatement.getCondition();
        if (prev != condition) {
          boolean fromThen = prev == ifStatement.getThenExpression();
          Boolean ok = isVersionCheckConditional(api, condition, fromThen, prev, null);
          if (ok != null) {
            return ok;
          }
        }
      }
      else if (current instanceof PsiPolyadicExpression &&
               (isAndedWithConditional(current, api, prev) ||
                isOredWithConditional(current, api, prev))) {
        return true;
      }
      else if (current instanceof PsiMethod || current instanceof PsiFile) {
        return false;
      }
      prev = current;
      current = PsiUtil.skipParenthesizedExprUp(current.getParent());
    }

    return false;
  }

  @Nullable
  private static Boolean isVersionCheckConditional(int api,
                                                   @NonNull PsiElement element, boolean and, @Nullable PsiElement prev,
                                                   @Nullable ApiLevelLookup apiLookup) {
    if (element instanceof PsiPolyadicExpression) {
      if (element instanceof PsiBinaryExpression) {
        Boolean ok = isVersionCheckConditional(api, and, (PsiBinaryExpression)element,
                                               apiLookup);
        if (ok != null) {
          return ok;
        }
      }
      PsiPolyadicExpression expression = (PsiPolyadicExpression)element;
      IElementType tokenType = expression.getOperationTokenType();
      if (and && tokenType == JavaTokenType.ANDAND) {
        if (isAndedWithConditional(element, api, prev)) {
          return true;
        }
      }
      else if (!and && tokenType == JavaTokenType.OROR) {
        if (isOredWithConditional(element, api, prev)) {
          return true;
        }
      }
    }
    else if (element instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression call = (PsiMethodCallExpression)element;
      PsiMethod method = call.resolveMethod();
      if (method == null) {
        return null;
      }
      String name = method.getName();
      if (name.startsWith("isAtLeast")) {
        PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && "android.support.v4.os.BuildCompat".equals(
          containingClass.getQualifiedName())) {
          if (name.equals("isAtLeastN")) {
            return api <= 24;
          }
          else if (name.equals("isAtLeastNMR1")) {
            return api <= 25;
          }
        }
      }
      PsiCodeBlock body = method.getBody();
      if (body == null) {
        return null;
      }
      PsiStatement[] statements = body.getStatements();
      if (statements.length != 1) {
        return null;
      }
      PsiStatement statement = statements[0];
      if (!(statement instanceof PsiReturnStatement)) {
        return null;
      }
      PsiReturnStatement returnStatement = (PsiReturnStatement)statement;
      PsiExpression returnValue = returnStatement.getReturnValue();
      if (returnValue == null) {
        return null;
      }
      PsiExpression[] expressions = call.getArgumentList().getExpressions();
      if (expressions.length == 0) {
        Boolean ok = isVersionCheckConditional(api, returnValue, and,
                                               null, null);
        if (ok != null) {
          return ok;
        }
      }

      if (expressions.length == 1) {
        // See if we're passing in a value
        ApiLevelLookup lookup = arg -> {
          if (arg instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression)arg).resolve();
            if (resolved instanceof PsiParameter) {
              PsiParameter parameter = (PsiParameter)resolved;
              PsiParameterList parameterList = PsiTreeUtil.getParentOfType(resolved,
                                                                           PsiParameterList.class);
              if (parameterList != null) {
                int index = parameterList.getParameterIndex(parameter);
                if (index != -1 && index < expressions.length) {
                  return getApiLevel(expressions[index], null);
                }
              }
            }
          }
          return -1;
        };
        Boolean ok = isVersionCheckConditional(api, returnValue, and, null, lookup);
        if (ok != null) {
          return ok;
        }
      }
    }
    else if (element instanceof PsiReferenceExpression) {
      // Constant expression for an SDK version check?
      PsiReferenceExpression refExpression = (PsiReferenceExpression)element;
      PsiElement resolved = refExpression.resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField)resolved;
        PsiModifierList modifierList = field.getModifierList();
        if (modifierList != null && modifierList.hasExplicitModifier(PsiModifier.STATIC)) {
          PsiExpression initializer = field.getInitializer();
          if (initializer != null) {
            Boolean ok = isVersionCheckConditional(api, initializer, and, null, null);
            if (ok != null) {
              return ok;
            }
          }
        }
      }
    }
    else if (element instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefixExpression = (PsiPrefixExpression)element;
      if (prefixExpression.getOperationTokenType() == JavaTokenType.EXCL) {
        PsiExpression operand = prefixExpression.getOperand();
        if (operand != null) {
          Boolean ok = isVersionCheckConditional(api, operand, !and, null, null);
          if (ok != null) {
            return ok;
          }
        }
      }
    }
    return null;
  }

  private static boolean isSdkInt(@NonNull PsiElement element) {
    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression) element;
      if (SDK_INT.equals(ref.getReferenceName())) {
        return true;
      }
      PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiVariable) {
        PsiExpression initializer = ((PsiVariable) resolved).getInitializer();
        if (initializer != null) {
          return isSdkInt(initializer);
        }
      }
    } else if (element instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression callExpression = (PsiMethodCallExpression) element;
      if ("getBuildSdkInt".equals(callExpression.getMethodExpression().getReferenceName())) {
        return true;
      } // else look inside the body?
    }

    return false;
  }

  @Nullable
  private static Boolean isVersionCheckConditional(int api,
                                                   boolean fromThen,
                                                   @NonNull PsiBinaryExpression binary,
                                                   @Nullable ApiLevelLookup apiLevelLookup) {
    IElementType tokenType = binary.getOperationTokenType();
    if (tokenType == JavaTokenType.GT || tokenType == JavaTokenType.GE ||
        tokenType == JavaTokenType.LE || tokenType == JavaTokenType.LT ||
        tokenType == JavaTokenType.EQEQ) {
      PsiExpression left = binary.getLOperand();
      int level;
      PsiExpression right;
      if (!isSdkInt(left)) {
        right = binary.getROperand();
        if (right != null && isSdkInt(right)) {
          fromThen = !fromThen;
          level = getApiLevel(left, apiLevelLookup);
        }
        else {
          return null;
        }
      }
      else {
        right = binary.getROperand();
        level = getApiLevel(right, apiLevelLookup);
      }
      if (level != -1) {
        if (tokenType == JavaTokenType.GE) {
          // if (SDK_INT >= ICE_CREAM_SANDWICH) { <call> } else { ... }
          return level >= api && fromThen;
        }
        else if (tokenType == JavaTokenType.GT) {
          // if (SDK_INT > ICE_CREAM_SANDWICH) { <call> } else { ... }
          return level >= api - 1 && fromThen;
        }
        else if (tokenType == JavaTokenType.LE) {
          // if (SDK_INT <= ICE_CREAM_SANDWICH) { ... } else { <call> }
          return level >= api - 1 && !fromThen;
        }
        else if (tokenType == JavaTokenType.LT) {
          // if (SDK_INT < ICE_CREAM_SANDWICH) { ... } else { <call> }
          return level >= api && !fromThen;
        }
        else if (tokenType == JavaTokenType.EQEQ) {
          // if (SDK_INT == ICE_CREAM_SANDWICH) { <call> } else {  }
          return level >= api && fromThen;
        }
        else {
          assert false : tokenType;
        }
      }
    }
    return null;
  }

  private static int getApiLevel(
    @Nullable PsiExpression element,
    @Nullable ApiLevelLookup apiLevelLookup) {
    int level = -1;
    if (element instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref2 = (PsiReferenceExpression)element;
      String codeName = ref2.getReferenceName();
      if (codeName != null) {
        level = SdkVersionInfo.getApiByBuildCode(codeName, false);
      }
    }
    else if (element instanceof PsiLiteralExpression) {
      PsiLiteralExpression lit = (PsiLiteralExpression)element;
      Object value = lit.getValue();
      if (value instanceof Integer) {
        level = (Integer)value;
      }
    }
    if (level == -1 && apiLevelLookup != null && element != null) {
      level = apiLevelLookup.getApiLevel(element);
    }
    return level;
  }

  private static boolean isOredWithConditional(PsiElement element, int api,
                                               @Nullable PsiElement before) {
    if (element instanceof PsiBinaryExpression) {
      PsiBinaryExpression inner = (PsiBinaryExpression)element;
      if (inner.getOperationTokenType() == JavaTokenType.OROR) {
        PsiExpression left = inner.getLOperand();

        if (before != left) {
          Boolean ok = isVersionCheckConditional(api, left, false, null, null);
          if (ok != null) {
            return ok;
          }
          PsiExpression right = inner.getROperand();
          if (right != null) {
            ok = isVersionCheckConditional(api, right, false, null, null);
            if (ok != null) {
              return ok;
            }
          }
        }
      }
      Boolean value = isVersionCheckConditional(api, false, inner, null);
      return value != null && value;
    }
    else if (element instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression ppe = (PsiPolyadicExpression)element;
      if (ppe.getOperationTokenType() == JavaTokenType.OROR) {
        for (PsiExpression operand : ppe.getOperands()) {
          if (operand == before) {
            break;
          }
          else if (isOredWithConditional(operand, api, before)) {
            return true;
          }
        }
      }
    }

    return false;
  }

  private static boolean isAndedWithConditional(PsiElement element, int api,
                                                @Nullable PsiElement before) {
    if (element instanceof PsiBinaryExpression) {
      PsiBinaryExpression inner = (PsiBinaryExpression)element;
      if (inner.getOperationTokenType() == JavaTokenType.ANDAND) {
        PsiExpression left = inner.getLOperand();
        if (before != left) {
          Boolean ok = isVersionCheckConditional(api, left, true, null, null);
          if (ok != null) {
            return ok;
          }
          PsiExpression right = inner.getROperand();
          if (right != null) {
            ok = isVersionCheckConditional(api, right, true, null, null);
            if (ok != null) {
              return ok;
            }
          }
        }
      }

      Boolean value = isVersionCheckConditional(api, true, inner, null);
      return value != null && value;
    }
    else if (element instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression ppe = (PsiPolyadicExpression)element;
      if (ppe.getOperationTokenType() == JavaTokenType.ANDAND) {
        for (PsiExpression operand : ppe.getOperands()) {
          if (operand == before) {
            break;
          }
          else if (isAndedWithConditional(operand, api, before)) {
            return true;
          }
        }
      }
    }

    return false;
  }
}
