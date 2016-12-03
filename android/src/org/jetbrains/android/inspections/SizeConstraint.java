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

import com.android.annotations.VisibleForTesting;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import org.jetbrains.android.inspections.ResourceTypeInspection.InspectionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.ATTR_VALUE;
import static com.android.tools.lint.checks.SupportAnnotationDetector.*;
import static com.intellij.psi.CommonClassNames.JAVA_LANG_STRING;
import static org.jetbrains.android.inspections.ResourceTypeInspection.getLongValue;

class SizeConstraint extends RangeAllowedValues {
  final long exact;
  final long min;
  final long max;
  final long multiple;

  public SizeConstraint(@NotNull PsiAnnotation annotation) {
    PsiAnnotationMemberValue exactValue = annotation.findAttributeValue(ATTR_VALUE);
    PsiAnnotationMemberValue fromValue = annotation.findDeclaredAttributeValue(ATTR_MIN);
    PsiAnnotationMemberValue toValue = annotation.findDeclaredAttributeValue(ATTR_MAX);
    PsiAnnotationMemberValue multipleValue = annotation.findDeclaredAttributeValue(ATTR_MULTIPLE);
    exact = getLongValue(exactValue, -1);
    min = getLongValue(fromValue, Long.MIN_VALUE);
    max = getLongValue(toValue, Long.MAX_VALUE);
    multiple = getLongValue(multipleValue, 1);
  }

  @VisibleForTesting
  SizeConstraint(long exact, long min, long max, long multiple) {
    this.exact = exact;
    this.min = min;
    this.max = max;
    this.multiple = multiple;
  }

  @Override
  public String toString() {
    return describe(null);
  }

  @Override
  public InspectionResult isValid(@NotNull PsiExpression argument) {
    Number size = guessSize(argument);
    if (size == null) {
      return InspectionResult.uncertain();
    }
    int actual = size.intValue();
    if (exact != -1) {
      if (exact != actual) {
        return InspectionResult.invalid(argument);
      }
    } else if (actual < min || actual > max || actual % multiple != 0) {
      return InspectionResult.invalid(argument);
    }

    return InspectionResult.valid();
  }

  @Override
  protected Number guessSize(@NotNull PsiExpression argument) {
    if (argument instanceof PsiNewExpression) {
      PsiNewExpression pne = (PsiNewExpression)argument;
      PsiArrayInitializerExpression initializer = pne.getArrayInitializer();
      if (initializer != null) {
        return initializer.getInitializers().length;
      }
      PsiExpression[] dimensions = pne.getArrayDimensions();
      if (dimensions.length > 0) {
        PsiExpression dimension = dimensions[0];
        return super.guessSize(dimension);
      }
    } else if (argument instanceof PsiLiteral) {
      PsiLiteral literal = (PsiLiteral)argument;
      Object o = literal.getValue();
      if (o instanceof String) {
        return ((String)o).length();
      }
    } else if (argument instanceof PsiBinaryExpression) {
      Object v = JavaConstantExpressionEvaluator.computeConstantExpression(argument, false);
      if (v instanceof String) {
        return ((String)v).length();
      }
    } else if (argument instanceof PsiReferenceExpression) {
      PsiReferenceExpression ref = (PsiReferenceExpression)argument;
      PsiElement resolved = ref.resolve();
      if (resolved instanceof PsiField) {
        PsiField field = (PsiField)resolved;
        PsiExpression initializer = field.getInitializer();
        if (initializer != null) {
          return guessSize(initializer);
        }
      } else if (resolved instanceof PsiLocalVariable) {
        PsiLocalVariable variable = (PsiLocalVariable) resolved;
        PsiExpression initializer = variable.getInitializer();
        if (initializer != null) {
          return guessSize(initializer);
        }
      }
    }
    return null;
  }

  @NotNull
  @Override
  public String describe(@Nullable PsiExpression argument) {
    StringBuilder sb = new StringBuilder(20);
    if (argument != null && argument.getType() != null && argument.getType().getCanonicalText().equals(JAVA_LANG_STRING)) {
      sb.append("Length");
    } else {
      sb.append("Size");
    }
    sb.append(" must be");
    if (exact != -1) {
      sb.append(" exactly ");
      sb.append(Long.toString(exact));
      return sb.toString();
    }
    boolean continued = true;
    if (min != Long.MIN_VALUE && max != Long.MAX_VALUE) {
      sb.append(" at least ");
      sb.append(Long.toString(min));
      sb.append(" and at most ");
      sb.append(Long.toString(max));
    } else if (min != Long.MIN_VALUE) {
      sb.append(" at least ");
      sb.append(Long.toString(min));
    } else  if (max != Long.MAX_VALUE) {
      sb.append(" at most ");
      sb.append(Long.toString(max));
    } else {
      continued = false;
    }
    if (multiple != 1) {
      if (continued) {
        sb.append(" and");
      }
      sb.append(" a multiple of ");
      sb.append(Long.toString(multiple));
    }
    if (argument != null) {
      Number actual = guessSize(argument);
      if (actual != null) {
        sb.append(" (was ").append(Integer.toString(actual.intValue())).append(')');
      }
    }
    return sb.toString();
  }

  @Override
  public InspectionResult contains(@NotNull RangeAllowedValues other) {
    if (other instanceof SizeConstraint) {
      SizeConstraint otherRange = (SizeConstraint)other;
      if (exact != -1 && otherRange.exact != -1) {
        return exact == otherRange.exact ? InspectionResult.valid() : InspectionResult.invalidWithoutNode();
      }
      if (multiple != 1) {
        if (otherRange.exact != -1) {
          if (otherRange.exact % multiple != 0) {
            return InspectionResult.invalidWithoutNode();
          }
        }
        else if (otherRange.multiple % multiple != 0) {
          return InspectionResult.invalidWithoutNode();
        }
      }
      if (otherRange.exact != -1) {
        return otherRange.exact >= min && otherRange.exact <= max
               ? InspectionResult.valid() : InspectionResult.invalidWithoutNode();
      }
      return otherRange.min >= min && otherRange.max <= max
             ? InspectionResult.valid() : InspectionResult.invalidWithoutNode();
    }
    return InspectionResult.uncertain();
  }
}
