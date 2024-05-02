/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.avdmanager.ui;

import com.android.sdklib.devices.Device;
import java.util.Arrays;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

enum Category {
  PHONE("Phone", "pixel_fold", definition -> !definition.getIsDeprecated() && Device.isPhone(definition)),
  TABLET("Tablet", "pixel_tablet", definition -> !definition.getIsDeprecated() && Device.isTablet(definition)),
  WEAR_OS("Wear OS", "wearos_square", definition -> !definition.getIsDeprecated() && Device.isWear(definition)),
  DESKTOP("Desktop", "desktop_medium", definition -> !definition.getIsDeprecated() && Device.isDesktop(definition)),
  TV("TV", "tv_1080p", definition -> !definition.getIsDeprecated() && (Device.isTv(definition) || hasTvScreen(definition))),

  AUTOMOTIVE("Automotive", "automotive_1024p_landscape", definition ->
    !definition.getIsDeprecated() && Device.isAutomotive(definition)),

  LEGACY("Legacy", "Nexus S", Device::getIsDeprecated);

  @NotNull
  private final String myName;

  @NotNull
  private final String myDefaultDefinitionId;

  @NotNull
  private final Predicate<Device> myPredicate;

  private static boolean hasTvScreen(@NotNull Device definition) {
    return definition.getDefaultHardware().getScreen().getDiagonalLength() >= Device.MINIMUM_TV_SIZE;
  }

  Category(@NotNull String name, @NotNull String defaultDefinitionId, @NotNull Predicate<Device> predicate) {
    myName = name;
    myDefaultDefinitionId = defaultDefinitionId;
    myPredicate = predicate;
  }

  @NotNull
  final String getDefaultDefinitionId() {
    return myDefaultDefinitionId;
  }

  @NotNull
  static Category valueOfDefinition(@NotNull Device definition) {
    return Arrays.stream(values())
      .filter(category -> category.myPredicate.test(definition))
      .findFirst()
      .orElse(PHONE);
  }

  @NotNull
  @Override
  public String toString() {
    return myName;
  }
}
