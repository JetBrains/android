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
package com.android.tools.idea.npw;

import com.android.sdklib.SdkVersionInfo;
import com.android.sdklib.repositoryv2.IdDisplay;
import com.android.sdklib.repositoryv2.targets.SystemImage;
import com.android.tools.idea.configurations.DeviceMenuAction;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Representations of all Android hardware devices we can target when building an app.
 *
 * TODO: Turn into an enum and combine with {@link DeviceMenuAction.FormFactor}
 */
public final class FormFactor implements Comparable<FormFactor> {
  public static final FormFactor MOBILE = new FormFactor(
    "Mobile", DeviceMenuAction.FormFactor.MOBILE, "Phone and Tablet", 15, 1, SdkVersionInfo.HIGHEST_KNOWN_API, Lists.newArrayList(20),
    Lists.newArrayList(SystemImage.DEFAULT_TAG, SystemImage.GOOGLE_APIS_TAG, SystemImage.GOOGLE_APIS_X86_TAG), 0, null);
  public static final FormFactor WEAR = new FormFactor(
    "Wear", DeviceMenuAction.FormFactor.WEAR, "Wear", 21, SdkVersionInfo.LOWEST_ACTIVE_API_WEAR, SdkVersionInfo.HIGHEST_KNOWN_API_WEAR,
    null, Lists.newArrayList(SystemImage.WEAR_TAG), 1, null);
  public static final FormFactor TV = new FormFactor(
    "TV", DeviceMenuAction.FormFactor.TV, "TV", 21, SdkVersionInfo.LOWEST_ACTIVE_API_TV, SdkVersionInfo.HIGHEST_KNOWN_API_TV,
    null, Lists.newArrayList(SystemImage.TV_TAG), 2, null);
  public static final FormFactor CAR = new FormFactor(
    "Car", DeviceMenuAction.FormFactor.CAR, "Android Auto", 21, 21, 21, null, null, 3, MOBILE);
  public static final FormFactor GLASS = new FormFactor(
    "Glass", DeviceMenuAction.FormFactor.GLASS, "Glass", 19, -1, -1, null, Lists.newArrayList(SystemImage.GLASS_TAG), 4, null);

  private static final Map<String, FormFactor> myFormFactors = new ImmutableMap.Builder<String, FormFactor>()
      .put(MOBILE.id, MOBILE)
      .put(WEAR.id, WEAR)
      .put(TV.id, TV)
      .put(CAR.id, CAR)
      .put(GLASS.id, GLASS).build();

  public final String id;
  @Nullable private String myDisplayName;
  public final int defaultApi;
  @NotNull private final List<Integer> myApiBlacklist;
  @NotNull private final List<IdDisplay> myTags;
  @NotNull private final DeviceMenuAction.FormFactor myEnumValue;
  private final int myRelativeOrder;
  private final int myMinOfflineApiLevel;
  private final int myMaxOfflineApiLevel;
  @Nullable public final FormFactor baseFormFactor;

  FormFactor(@NotNull String id, @NotNull DeviceMenuAction.FormFactor enumValue, @Nullable String displayName,
             int defaultApi, int minOfflineApiLevel, int maxOfflineApiLevel, @Nullable List<Integer> apiBlacklist,
             @Nullable List<IdDisplay> apiTags, int relativeOrder, @Nullable FormFactor baseFormFactor) {
    this.id = id;
    myEnumValue = enumValue;
    myDisplayName = displayName;
    this.defaultApi = defaultApi;
    myMinOfflineApiLevel = minOfflineApiLevel;
    myMaxOfflineApiLevel = Math.min(maxOfflineApiLevel, SdkVersionInfo.HIGHEST_KNOWN_STABLE_API);
    myRelativeOrder = relativeOrder;
    myApiBlacklist = apiBlacklist != null ? apiBlacklist : Collections.<Integer>emptyList();
    myTags = apiTags != null ? apiTags : Collections.<IdDisplay>emptyList();
    this.baseFormFactor = baseFormFactor;
  }

  @Nullable
  public static FormFactor get(@NotNull String id) {
    if (myFormFactors.containsKey(id)) {
      return myFormFactors.get(id);

    }
    return new FormFactor(id, DeviceMenuAction.FormFactor.MOBILE, id, 1, -1, -1, null, null, myFormFactors.size(), null);
  }

  @NotNull
  public DeviceMenuAction.FormFactor getEnumValue() {
    return myEnumValue;
  }

  @Override
  public String toString() {
    return myDisplayName == null ? id : myDisplayName;
  }

  @NotNull
  public Icon getIcon() {
    return myEnumValue.getIcon64();
  }

  @NotNull
  List<Integer> getApiBlacklist() {
    return myApiBlacklist;
  }

  @NotNull
  List<IdDisplay> getTags() {
    return myTags;
  }

  public int getMinOfflineApiLevel() {
    return myMinOfflineApiLevel;
  }

  public int getMaxOfflineApiLevel() {
    return myMaxOfflineApiLevel;
  }

  public static Iterator<FormFactor> iterator() {
    return myFormFactors.values().iterator();
  }

  @Override
  public boolean equals(Object obj) {
    return obj != null && obj instanceof FormFactor && ((FormFactor) obj).id.equals(id);
  }

  @Override
  public int compareTo(FormFactor formFactor) {
    return myRelativeOrder - formFactor.myRelativeOrder;
  }
}
