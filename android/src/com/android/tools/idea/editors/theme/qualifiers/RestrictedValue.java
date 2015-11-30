/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.qualifiers;

import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.android.ide.common.resources.configuration.VersionQualifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class RestrictedValue implements RestrictedQualifier {
  private int myMinValue;
  private int myMaxValue;

  RestrictedValue() {
    myMinValue = 0;
    myMaxValue = Integer.MAX_VALUE;
  }

  RestrictedValue(int minValue, int maxValue) {
    myMinValue = minValue;
    myMaxValue = maxValue;
  }

  private static int getValue(ResourceQualifier qualifier) {
    if (qualifier instanceof VersionQualifier) {
      return ((VersionQualifier)qualifier).getVersion();
    }
    return ((Integer)QualifierUtils.getValue(qualifier)).intValue();
  }

  @Override
  public void setRestrictions(@Nullable ResourceQualifier compatible, @NotNull Collection<ResourceQualifier> incompatibles) {
    if (compatible != null) {
      myMinValue = getValue(compatible);
    }
    for (ResourceQualifier qualifier : incompatibles) {
      int value = getValue(qualifier);
      if (value > myMinValue) {
        myMaxValue = Math.min(myMaxValue, value - 1);
      }
    }
  }

  @Override
  public boolean isMatchFor(@Nullable ResourceQualifier qualifier) {
    if (qualifier == null) {
      return false;
    }
    int value = getValue(qualifier);
    return myMinValue <= value && value <= myMaxValue;
  }

  @Override
  public boolean isEmpty() {
    return myMinValue > myMaxValue;
  }

  @Override
  @Nullable("if there is no boundary for a value")
  public Object getAny() {
    if (myMinValue == 0 && myMaxValue == Integer.MAX_VALUE) {
      return null;
    }
    if (myMaxValue != Integer.MAX_VALUE) {
      return Integer.valueOf(myMaxValue);
    }
    return Integer.valueOf(myMinValue);
  }

  @Nullable
  @Override
  public RestrictedQualifier intersect(@NotNull RestrictedQualifier otherRestricted) {
    assert otherRestricted instanceof RestrictedValue;
    int resultMinValue = Math.max(myMinValue, ((RestrictedValue)otherRestricted).myMinValue);
    int resultMaxValue = Math.min(myMaxValue, ((RestrictedValue)otherRestricted).myMaxValue);

    if (resultMinValue > resultMaxValue) {
      return null;
    }
    return new RestrictedValue(resultMinValue, resultMaxValue);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RestrictedValue that = (RestrictedValue)o;

    if (myMinValue != that.myMinValue) return false;
    if (myMaxValue != that.myMaxValue) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myMinValue;
    result = 31 * result + myMaxValue;
    return result;
  }
}
