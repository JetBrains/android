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
package org.jetbrains.android.inspections;

import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.android.inspections.ResourceTypeInspection.InspectionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RangeAllowedValues extends ResourceTypeInspection.Constraints {
  @NotNull
  public String describe(@Nullable PsiExpression argument) {
    assert false;
    return "";
  }

  public InspectionResult isValid(@NotNull PsiExpression argument) {
    return InspectionResult.uncertain();
  }

  @Nullable
  protected Number guessSize(@NotNull PsiExpression argument) {
    if (argument instanceof PsiLiteral) {
      PsiLiteral literal = (PsiLiteral)argument;
      Object v = literal.getValue();
      if (v instanceof Number) {
        return (Number)v;
      }
    } else if (argument instanceof PsiBinaryExpression) {
      Object v = JavaConstantExpressionEvaluator.computeConstantExpression(argument, false);
      if (v instanceof Number) {
        return (Number)v;
      }
    } else if (argument instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)argument;
      PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField)resolved;
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          Number number = guessSize(initializer);
          if (number != null) {
            // If we're surrounded by an if check involving the variable, then don't validate
            // based on the initial value since it might be clipped to a valid range
            PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(argument, PsiIfStatement.class, true);
            if (ifStatement != null) {
              PsiExpression condition = ifStatement.getCondition();
              if (comparesReference(resolved, condition)) {
                return null;
              }
            }
          }
          return number;
        }
      } else if (resolved instanceof PsiLocalVariable) {
        PsiLocalVariable variable = (PsiLocalVariable) resolved;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          Number number = guessSize(initializer);
          if (number != null) {
            // If we're surrounded by an if check involving the variable, then don't validate
            // based on the initial value since it might be clipped to a valid range
            PsiIfStatement ifStatement = PsiTreeUtil.getParentOfType(argument, PsiIfStatement.class, true);
            if (ifStatement != null) {
              PsiExpression condition = ifStatement.getCondition();
              if (comparesReference(resolved, condition)) {
                return null;
              }
            }
          }
          return number;
        }
      }
    } else if (argument instanceof PsiPrefixExpression) {
      PsiPrefixExpression prefix = (PsiPrefixExpression)argument;
      if (prefix.getOperationTokenType() == JavaTokenType.MINUS) {
        PsiExpression operand = prefix.getOperand();
        if (operand != null) {
          Number number = guessSize(operand);
          if (number != null) {
            if (number instanceof Long) {
              return -number.longValue();
            } else if (number instanceof Integer) {
              return -number.intValue();
            } else if (number instanceof Double) {
              return -number.doubleValue();
            } else if (number instanceof Float) {
              return -number.floatValue();
            } else if (number instanceof Short) {
              return -number.shortValue();
            } else if (number instanceof Byte) {
              return -number.byteValue();
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Checks whether the given range is compatible with this one.
   * We err on the side of caution. E.g. if we have
   * <pre>
   *    method(x)
   * </pre>
   * and the parameter declaration says that x is between 0 and 10,
   * and then we have a parameter which is known to be in the range 5 to 15,
   * here we consider this a compatible range; we don't flag this as
   * an error. If however, the ranges don't overlap, *then* we complain.
   */
  public InspectionResult contains(@NotNull RangeAllowedValues other) {
    return InspectionResult.uncertain();
  }

  private static boolean comparesReference(@NotNull PsiElement reference, @Nullable PsiExpression expression) {
    if (expression instanceof PsiBinaryExpression) {
      PsiBinaryExpression binary = (PsiBinaryExpression)expression;
      IElementType tokenType = binary.getOperationTokenType();
      if (tokenType == JavaTokenType.GE ||
          tokenType == JavaTokenType.GT ||
          tokenType == JavaTokenType.LT ||
          tokenType == JavaTokenType.LE ||
          tokenType == JavaTokenType.EQ) {
        PsiExpression lOperand = binary.getLOperand();
        PsiExpression rOperand = binary.getROperand();
        if (lOperand instanceof PsiReferenceExpression) {
          return reference.equals(((PsiReferenceExpression)lOperand).resolve());
        }
        if (rOperand instanceof PsiReferenceExpression) {
          return reference.equals(((PsiReferenceExpression)rOperand).resolve());
        }
      } else if (tokenType == JavaTokenType.ANDAND || tokenType == JavaTokenType.OROR) {
        return comparesReference(reference, binary.getLOperand()) || comparesReference(reference, binary.getROperand());
      }
    }

    return false;
  }
}
