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
package com.android.tools.idea.res.aar;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.PluralsResourceValue;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.tools.idea.flags.StudioFlags;
import com.android.utils.XmlUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This test compares contents of {@link AarSourceResourceRepository} with and without
 * the {@link StudioFlags#LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR} flag. This test will be
 * removed after the {@link StudioFlags#LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR} flag is removed.
 */
public class AarSourceResourceRepositoryComparisonTest extends AndroidTestCase {
  /** Enables printing of repository loading statistics. */
  private static final boolean PRINT_STATS = false;

  private static final String LIBRARY_NAME = "design-27.0.2";

  private static final Pattern DIMEN_PATTERN = Pattern.compile("(?<number>-?\\d+(\\.\\d*)?)(?<suffix>px|dp|dip|sp|pt|in|mm|)");

  private static final Comparator<ResourceItem> ITEM_COMPARATOR = (item1, item2) -> {
    int comp = item1.getType().compareTo(item2.getType());
    if (comp != 0) {
      return comp;
    }
    comp = item1.getNamespace().compareTo(item2.getNamespace());
    if (comp != 0) {
      return comp;
    }
    comp = item1.getName().compareTo(item2.getName());
    if (comp != 0) {
      return comp;
    }
    comp = item1.getConfiguration().compareTo(item2.getConfiguration());
    if (comp != 0) {
      return comp;
    }
    PathString source1 = item1.getSource();
    PathString source2 = item2.getSource();
    if (source1 == source2) {
      return 0;
    }
    if (source1 == null) {
      return -1;
    }
    if (source2 == null) {
      return 1;
    }
    return source1.compareTo(source2);
  };

  private File myAarFolder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myAarFolder = new File(myFixture.getTestDataPath(), "design_aar");
  }

  private static void compareContents(@NotNull SingleNamespaceResourceRepository expected,
                                      @NotNull SingleNamespaceResourceRepository actual) {
    List<ResourceItem> expectedItems = new ArrayList<>(expected.getAllResources());
    List<ResourceItem> actualItems = new ArrayList<>(actual.getAllResources());

    expectedItems.sort(ITEM_COMPARATOR);
    actualItems.sort(ITEM_COMPARATOR);

    for (int i = 0; i < expectedItems.size() || i < actualItems.size(); i++) {
      if (i >= expectedItems.size()) {
        fail("Unexpected ResourceItem at position " + i + " - " + actualItems.get(i));
      }
      if (i >= actualItems.size()) {
        fail("Missing ResourceItem at position " + i  + " expected " + expectedItems.get(i));
      }
      ResourceItem expectedItem = expectedItems.get(i);
      ResourceItem actualItem = actualItems.get(i);
      assertTrue("Different ResourceItem at position " + i, areEquivalent(expectedItem, actualItem));
      assertTrue("Different ResourceValue at position " + i,
                 areEquivalentResourceValues(expectedItem.getResourceValue(), actualItem.getResourceValue()));
    }

    for (ResourceType type : ResourceType.values()) {
      List<ResourceItem> expectedPublic = new ArrayList<>(expected.getPublicResources(expected.getNamespace(), type));
      List<ResourceItem> actualPublic = new ArrayList<>(actual.getPublicResources(actual.getNamespace(), type));
      assertEquals("Number of public resources doesn't match for type " + type.getName(), expectedPublic.size(), actualPublic.size());
      expectedPublic.sort(ITEM_COMPARATOR);
      actualPublic.sort(ITEM_COMPARATOR);
      for (int i = 0; i < expectedPublic.size(); i++) {
        ResourceItem expectedItem = expectedPublic.get(i);
        ResourceItem actualItem = actualPublic.get(i);
        assertTrue("Public resource difference at position " + i + " for type " + type.getName(), areEquivalent(expectedItem, actualItem));
      }
    }
  }

  private static boolean areEquivalent(@NotNull ResourceItem item1, @NotNull ResourceItem item2) {
    if (!item1.getType().equals(item2.getType())) {
      return false;
    }
    if (!item1.getNamespace().equals(item2.getNamespace())) {
      return false;
    }
    if (!item1.getName().equals(item2.getName())) {
      return false;
    }
    if (!Objects.equals(item1.getLibraryName(), item2.getLibraryName())) {
      return false;
    }
    if (!Objects.equals(item1.getConfiguration(), item2.getConfiguration())) {
      return false;
    }
    return Objects.equals(item1.getSource(), item2.getSource());
  }

  private static boolean areEquivalentResourceValues(@Nullable ResourceValue value1, @Nullable ResourceValue value2) {
    if (value1 == value2) {
      return true;
    }
    if (value1 == null || value2 == null) {
      return false;
    }
    if (!value1.getResourceType().equals(value2.getResourceType())) {
      return false;
    }
    if (!value1.getNamespace().equals(value2.getNamespace())) {
      return false;
    }
    if (!value1.getName().equals(value2.getName())) {
      return false;
    }
    if (!Objects.equals(value1.getLibraryName(), value2.getLibraryName())) {
      return false;
    }

    Density density1 = value1 instanceof DensityBasedResourceValue ? ((DensityBasedResourceValue)value1).getResourceDensity() : null;
    Density density2 = value2 instanceof DensityBasedResourceValue ? ((DensityBasedResourceValue)value2).getResourceDensity() : null;
    if (!Objects.equals(density1, density2)) {
      return false;
    }

    if (value1 instanceof StyleableResourceValue && value2 instanceof StyleableResourceValue) {
      List<AttrResourceValue> attrs1 = ((StyleableResourceValue)value1).getAllAttributes();
      List<AttrResourceValue> attrs2 = ((StyleableResourceValue)value2).getAllAttributes();
      if (attrs1.size() != attrs2.size()) {
        return false;
      }
      for (int i = 0; i < attrs1.size(); i++) {
        if (!areEquivalentResourceValues(attrs1.get(i), attrs2.get(i))) {
          return false;
        }
      }
    } else if ((value1 instanceof StyleableResourceValue) != (value2 instanceof StyleableResourceValue)) {
      return false;
    }

    if (value1 instanceof AttrResourceValue && value2 instanceof AttrResourceValue) {
      AttrResourceValue attr1 = (AttrResourceValue)value1;
      AttrResourceValue attr2 = (AttrResourceValue)value2;
      if (!Objects.equals(attr1.getDescription(), attr2.getDescription())) {
        return false;
      }
      if (!Objects.equals(attr1.getGroupName(), attr2.getGroupName())) {
        return false;
      }
      if (!Objects.equals(attr1.getFormats(), attr2.getFormats())) {
        return false;
      }
      Map<String, Integer> attrValues1 = attr1.getAttributeValues();
      Map<String, Integer> attrValues2 = attr2.getAttributeValues();
      if (!Objects.equals(attrValues1, attrValues2)) {
        return false;
      }
      if (attrValues1 != null) {
        for (String valueName: attrValues1.keySet()) {
          if (!Objects.equals(attr1.getValueDescription(valueName), attr2.getValueDescription(valueName))) {
            return false;
          }
        }
      }
    } else if ((value1 instanceof AttrResourceValue) != (value2 instanceof AttrResourceValue)) {
      return false;
    }

    if (value1 instanceof StyleResourceValue && value2 instanceof StyleResourceValue) {
      StyleResourceValue style1 = (StyleResourceValue)value1;
      StyleResourceValue style2 = (StyleResourceValue)value2;
      if (!Objects.equals(style1.getParentStyle(), style2.getParentStyle())) {
        return false;
      }
      Collection<StyleItemResourceValue> items1 = style1.getDefinedItems();
      Collection<StyleItemResourceValue> items2 = style2.getDefinedItems();
      if (items1.size() != items2.size()) {
        return false;
      }
      Iterator<StyleItemResourceValue> it1 = items1.iterator();
      Iterator<StyleItemResourceValue> it2 = items2.iterator();
      while (it1.hasNext()) {
        StyleItemResourceValue item1 = it1.next();
        StyleItemResourceValue item2 = it2.next();
        if (!areEquivalentResourceValues(item1, item2)) {
          return false;
        }
      }
    } else if ((value1 instanceof StyleResourceValue) != (value2 instanceof StyleResourceValue)) {
      return false;
    }

    if (value1 instanceof ArrayResourceValue && value2 instanceof ArrayResourceValue) {
      ArrayResourceValue array1 = (ArrayResourceValue)value1;
      ArrayResourceValue array2 = (ArrayResourceValue)value2;
      if (array1.getElementCount() != array2.getElementCount()) {
        return false;
      }
      for (int i = 0; i < array1.getElementCount(); i++) {
        if (!array1.getElement(i).equals(array2.getElement(i))) {
          return false;
        }
      }
    } else if ((value1 instanceof ArrayResourceValue) != (value2 instanceof ArrayResourceValue)) {
      return false;
    }

    if (value1 instanceof PluralsResourceValue && value2 instanceof PluralsResourceValue) {
      PluralsResourceValue plural1 = (PluralsResourceValue)value1;
      PluralsResourceValue plural2 = (PluralsResourceValue)value2;
      if (plural1.getPluralsCount() != plural2.getPluralsCount()) {
        return false;
      }
      for (int i = 0; i < plural1.getPluralsCount(); i++) {
        if (!plural1.getQuantity(i).equals(plural2.getQuantity(i)) || !plural1.getValue(i).equals(plural2.getValue(i))) {
          return false;
        }
      }
    } else if ((value1 instanceof PluralsResourceValue) != (value2 instanceof PluralsResourceValue)) {
      return false;
    }

    String v1 = value1.getValue();
    String v2 = value2.getValue();
    if (Objects.equals(v1, v2)) {
      return true;
    }
    if (value1 instanceof AarFileResourceItem) {
      String temp = v1;
      v1 = v2;
      v2 = temp;
    }

    switch (value1.getResourceType()) {
      case COLOR:
      case DRAWABLE:
        if (v1.startsWith("#") && v2.startsWith("#") && v1.equalsIgnoreCase(v2)) {
          return true; // Hexadecimal representations of the same color may differ by case.
        }
        break;

      case STRING:
        if (!v2.contains("%")) {
          String temp = v1;
          v1 = v2;
          v2 = temp;
        }
        if (v2.contains("%")) {
          // AAR string resources do not contain any xliff information.
          // Allow "Share with %s" to match "Share with (Mail)" or "Share with ${application_name}".
          Pattern pattern = buildPattern(v2);
          if (pattern.matcher(v1).matches()) {
            return true;
          }
        }
        break;

      case STYLE_ITEM:
        ResourceUrl url1 = ResourceUrl.parse(v1);
        ResourceUrl url2 = ResourceUrl.parse(v2);
        if (url1 != null && url1.equals(url2)) {
          return true;
        }
        break;

      case ID:
        return true; // ID resources don't have values in AARv2.

      default:
        break;
    }

    if (v1 == null || v2 == null) {
      return false;
    }
    if (normalizeDimensionValue(v1).equals(normalizeDimensionValue(v2))) {
      return true;
    }
    return false;
  }

  @NotNull
  private static String normalizeDimensionValue(@NotNull String value) {
    Matcher matcher = DIMEN_PATTERN.matcher(value);
    if (!matcher.matches()) {
      return value;
    }
    String number = XmlUtils.trimInsignificantZeros(matcher.group("number"));
    String suffix = matcher.group("suffix");
    if (suffix.equals("dip")) {
      suffix = "dp";
    }
    return number + suffix;
  }

  /**
   * Builds a regular expression pattern replacing format specifiers with (\$\{.*\}|\(.*\)) patterns and
   * escaping the rest the string.
   *
   * @param formatStr the format string to build a regular expression pattern for
   * @return a regular expression pattern
   */
  @NotNull
  private static Pattern buildPattern(@NotNull String formatStr) {
    StringBuilder buf = new StringBuilder(formatStr.length() * 2);
    boolean escaped = false;
    boolean inFormatSpecifier = false;
    for (int i = 0; i < formatStr.length(); i++) {
      char c = formatStr.charAt(i);
      if (inFormatSpecifier) {
        if (c == 's') {
          buf.append("(\\$\\{.*\\}|\\(.*\\))");
          inFormatSpecifier = false;
        }
      } else {
        switch (c) {
          case '%':
            if (escaped) {
              buf.append(c);
              escaped = false;
            } else {
              inFormatSpecifier = true;
            }
            break;
          case '\\':
          case '.':
          case '^':
          case '$':
          case '|':
          case '(':
          case ')':
          case '{':
          case '}':
          case '[':
          case ']':
          case '*':
          case '+':
          case '?':
            buf.append('\\').append(c);
            escaped = c != '\\' || !escaped;
            break;
          default:
            buf.append(c);
            escaped = false;
            break;
        }
      }
    }
    return Pattern.compile(buf.toString());
  }

  @Override
  public void tearDown() throws Exception {
    try {
      StudioFlags.LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR.clearOverride();
    } finally {
      super.tearDown();
    }
  }

  public void testLoading() throws Exception {
    File resFolder = new File(myAarFolder, SdkConstants.FD_RES);
    long loadTimeWithResourceMerger = 0;
    long loadTimeWithoutResourceMerger = 0;
    int count = PRINT_STATS ? 100 : 1;
    for (int i = 0; i < count; i++) {
      StudioFlags.LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR.override(false);
      long start = System.currentTimeMillis();
      AarSourceResourceRepository usingResourceMerger = AarSourceResourceRepository.create(resFolder, LIBRARY_NAME);
      loadTimeWithResourceMerger += System.currentTimeMillis() - start;

      StudioFlags.LIGHTWEIGHT_DATA_STRUCTURES_FOR_AAR.override(true);
      start = System.currentTimeMillis();
      AarSourceResourceRepository withoutResourceMerger = AarSourceResourceRepository.create(resFolder, LIBRARY_NAME);
      loadTimeWithoutResourceMerger += System.currentTimeMillis() - start;
      if (i == 0) {
        compareContents(usingResourceMerger, withoutResourceMerger);
      }
    }
    if (PRINT_STATS) {
      System.out.println("Load time with resource merger: " + loadTimeWithResourceMerger / (count * 1000.)
                         + " sec, without resource merger: " + loadTimeWithoutResourceMerger / (count * 1000.) + " sec");
    }
  }
}
