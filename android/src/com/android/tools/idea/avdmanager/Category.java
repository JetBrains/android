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
package com.android.tools.idea.avdmanager;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.sdklib.devices.Device;
import java.util.Arrays;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;

enum Category {
  PHONE("Phone", "Pixel 2", definition -> !definition.getIsDeprecated() && definition.getTagId() == null && !hasTabletScreen(definition)),
  TABLET("Tablet", "Pixel C", definition -> !definition.getIsDeprecated() && definition.getTagId() == null && hasTabletScreen(definition)),
  WEAR_OS("Wear OS", "Wear OS Square", definition -> !definition.getIsDeprecated() && HardwareConfigHelper.isWear(definition)),
  DESKTOP("Desktop", "Medium Desktop", definition -> !definition.getIsDeprecated() && HardwareConfigHelper.isDesktop(definition)),

  TV("TV", "Television (1080p)", definition ->
    !definition.getIsDeprecated() && (HardwareConfigHelper.isTv(definition) || hasTvScreen(definition))),

  AUTOMOTIVE("Automotive", "Automotive (1024p landscape)", definition ->
    !definition.getIsDeprecated() && HardwareConfigHelper.isAutomotive(definition)),

  LEGACY("Legacy", "Nexus S", Device::getIsDeprecated);

  @NotNull
  private final String myName;

  @NotNull
  private final String myDefaultDefinitionName;

  @NotNull
  private final Predicate<Device> myPredicate;

  private static boolean hasTabletScreen(@NotNull Device definition) {
    var screen = definition.getDefaultHardware().getScreen();
    return screen.getDiagonalLength() >= Device.MINIMUM_TABLET_SIZE && !screen.isFoldable();
  }

  private static boolean hasTvScreen(@NotNull Device definition) {
    return definition.getDefaultHardware().getScreen().getDiagonalLength() >= Device.MINIMUM_TV_SIZE;
  }

  Category(@NotNull String name, @NotNull String defaultDefinitionName, @NotNull Predicate<Device> predicate) {
    myName = name;
    myDefaultDefinitionName = defaultDefinitionName;
    myPredicate = predicate;
  }

  @NotNull
  final String getName() {
    return myName;
  }

  @NotNull
  final String getDefaultDefinitionName() {
    return myDefaultDefinitionName;
  }

  @NotNull
  static Category valueOfDefinition(@NotNull Device definition) {
    return valueOfObject(definition, category -> category.myPredicate.test(definition));
  }

  @NotNull
  static Category valueOfName(@NotNull String name) {
    return valueOfObject(name, category -> category.myName.equals(name));
  }

  @NotNull
  private static Category valueOfObject(@NotNull Object object, @NotNull Predicate<Category> predicate) {
    return Arrays.stream(values())
      .filter(predicate)
      .findFirst()
      .orElseThrow(() -> new IllegalArgumentException(object.toString()));
  }
}
