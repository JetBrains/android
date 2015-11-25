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

import com.android.ide.common.resources.configuration.LocaleQualifier;
import com.android.ide.common.resources.configuration.ResourceQualifier;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class RestrictedLocale implements  RestrictedQualifier {
  private String myNeedsToMatch = LocaleQualifier.FAKE_VALUE;
  private final Collection<String> myNeedsToNotMatch = Sets.newHashSet();

  @Override
  public void setRestrictions(@Nullable ResourceQualifier compatible, @NotNull Collection<ResourceQualifier> incompatibles) {
    if (compatible != null) {
      myNeedsToMatch = ((LocaleQualifier)compatible).getValue();
    } else {
      for (ResourceQualifier qualifier : incompatibles) {
        myNeedsToNotMatch.add(((LocaleQualifier)qualifier).getValue());
      }
    }
  }

  @Override
  public boolean isMatchFor(@Nullable ResourceQualifier qualifier) {
    if (qualifier == null) {
      return false;
    }
    String value = ((LocaleQualifier)qualifier).getValue();
    if (LocaleQualifier.FAKE_VALUE.equals(myNeedsToMatch)) {
      return !myNeedsToNotMatch.contains(value);
    } else {
      return value.equals(myNeedsToMatch);
    }
  }

  @Override
  public boolean isEmpty() {
    return myNeedsToMatch == null;
  }

  @Override
  @Nullable("if there is no boundary for a value")
  public Object getAny() {
    assert myNeedsToMatch != null;
    if (LocaleQualifier.FAKE_VALUE.equals(myNeedsToMatch) && myNeedsToNotMatch.isEmpty()) {
      return null;
    }
    return myNeedsToMatch;
  }

  @Nullable
  @Override
  public RestrictedQualifier intersect(@NotNull RestrictedQualifier otherRestrictedQualifier) {
    assert otherRestrictedQualifier instanceof RestrictedLocale;
    RestrictedLocale otherRestrictedLocale = (RestrictedLocale)otherRestrictedQualifier;

    RestrictedLocale result = new RestrictedLocale();
    result.myNeedsToNotMatch.addAll(myNeedsToNotMatch);
    result.myNeedsToNotMatch.addAll(otherRestrictedLocale.myNeedsToNotMatch);

    if (!otherRestrictedLocale.myNeedsToMatch.equals(LocaleQualifier.FAKE_VALUE)) {
      result.myNeedsToMatch = otherRestrictedLocale.myNeedsToMatch;
    }

    if (!myNeedsToMatch.equals(LocaleQualifier.FAKE_VALUE)) {
      if (!result.myNeedsToMatch.equals(LocaleQualifier.FAKE_VALUE) && !result.myNeedsToMatch.equals(myNeedsToMatch)) {
        return null;
      }
      result.myNeedsToMatch = myNeedsToMatch;
    }

    if (result.myNeedsToMatch.equals(LocaleQualifier.FAKE_VALUE)) {
      return result;
    }

    if (result.myNeedsToNotMatch.contains(result.myNeedsToMatch)) {
      return null;
    }
    result.myNeedsToNotMatch.clear();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    RestrictedLocale that = (RestrictedLocale)o;

    if (myNeedsToMatch != null ? !myNeedsToMatch.equals(that.myNeedsToMatch) : that.myNeedsToMatch != null) return false;
    if (!myNeedsToNotMatch.equals(that.myNeedsToNotMatch)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myNeedsToMatch != null ? myNeedsToMatch.hashCode() : 0;
    result = 31 * result + myNeedsToNotMatch.hashCode();
    return result;
  }
}
