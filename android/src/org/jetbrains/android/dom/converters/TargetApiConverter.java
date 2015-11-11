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
package org.jetbrains.android.dom.converters;

import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.SdkVersionInfo;
import com.google.common.base.Strings;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Range;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

import static com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_API;

public class TargetApiConverter extends ResolvingConverter<Integer> {
  private final Set<Integer> myAllVariants;

  public TargetApiConverter() {
    Range<Integer> range = Range.closed(1, HIGHEST_KNOWN_API);
    myAllVariants = ContiguousSet.create(range, DiscreteDomain.integers());
  }

  @Nullable
  @Override
  public LookupElement createLookupElement(Integer api) {
    if (api == null) {
      return null;
    }

    String lookupString = SdkVersionInfo.getBuildCode(api);
    if (lookupString == null) {
      return null;
    }

    StringBuilder typeText = new StringBuilder();

    // Since API 23, short one-letter names are used as build codes.
    // Because of that, we want to add at least a hint on what is the full code name
    // of that API level.
    if (lookupString.length() == 1) {
      String codeName = SdkVersionInfo.getCodeName(api);
      if (codeName != null) {
        typeText.append(codeName).append(", ");
      }
    }
    typeText.append(api);

    // Using PrioritizedLookupElement to show API levels from latest to oldest
    return PrioritizedLookupElement.withPriority(LookupElementBuilder.create(lookupString).withTypeText(typeText.toString(), true).withCaseSensitivity(false), api);
  }

  @Nullable
  @Override
  public PsiElement resolve(Integer o, ConvertContext context) {
    return super.resolve(o, context);
  }

  @Override
  public String getErrorMessage(@Nullable String s, ConvertContext context) {
    if (Strings.isNullOrEmpty(s)) {
      return "Value shouldn't be empty";
    }

    try {
      int api = Integer.parseInt(s);
      if (!isLegalApi(api, context)) {
        return String.format("%d is not a valid API level", api);
      }
    }
    catch (NumberFormatException e) {
      // not a number, do a lookup by name
      int buildCode = SdkVersionInfo.getApiByBuildCode(s, false);
      if (buildCode == -1) {
        return "Value is neither valid build code nor an integer";
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Integer fromString(@Nullable @NonNls String s, ConvertContext context) {
    if (s == null) {
      return null;
    }

    try {
      int api = Integer.parseInt(s);

      // Succeed if only API number is legal
      return isLegalApi(api, context) ? api : null;
    }
    catch (NumberFormatException e) {
      int buildCode = SdkVersionInfo.getApiByBuildCode(s, false);
      return buildCode == -1 ? null : buildCode;
    }
  }

  private static boolean isLegalApi(int api, @NotNull ConvertContext context) {
    return api >= 1 && api <= getHighestKnownApi(context);
  }

  private static int getHighestKnownApi(@NotNull ConvertContext context) {
    Module module = context.getModule();
    if (module == null) {
      return HIGHEST_KNOWN_API;
    }

    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet == null) {
      return HIGHEST_KNOWN_API;
    }

    IAndroidTarget apiTarget = facet.getConfigurationManager().getHighestApiTarget();
    if (apiTarget == null) {
      return HIGHEST_KNOWN_API;
    }

    return Math.max(apiTarget.getVersion().getApiLevel(), HIGHEST_KNOWN_API);
  }

  @Nullable
  @Override
  public String toString(@Nullable Integer s, ConvertContext context) {
    if (s == null) {
      return null;
    }

    return SdkVersionInfo.getBuildCode(s);
  }

  @NotNull
  @Override
  public Collection<Integer> getVariants(ConvertContext context) {
    return myAllVariants;
  }
}
