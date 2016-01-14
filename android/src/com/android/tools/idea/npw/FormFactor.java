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
  public static final FormFactor MOBILE = new FormFactor("Mobile", DeviceMenuAction.FormFactor.MOBILE, "Phone and Tablet", 15, Lists
    .newArrayList("20", "google_gdk", "google_apis", "google_tv_addon", "Glass", "Google APIs"), null, 0, null);
  // TODO: in the future instead of whitelisting maybe we could determine this by availability of system images.
  public static final FormFactor WEAR = new FormFactor("Wear", DeviceMenuAction.FormFactor.WEAR, "Wear", 21,
                                                       Lists.newArrayList("google_apis"), Lists.newArrayList("20", "21", "22"), 1, null);
  public static final FormFactor TV = new FormFactor("TV", DeviceMenuAction.FormFactor.TV, "TV", 21,
                                                     Lists.newArrayList("20", "google_apis", "google_gdk"), null, 2, null);
  public static final FormFactor CAR = new FormFactor("Car", DeviceMenuAction.FormFactor.CAR, "Android Auto", 21,
                                                      null, null, 3, MOBILE);

  public static final FormFactor GLASS = new FormFactor("Glass", DeviceMenuAction.FormFactor.GLASS, "Glass", 19,
                                                        null, Lists.newArrayList("Glass", "google_gdk"), 4, null);

  private static final Map<String, FormFactor> myFormFactors = new ImmutableMap.Builder<String, FormFactor>()
      .put(MOBILE.id, MOBILE)
      .put(WEAR.id, WEAR)
      .put(TV.id, TV)
      .put(CAR.id, CAR)
      .put(GLASS.id, GLASS).build();

  public final String id;
  @Nullable private String myDisplayName;
  public final int defaultApi;
  @NotNull private final List<String> myApiBlacklist;
  @NotNull private final List<String> myApiWhitelist;
  @NotNull private final DeviceMenuAction.FormFactor myEnumValue;
  private final int relativeOrder;
  @Nullable public final FormFactor baseFormFactor;

  FormFactor(@NotNull String id, @NotNull DeviceMenuAction.FormFactor enumValue, @Nullable String displayName,
             int defaultApi, @Nullable List<String> apiBlacklist, @Nullable List<String> apiWhitelist,
             int relativeOrder, @Nullable FormFactor baseFormFactor) {
    this.id = id;
    myEnumValue = enumValue;
    myDisplayName = displayName;
    this.defaultApi = defaultApi;
    this.relativeOrder = relativeOrder;
    myApiBlacklist = apiBlacklist != null ? apiBlacklist : Collections.<String>emptyList();
    myApiWhitelist = apiWhitelist != null ? apiWhitelist : Collections.<String>emptyList();
    this.baseFormFactor = baseFormFactor;
  }

  @Nullable
  public static FormFactor get(@NotNull String id) {
    if (myFormFactors.containsKey(id)) {
      return myFormFactors.get(id);

    }
    return new FormFactor(id, DeviceMenuAction.FormFactor.MOBILE, id, 1, null, null, myFormFactors.size(), null);
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
  List<String> getApiBlacklist() {
    return myApiBlacklist;
  }

  @NotNull
  List<String> getApiWhitelist() {
    return myApiWhitelist;
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
    return relativeOrder - formFactor.relativeOrder;
  }
}
