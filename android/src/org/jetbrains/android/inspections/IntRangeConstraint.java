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
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiExpression;
import org.jetbrains.android.inspections.ResourceTypeInspection.InspectionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_FROM;
import static com.android.tools.lint.checks.SupportAnnotationDetector.ATTR_TO;
import static org.jetbrains.android.inspections.ResourceTypeInspection.getLongValue;

class IntRangeConstraint extends RangeAllowedValues {
  final long from;
  final long to;

  public IntRangeConstraint(@NotNull PsiAnnotation annotation) {
    PsiAnnotationMemberValue fromValue = annotation.findDeclaredAttributeValue(ATTR_FROM);
    PsiAnnotationMemberValue toValue = annotation.findDeclaredAttributeValue(ATTR_TO);
    from = getLongValue(fromValue, Long.MIN_VALUE);
    to = getLongValue(toValue, Long.MAX_VALUE);
  }

  @VisibleForTesting
  IntRangeConstraint(long from, long to) {
    this.from = from;
    this.to = to;
  }

  @Override
  public InspectionResult isValid(@NotNull PsiExpression argument) {
    Number literalValue = guessSize(argument);
    if (literalValue != null) {
      long value = literalValue.longValue();
      return value >= from && value <= to ? InspectionResult.valid() : InspectionResult
        .invalid(argument);
    }

    return InspectionResult.uncertain();
  }

  @NotNull
  @Override
  public String describe(@Nullable PsiExpression argument) {
    StringBuilder sb = new StringBuilder(20);
    if (to == Long.MAX_VALUE) {
      sb.append("Value must be \u2265 ");
      sb.append(Long.toString(from));
    }
    else if (from == Long.MIN_VALUE) {
      sb.append("Value must be \u2264 ");
      sb.append(Long.toString(to));
    }
    else {
      sb.append("Value must be \u2265 ");
      sb.append(Long.toString(from));
      sb.append(" and \u2264 ");
      sb.append(Long.toString(to));
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
  public String toString() {
    return describe(null);
  }

  @Override
  public InspectionResult contains(@NotNull RangeAllowedValues other) {
    if (other instanceof IntRangeConstraint) {
      IntRangeConstraint otherRange = (IntRangeConstraint)other;
      return otherRange.from >= from && otherRange.to <= to
             ? InspectionResult.valid() : InspectionResult.invalidWithoutNode();
    } else if (other instanceof FloatRangeConstraint) {
      FloatRangeConstraint otherRange = (FloatRangeConstraint)other;
      if (!otherRange.fromInclusive && otherRange.from == (double)from) {
        return InspectionResult.invalidWithoutNode();
      }
      if (!otherRange.toInclusive && otherRange.to == (double)to) {
        return InspectionResult.invalidWithoutNode();
      }
      return otherRange.from >= from && otherRange.to <= to
             ? InspectionResult.valid() : InspectionResult.invalidWithoutNode();
    }
    return InspectionResult.uncertain();
  }
}
