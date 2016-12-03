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
import com.intellij.psi.PsiLiteral;
import org.jetbrains.android.inspections.ResourceTypeInspection.InspectionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.tools.lint.checks.SupportAnnotationDetector.*;
import static org.jetbrains.android.inspections.ResourceTypeInspection.getBooleanValue;
import static org.jetbrains.android.inspections.ResourceTypeInspection.getDoubleValue;

class FloatRangeConstraint extends RangeAllowedValues {
  final double from;
  final double to;
  final boolean fromInclusive;
  final boolean toInclusive;

  public FloatRangeConstraint(@NotNull PsiAnnotation annotation) {
    PsiAnnotationMemberValue fromValue = annotation.findDeclaredAttributeValue(ATTR_FROM);
    PsiAnnotationMemberValue toValue = annotation.findDeclaredAttributeValue(ATTR_TO);
    PsiAnnotationMemberValue fromInclusiveValue = annotation.findDeclaredAttributeValue(ATTR_FROM_INCLUSIVE);
    PsiAnnotationMemberValue toInclusiveValue = annotation.findDeclaredAttributeValue(ATTR_TO_INCLUSIVE);
    from = getDoubleValue(fromValue, Double.NEGATIVE_INFINITY);
    to = getDoubleValue(toValue, Double.POSITIVE_INFINITY);
    fromInclusive = getBooleanValue(fromInclusiveValue, true);
    toInclusive = getBooleanValue(toInclusiveValue, true);
  }

  @VisibleForTesting
  FloatRangeConstraint(double from, double to, boolean fromInclusive, boolean toInclusive) {
    this.from = from;
    this.to = to;
    this.fromInclusive = fromInclusive;
    this.toInclusive = toInclusive;
  }

  @Override
  public InspectionResult isValid(@NotNull PsiExpression argument) {
    Number number = guessSize(argument);
    if (number != null) {
      double value = number.doubleValue();
      if (!((fromInclusive && value >= from || !fromInclusive && value > from) &&
            (toInclusive && value <= to || !toInclusive && value < to))) {
        return InspectionResult.invalid(argument);
      }
      return InspectionResult.valid();
    }
    return InspectionResult.uncertain();
  }

  @NotNull
  @Override
  public String describe(@Nullable PsiExpression argument) {
    StringBuilder sb = new StringBuilder(20);
    if (from != Double.NEGATIVE_INFINITY) {
      if (to != Double.POSITIVE_INFINITY) {
        sb.append("Value must be ");
        if (fromInclusive) {
          sb.append('\u2265'); // >= sign
        } else {
          sb.append('>');
        }
        sb.append(' ');
        sb.append(Double.toString(from));
        sb.append(" and ");
        if (toInclusive) {
          sb.append('\u2264'); // <= sign
        } else {
          sb.append('<');
        }
        sb.append(' ');
        sb.append(Double.toString(to));
      } else {
        sb.append("Value must be ");
        if (fromInclusive) {
          sb.append('\u2265'); // >= sign
        } else {
          sb.append('>');
        }
        sb.append(' ');
        sb.append(Double.toString(from));
      }
    } else if (to != Double.POSITIVE_INFINITY) {
      sb.append("Value must be ");
      if (toInclusive) {
        sb.append('\u2264'); // <= sign
      } else {
        sb.append('<');
      }
      sb.append(' ');
      sb.append(Double.toString(to));
    }
    if (argument != null) {
      Number actual = guessSize(argument);
      if (actual != null) {
        sb.append(" (was ");
        // Try to avoid going through an actual double which introduces
        // potential rounding ugliness (e.g. source can say "2.49f" and this is printed as "2.490000009536743")
        if (argument instanceof PsiLiteral) {
          PsiLiteral literal = (PsiLiteral)argument;
          sb.append(literal.getText());
        }
        else {
          sb.append(Double.toString(actual.doubleValue()));
        }
        sb.append(')');
      }
    }
    return sb.toString();
  }

  @Override
  public InspectionResult contains(@NotNull RangeAllowedValues other) {
    if (other instanceof FloatRangeConstraint) {
      FloatRangeConstraint otherRange = (FloatRangeConstraint)other;
      if (otherRange.from < from || otherRange.to > to) {
        return InspectionResult.invalidWithoutNode();
      }
      if (!fromInclusive && otherRange.fromInclusive && otherRange.from == from) {
        return InspectionResult.invalidWithoutNode();
      }
      if (!toInclusive && otherRange.toInclusive && otherRange.to == to) {
        return InspectionResult.invalidWithoutNode();
      }
      return InspectionResult.valid();
    } else if (other instanceof IntRangeConstraint) {
      IntRangeConstraint otherRange = (IntRangeConstraint)other;
      if (otherRange.from < from || otherRange.to > to) {
        return InspectionResult.invalidWithoutNode();
      }
      if (!fromInclusive && otherRange.from == from) {
        return InspectionResult.invalidWithoutNode();
      }
      if (!toInclusive && otherRange.to == to) {
        return InspectionResult.invalidWithoutNode();
      }
      return InspectionResult.valid();
    }
    return InspectionResult.uncertain();
  }

  @Override
  public String toString() {
    return describe(null);
  }
}
