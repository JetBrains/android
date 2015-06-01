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
package com.android.tools.idea.structure.services;

import org.jetbrains.annotations.NotNull;

import static com.google.common.base.CaseFormat.UPPER_CAMEL;
import static com.google.common.base.CaseFormat.UPPER_UNDERSCORE;

/**
 * The valid list of categories a developer service can be a part of.
 */
public enum ServiceCategory {

  ADS,
  ANALYTICS,
  AUTHENTICATION,
  CLOUD,
  FITNESS,
  GAMES,
  GEO_LOCATION("Geo/Location"),
  LOCALIZATION,
  MARKETING,
  MEDIA,
  NOTIFICATIONS,
  PAYMENTS,
  SOCIAL;

  @NotNull private final String myDisplayName;

  ServiceCategory() {
    myDisplayName = UPPER_UNDERSCORE.to(UPPER_CAMEL, name());
  }

  ServiceCategory(@NotNull final String displayName) {
    myDisplayName = displayName;
  }

  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }
}
