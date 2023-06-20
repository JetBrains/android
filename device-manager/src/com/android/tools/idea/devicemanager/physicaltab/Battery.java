/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.android.tools.idea.devicemanager.Patterns;
import com.google.common.annotations.VisibleForTesting;
import com.ibm.icu.number.LocalizedNumberFormatter;
import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.util.NoUnit;
import com.intellij.openapi.diagnostic.Logger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Battery {
  private static final @NotNull Pattern AC = Pattern.compile(" {2}AC powered: (\\w+)");
  private static final @NotNull Pattern USB = Pattern.compile(" {2}USB powered: (\\w+)");
  private static final @NotNull Pattern WIRELESS = Pattern.compile(" {2}Wireless powered: (\\w+)");
  private static final @NotNull Pattern LEVEL = Pattern.compile(" {2}level: (\\d+)");
  private static final @NotNull Pattern SCALE = Pattern.compile(" {2}scale: (\\d+)");

  private static final @NotNull LocalizedNumberFormatter FORMATTER = NumberFormatter.withLocale(Locale.US)
    .unit(NoUnit.PERCENT);

  private final @NotNull ChargingType myChargingType;
  private final int myLevel;

  @VisibleForTesting
  enum ChargingType {
    NOT_CHARGING, AC, USB, WIRELESS;

    private static @NotNull ChargingType valueOf(boolean ac, boolean usb, boolean wireless) {
      if (ac) {
        return AC;
      }

      if (usb) {
        return USB;
      }

      if (wireless) {
        return WIRELESS;
      }

      return NOT_CHARGING;
    }
  }

  @VisibleForTesting
  Battery(@NotNull ChargingType chargingType, int level) {
    myChargingType = chargingType;
    myLevel = level;
  }

  static @NotNull Optional<Battery> newBattery(@NotNull List<String> output) {
    Optional<Boolean> ac = parseBoolean(AC, output.get(1));
    Optional<Boolean> usb = parseBoolean(USB, output.get(2));
    Optional<Boolean> wireless = parseBoolean(WIRELESS, output.get(3));
    OptionalInt level = Patterns.parseInt(LEVEL, output.get(10));
    OptionalInt scale = Patterns.parseInt(SCALE, output.get(11));

    if (!(ac.isPresent() && usb.isPresent() && wireless.isPresent() && level.isPresent() && scale.isPresent())) {
      return Optional.empty();
    }

    return Optional.of(new Battery(ChargingType.valueOf(ac.get(), usb.get(), wireless.get()), 100 * level.getAsInt() / scale.getAsInt()));
  }

  private static @NotNull Optional<Boolean> parseBoolean(@NotNull Pattern pattern, @NotNull String string) {
    Matcher matcher = pattern.matcher(string);

    if (!matcher.matches()) {
      Logger.getInstance(Battery.class).warn(string);
      return Optional.empty();
    }

    return Optional.of(Boolean.parseBoolean(matcher.group(1)));
  }

  @Override
  public int hashCode() {
    return 31 * myChargingType.hashCode() + myLevel;
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (!(object instanceof Battery)) {
      return false;
    }

    Battery battery = (Battery)object;
    return myChargingType.equals(battery.myChargingType) && myLevel == battery.myLevel;
  }

  @Override
  public @NotNull String toString() {
    switch (myChargingType) {
      case NOT_CHARGING:
        return "Battery " + FORMATTER.format(myLevel);
      case WIRELESS:
        return "Wireless";
      default:
        return myChargingType.toString();
    }
  }
}
