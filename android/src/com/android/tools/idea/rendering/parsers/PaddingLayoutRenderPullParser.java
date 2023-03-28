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
package com.android.tools.idea.rendering.parsers;

import com.android.annotations.Nullable;
import com.android.resources.Density;
import com.android.tools.idea.rendering.IRenderLogger;
import com.android.tools.idea.res.ResourceRepositoryManager;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;

/**
 * A custom version of the {@link LayoutRenderPullParser} which
 * can add padding to a dedicated set of layout nodes, which for example can be used to
 * ensure that empty view groups have certain minimum size during a palette drop.
 */
class PaddingLayoutRenderPullParser extends LayoutRenderPullParser {
  private final static Pattern FLOAT_PATTERN = Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)(.*)"); //$NON-NLS-1$
  private final static int PADDING_VALUE = 10;

  private boolean myZeroAttributeIsPadding;
  private boolean myIncreaseExistingPadding;

  @NotNull
  private final Density myDensity;

  /**
   * Number of pixels to pad views with in exploded-rendering mode.
   */
  private static final String DEFAULT_PADDING_VALUE = PADDING_VALUE + UNIT_PX;

  /**
   * Number of pixels to pad exploded individual views with. (This is HALF the width of the
   * rectangle since padding is repeated on both sides of the empty content.)
   */
  private static final String FIXED_PADDING_VALUE = "20px"; //$NON-NLS-1$

  /**
   * Set of nodes that we want to auto-pad using {@link #FIXED_PADDING_VALUE} as the padding
   * attribute value. Can be null, which is the case when we don't want to perform any
   * <b>individual</b> node exploding.
   */
  private final Set<RenderXmlTag> myExplodeNodes;

  /**
   * Use the {@link LayoutRenderPullParser#create(RenderXmlFile, IRenderLogger, Set, Density, ResourceRepositoryManager)} factory instead.
   */
  PaddingLayoutRenderPullParser(@NotNull RenderXmlFile file, @NotNull IRenderLogger logger, @NotNull Set<RenderXmlTag> explodeNodes,
                                @NotNull Density density, @Nullable ResourceRepositoryManager resourceRepositoryManager) {
    super(file, logger, true, resourceRepositoryManager);
    myExplodeNodes = explodeNodes;
    myDensity = density;
  }

  @Override
  protected void push(@NotNull TagSnapshot node) {
    super.push(node);

    myZeroAttributeIsPadding = false;
    myIncreaseExistingPadding = false;
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Override
  public int getAttributeCount() {
    int count = super.getAttributeCount();
    return count + (myZeroAttributeIsPadding ? 1 : 0);
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Nullable
  @Override
  public String getAttributeName(int i) {
    if (myZeroAttributeIsPadding) {
      if (i == 0) {
        return ATTR_PADDING;
      }
      else {
        i--;
      }
    }

    return super.getAttributeName(i);
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Override
  public String getAttributeNamespace(int i) {
    if (myZeroAttributeIsPadding) {
      if (i == 0) {
        return ANDROID_URI;
      }
      else {
        i--;
      }
    }

    return super.getAttributeNamespace(i);
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Nullable
  @Override
  public String getAttributePrefix(int i) {
    if (myZeroAttributeIsPadding) {
      if (i == 0) {
        assert myRoot != null;
        return myNamespacePrefixes.get(ANDROID_URI);
      }
      else {
        i--;
      }
    }

    return super.getAttributePrefix(i);
  }

  /*
   * This does not seem to be called by the layoutlib, but we keep this (and maintain
   * it) just in case.
   */
  @Nullable
  @Override
  public String getAttributeValue(int i) {
    if (myZeroAttributeIsPadding) {
      if (i == 0) {
        return DEFAULT_PADDING_VALUE;
      }
      else {
        i--;
      }
    }

    AttributeSnapshot attribute = getAttribute(i);
    if (attribute != null) {
      String value = attribute.value;
      if (value != null && myIncreaseExistingPadding && ATTR_PADDING.equals(attribute.name) &&
          ANDROID_URI.equals(attribute.namespace)) {
        // add the padding and return the value
        return addPaddingToValue(value);
      }
      return value;
    }

    return null;
  }

  /*
   * This is the main method used by the LayoutInflater to query for attributes.
   */
  @Nullable
  @Override
  public String getAttributeValue(String namespace, String localName) {
    boolean isPaddingAttribute = ATTR_PADDING.equals(localName);
    if (isPaddingAttribute && ANDROID_URI.equals(namespace)) {
      TagSnapshot node = getCurrentNode();
      if (node != null && myExplodeNodes.contains(node.tag)) {
        return FIXED_PADDING_VALUE;
      }
    }

    if (myZeroAttributeIsPadding && isPaddingAttribute && ANDROID_URI.equals(namespace)) {
      return DEFAULT_PADDING_VALUE;
    }

    String value = super.getAttributeValue(namespace, localName);
    if (value != null) {
      if (myIncreaseExistingPadding && isPaddingAttribute && ANDROID_URI.equals(namespace)) {
        // add the padding and return the value
        return addPaddingToValue(value);
      }

    }

    return value;
  }

  // ------- TypedValue stuff
  // This is adapted from com.android.layoutlib.bridge.ResourceHelper
  // (but modified to directly take the parsed value and convert it into pixel instead of
  // storing it into a TypedValue)
  // this was originally taken from platform/frameworks/base/libs/utils/ResourceTypes.cpp

  private static final class DimensionEntry {
    final String name;
    final int type;

    DimensionEntry(String name, int unit) {
      this.name = name;
      this.type = unit;
    }
  }

  /**
   * {@link DimensionEntry} complex unit: Value is raw pixels.
   */
  private static final int COMPLEX_UNIT_PX = 0;
  /**
   * {@link DimensionEntry} complex unit: Value is Device Independent
   * Pixels.
   */
  private static final int COMPLEX_UNIT_DIP = 1;
  /**
   * {@link DimensionEntry} complex unit: Value is a scaled pixel.
   */
  private static final int COMPLEX_UNIT_SP = 2;
  /**
   * {@link DimensionEntry} complex unit: Value is in points.
   */
  private static final int COMPLEX_UNIT_PT = 3;
  /**
   * {@link DimensionEntry} complex unit: Value is in inches.
   */
  private static final int COMPLEX_UNIT_IN = 4;
  /**
   * {@link DimensionEntry} complex unit: Value is in millimeters.
   */
  private static final int COMPLEX_UNIT_MM = 5;

  private final static DimensionEntry[] DIMENSIONS =
    new DimensionEntry[]{
      new DimensionEntry(UNIT_PX, COMPLEX_UNIT_PX), new DimensionEntry(UNIT_DIP, COMPLEX_UNIT_DIP),
      new DimensionEntry(UNIT_DP, COMPLEX_UNIT_DIP), new DimensionEntry(UNIT_SP, COMPLEX_UNIT_SP),
      new DimensionEntry(UNIT_PT, COMPLEX_UNIT_PT), new DimensionEntry(UNIT_IN, COMPLEX_UNIT_IN),
      new DimensionEntry(UNIT_MM, COMPLEX_UNIT_MM)
    };

  /**
   * Adds padding to an existing dimension.
   * <p/>This will resolve the attribute value (which can be px, dip, dp, sp, pt, in, mm) to
   * a pixel value, add the padding value ({@link #PADDING_VALUE}),
   * and then return a string with the new value as a px string ("42px");
   * If the conversion fails, only the special padding is returned.
   */
  private String addPaddingToValue(@Nullable String s) {
    if (s == null) {
      return DEFAULT_PADDING_VALUE;
    }
    int padding = PADDING_VALUE;
    if (stringToPixel(s)) {
      padding += myLastPixel;
    }

    return padding + UNIT_PX;
  }

  /** Out value from {@link #stringToPixel(String)}: the integer pixel value */
  private int myLastPixel;

  /**
   * Convert the string into a pixel value, and puts it in {@link #myLastPixel}
   *
   * @param s the dimension value from an XML attribute
   * @return true if success.
   */
  private boolean stringToPixel(String s) {
    // remove the space before and after
    s = s.trim();
    int len = s.length();

    if (len <= 0) {
      return false;
    }

    // check that there's no non ASCII characters.
    char[] buf = s.toCharArray();
    for (int i = 0; i < len; i++) {
      if (buf[i] > 255) {
        return false;
      }
    }

    // check the first character
    if (buf[0] < '0' && buf[0] > '9' && buf[0] != '.' && buf[0] != '-') {
      return false;
    }

    // now look for the string that is after the float...
    Matcher m = FLOAT_PATTERN.matcher(s);
    if (m.matches()) {
      String f_str = m.group(1);
      String end = m.group(2);

      float f;
      try {
        f = Float.parseFloat(f_str);
      }
      catch (NumberFormatException e) {
        // this shouldn't happen with the regexp above.
        return false;
      }

      if (!end.isEmpty() && end.charAt(0) != ' ') {
        // We only support dimension-type values, so try to parse the unit for dimension
        DimensionEntry dimension = parseDimension(end);
        if (dimension != null) {
          // convert the value into pixel based on the dimension type
          // This is similar to TypedValue.applyDimension()
          switch (dimension.type) {
            case COMPLEX_UNIT_PX:
              // do nothing, value is already in px
              break;
            case COMPLEX_UNIT_DIP:
            case COMPLEX_UNIT_SP: // intended fall-through since we don't
              // adjust for font size
              f *= (float)myDensity.getDpiValue() / Density.DEFAULT_DENSITY;
              break;
            case COMPLEX_UNIT_PT:
              f *= myDensity.getDpiValue() * (1.0f / 72);
              break;
            case COMPLEX_UNIT_IN:
              f *= myDensity.getDpiValue();
              break;
            case COMPLEX_UNIT_MM:
              f *= myDensity.getDpiValue() * (1.0f / 25.4f);
              break;
          }

          // store result (converted to int)
          myLastPixel = (int)(f + 0.5);

          return true;
        }
      }
    }

    return false;
  }

  @Nullable
  private static DimensionEntry parseDimension(String str) {
    str = str.trim();

    for (DimensionEntry d : DIMENSIONS) {
      if (d.name.equals(str)) {
        return d;
      }
    }

    return null;
  }
}
