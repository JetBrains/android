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

import com.android.resources.ResourceEnum;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

public class RestrictedEnum implements RestrictedQualifier {

  private final Collection<ResourceEnum> myPossibleValues;
  private final ImmutableList<ResourceEnum> myAllValues;

  public RestrictedEnum(@NotNull Class<? extends ResourceEnum> enumClass) {
    myPossibleValues = Lists.newArrayList(Arrays.asList(enumClass.getEnumConstants()));
    myAllValues = ImmutableList.copyOf(myPossibleValues);
  }

  @NotNull
  private static ResourceEnum getValue(@NotNull ResourceQualifier qualifier) {
    return (ResourceEnum)QualifierUtils.getValue(qualifier);
  }

  @Override
  public void setRestrictions(@Nullable ResourceQualifier compatible, @NotNull Collection<ResourceQualifier> incompatibles) {
    if (compatible != null) {
      myPossibleValues.clear();
      myPossibleValues.add(getValue(compatible));
    } else {
      for (ResourceQualifier qualifier : incompatibles) {
        myPossibleValues.remove(getValue(qualifier));
      }
    }
  }

  @Override
  public boolean isMatchFor(@Nullable ResourceQualifier qualifier) {
    if (qualifier == null) {
      return false;
    }
    return myPossibleValues.contains(getValue(qualifier));
  }

  @Override
  public boolean isEmpty() {
    return myPossibleValues.isEmpty();
  }

  @Override
  @Nullable("if there is no boundary for a value")
  public Object getAny() {
    assert !myPossibleValues.isEmpty();
    if (myAllValues.size() == myPossibleValues.size()) {
      return null;
    }
    return myPossibleValues.iterator().next();
  }
}
