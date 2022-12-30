/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.res;

import com.android.sdklib.devices.Device;
import com.android.tools.idea.configurations.Configuration;
import com.intellij.openapi.diagnostic.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/**
 * Utilities for dealing with float values in resources.
 */
public final class FloatResources {
  private static final Logger LOG = Logger.getInstance(FloatResources.class);
  private static final String DIMENSION_ERROR_FORMAT = "The specified dimension %1$s does not have a unit";
  private static final Pattern sFloatPattern = Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)(.*)");
  private static final float[] sFloatOut = new float[1];

  private final static UnitEntry[] sUnitNames = new UnitEntry[] {
    new UnitEntry("px",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_PX,  1.0f),
    new UnitEntry("dip", TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_DIP, 1.0f),
    new UnitEntry("dp",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_DIP, 1.0f),
    new UnitEntry("sp",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_SP,  1.0f),
    new UnitEntry("pt",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_PT,  1.0f),
    new UnitEntry("in",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_IN,  1.0f),
    new UnitEntry("mm",  TypedValue.TYPE_DIMENSION, TypedValue.COMPLEX_UNIT_MM,  1.0f)
  };

  private FloatResources() {}

  /**
   * Parse a float attribute and return the parsed value into a given TypedValue.
   *
   * @param value       the string value of the attribute
   * @param outValue    the TypedValue to receive the parsed value
   * @param requireUnit whether the value is expected to contain a unit.
   * @return true if success.
   */
  public static boolean parseFloatAttribute(@NotNull String value, TypedValue outValue, boolean requireUnit) {
    // remove the space before and after
    value = value.trim();
    int len = value.length();

    if (len <= 0) {
      return false;
    }

    // check that there's no non ascii characters.
    char[] buf = value.toCharArray();
    for (int i = 0 ; i < len ; i++) {
      if (buf[i] > 255) {
        return false;
      }
    }

    // check the first character
    if ((buf[0] < '0' || buf[0] > '9') && buf[0] != '.' && buf[0] != '-' && buf[0] != '+') {
      return false;
    }

    // now look for the string that is after the float...
    Matcher m = sFloatPattern.matcher(value);
    if (m.matches()) {
      String f_str = m.group(1);
      String end = m.group(2);

      float f;
      try {
        f = Float.parseFloat(f_str);
      } catch (NumberFormatException e) {
        // this shouldn't happen with the regexp above.
        return false;
      }

      if (!end.isEmpty() && end.charAt(0) != ' ') {
        // Might be a unit...
        if (parseUnit(end, outValue)) {
          computeTypedValue(outValue, f, sFloatOut[0]);
          return true;
        }
        return false;
      }

      // make sure it's only spaces at the end.
      end = end.trim();

      if (end.isEmpty()) {
        if (outValue != null) {
          if (!requireUnit) {
            outValue.type = TypedValue.TYPE_FLOAT;
            outValue.data = Float.floatToIntBits(f);
          } else {
            // no unit when required? Use dp and out an error.
            applyUnit(sUnitNames[1], outValue);
            computeTypedValue(outValue, f, sFloatOut[0]);

            LOG.warn(String.format(DIMENSION_ERROR_FORMAT, value));
          }
          return true;
        }
      }
    }

    return false;
  }

  private static void computeTypedValue(TypedValue outValue, float value, float scale) {
    value *= scale;
    boolean neg = value < 0;
    if (neg) {
      value = -value;
    }
    long bits = (long)(value*(1<<23)+.5f);
    int radix;
    int shift;
    if ((bits&0x7fffff) == 0) {
      // Always use 23p0 if there is no fraction, just to make
      // things easier to read.
      radix = TypedValue.COMPLEX_RADIX_23p0;
      shift = 23;
    } else if ((bits&0xffffffffff800000L) == 0) {
      // Magnitude is zero -- can fit in 0 bits of precision.
      radix = TypedValue.COMPLEX_RADIX_0p23;
      shift = 0;
    } else if ((bits&0xffffffff80000000L) == 0) {
      // Magnitude can fit in 8 bits of precision.
      radix = TypedValue.COMPLEX_RADIX_8p15;
      shift = 8;
    } else if ((bits&0xffffff8000000000L) == 0) {
      // Magnitude can fit in 16 bits of precision.
      radix = TypedValue.COMPLEX_RADIX_16p7;
      shift = 16;
    } else {
      // Magnitude needs entire range, so no fractional part.
      radix = TypedValue.COMPLEX_RADIX_23p0;
      shift = 23;
    }
    int mantissa = (int)(
      (bits>>shift) & TypedValue.COMPLEX_MANTISSA_MASK);
    if (neg) {
      mantissa = (-mantissa) & TypedValue.COMPLEX_MANTISSA_MASK;
    }
    outValue.data |=
      (radix<<TypedValue.COMPLEX_RADIX_SHIFT)
      | (mantissa<<TypedValue.COMPLEX_MANTISSA_SHIFT);
  }

  private static boolean parseUnit(String str, TypedValue outValue) {
    str = str.trim();

    for (UnitEntry unit : sUnitNames) {
      if (unit.name.equals(str)) {
        applyUnit(unit, outValue);
        return true;
      }
    }

    return false;
  }

  private static void applyUnit(UnitEntry unit, TypedValue outValue) {
    outValue.type = unit.type;
    //noinspection PointlessBitwiseExpression
    outValue.data = unit.unit << TypedValue.COMPLEX_UNIT_SHIFT;
    sFloatOut[0] = unit.scale;
  }

  private static final class UnitEntry {
    private final String name;
    private final int type;
    private final int unit;
    private final float scale;

    UnitEntry(String name, int type, int unit, float scale) {
      this.name = name;
      this.type = type;
      this.unit = unit;
      this.scale = scale;
    }
  }

  /**
   * Container for a dynamically typed data value. Used to hold resources values.
   */
  public static class TypedValue {
    static final int TYPE_FLOAT = 0x04;
    static final int TYPE_DIMENSION = 0x05;

    static final int COMPLEX_UNIT_SHIFT = 0;
    static final int COMPLEX_UNIT_MASK = 0xf;

    static final int COMPLEX_UNIT_PX = 0;
    static final int COMPLEX_UNIT_DIP = 1;
    static final int COMPLEX_UNIT_SP = 2;
    static final int COMPLEX_UNIT_PT = 3;
    static final int COMPLEX_UNIT_IN = 4;
    static final int COMPLEX_UNIT_MM = 5;

    static final int COMPLEX_RADIX_SHIFT = 4;
    static final int COMPLEX_RADIX_MASK = 0x3;

    static final int COMPLEX_RADIX_23p0 = 0;
    static final int COMPLEX_RADIX_16p7 = 1;
    static final int COMPLEX_RADIX_8p15 = 2;
    static final int COMPLEX_RADIX_0p23 = 3;

    static final int COMPLEX_MANTISSA_SHIFT = 8;
    static final int COMPLEX_MANTISSA_MASK = 0xffffff;

    private static final float MANTISSA_MULT = 1.0f / (1 << COMPLEX_MANTISSA_SHIFT);
    private static final float[] RADIX_MULTS = new float[] {
      1.0f * MANTISSA_MULT, 1.0f / (1 << 7) * MANTISSA_MULT,
      1.0f / (1 << 15) * MANTISSA_MULT, 1.0f / (1 << 23) * MANTISSA_MULT
    };

    public int type;
    public int data;

    /**
     * Converts a complex data value holding a dimension to its final value
     * as an integer pixel size. A size conversion involves rounding the base
     * value, and ensuring that a non-zero base value is at least one pixel
     * in size. The given <var>data</var> must be structured as a
     * {@link #TYPE_DIMENSION}.
     *
     * @param data   A complex data value holding a unit, magnitude, and mantissa.
     * @param config The device configuration
     * @return The number of pixels specified by the data and its desired
     * multiplier and units.
     */
    public static int complexToDimensionPixelSize(int data, Configuration config) {
      final float value = complexToFloat(data);
      //noinspection PointlessBitwiseExpression
      final float f = applyDimension((data >> COMPLEX_UNIT_SHIFT) & COMPLEX_UNIT_MASK, value, config);
      final int res = (int)(f+0.5f);
      if (res != 0) return res;
      if (value == 0) return 0;
      if (value > 0) return 1;
      return -1;
    }

    /**
     * Retrieve the base value from a complex data integer.  This uses the
     * {@link #COMPLEX_MANTISSA_MASK} and {@link #COMPLEX_RADIX_MASK} fields of
     * the data to compute a floating point representation of the number they
     * describe.  The units are ignored.
     *
     * @param complex A complex data value.
     * @return A floating point value corresponding to the complex data.
     */
    @SuppressWarnings("NumericOverflow")
    static float complexToFloat(int complex) {
      return (complex&(COMPLEX_MANTISSA_MASK << COMPLEX_MANTISSA_SHIFT))
             * RADIX_MULTS[(complex>>COMPLEX_RADIX_SHIFT) & COMPLEX_RADIX_MASK];
    }

    /**
     * Converts an unpacked complex data value holding a dimension to its final floating
     * point value. The two parameters <var>unit</var> and <var>value</var>
     * are as in {@link #TYPE_DIMENSION}.
     *
     * @param unit   The unit to convert from.
     * @param value  The value to apply the unit to.
     * @param config The device configuration
     * @return The complex floating point value multiplied by the appropriate
     * metrics depending on its unit.
     */
    static float applyDimension(int unit, float value, Configuration config) {
      Device device = config.getCachedDevice();
      float xdpi = 493.0f; // assume Nexus 6 density
      if (device != null) {
        xdpi = (float) device.getDefaultHardware().getScreen().getXdpi();
      }

      switch (unit) {
        case COMPLEX_UNIT_PX:
          return value;
        case COMPLEX_UNIT_DIP:
          return value * config.getDensity().getDpiValue() / 160.0f;
        case COMPLEX_UNIT_SP:
          return value * config.getDensity().getDpiValue() / 160.0f;
        case COMPLEX_UNIT_PT:
          return value * xdpi * (1.0f / 72.0f);
        case COMPLEX_UNIT_IN:
          return value * xdpi * (1.0f / 72.0f);
        case COMPLEX_UNIT_MM:
          return value * xdpi * (1.0f / 72.0f);
      }
      return 0;
    }
  }
}
