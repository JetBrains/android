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
package com.android.tools.idea.resources.aar;

import static com.android.utils.DecimalUtils.trimInsignificantZeros;
import static com.google.common.truth.Truth.assertThat;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.PluralsResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceItemWithVisibility;
import com.android.ide.common.resources.SingleNamespaceResourceRepository;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.ResourceVisibility;
import com.android.testutils.TestUtils;
import com.google.common.base.Splitter;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import junit.framework.TestCase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Tests for {@link AarProtoResourceRepository}.
 */
public class AarProtoResourceRepositoryTest extends AndroidTestCase {
  /** Enables printing of repository loading statistics. */
  private static final boolean PRINT_STATS = false;

  private static final String LIBRARY_NAME = "design-27.1.1";
  private static final String LIBRARY_PACKAGE = "android.support.design";

  private static final String TAG_ATTR = "attr";
  private static final String TAG_ENUM = "enum";
  private static final String TAG_FLAG = "flag";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_VALUE = "value";
  private static final Pattern DIMEN_PATTERN = Pattern.compile("(?<number>-?\\d+(\\.\\d*)?)(?<suffix>px|dp|dip|sp|pt|in|mm|)");
  private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<number>-?\\d+(\\.\\d*)?)");
  private static final Splitter FLAG_SPLITTER = Splitter.on('|');

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

  private static final Comparator<ResourceValue> VALUE_COMPARATOR = (resourceValue1, resourceValue2) -> {
    int comp = resourceValue1.getResourceType().compareTo(resourceValue2.getResourceType());
    if (comp != 0) {
      return comp;
    }
    comp = resourceValue1.getNamespace().compareTo(resourceValue2.getNamespace());
    if (comp != 0) {
      return comp;
    }
    comp = resourceValue1.getName().compareTo(resourceValue2.getName());
    if (comp != 0) {
      return comp;
    }
    String value1 = resourceValue1.getValue();
    String value2 = resourceValue2.getValue();
    if (value1 == value2) {
      return 0;
    }
    if (value1 == null) {
      return -1;
    }
    if (value2 == null) {
      return 1;
    }
    return value1.compareTo(value2);
  };

  private Path myAarFolder;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myAarFolder = Paths.get(myFixture.getTestDataPath(), "design_aar");
  }

  /**
   * Checks if contents of two resource repositories are equivalent.
   *
   * @param expected the golden repository
   * @param actual the repository to compare to the golden one
   * @param enumMap the map used to establish equivalence between symbolic labels and numeric values of flags and enumerators.
   *     The map is keyed by attribute names. The values are maps from symbolic labels to the corresponding numerical values.
   */
  private static void compareContents(@NotNull SingleNamespaceResourceRepository expected,
                                      @NotNull SingleNamespaceResourceRepository actual,
                                      @NotNull Map<String, Map<String, Integer>> enumMap) {
    TestCase.assertEquals(expected.getNamespace(), actual.getNamespace());
    String packagePrefix = getPackagePrefix(expected);

    List<ResourceItem> expectedItems = new ArrayList<>(expected.getAllResources());
    List<ResourceItem> actualItems = new ArrayList<>(actual.getAllResources());

    expectedItems.sort(ITEM_COMPARATOR);
    actualItems.sort(ITEM_COMPARATOR);

    ResourceItem previousItem = null;
    for (int i = 0, j = 0; i < expectedItems.size() || j < actualItems.size(); i++, j++) {
      ResourceItem expectedItem = i < expectedItems.size() ? expectedItems.get(i) : null;
      ResourceItem actualItem = j < actualItems.size() ? actualItems.get(j) : null;
      if (actualItem == null || expectedItem == null || !areEquivalent(expectedItem, actualItem, packagePrefix)) {
        if (actualItem != null && actualItem.getType() == ResourceType.ID &&
            ITEM_COMPARATOR.compare(actualItem, expectedItem) < 0) {
          // AarSourceResourceRepository does not create ID resources for enum and flag values of attr resources but AAPT2 does.
          //noinspection AssignmentToForLoopParameter
          i--; // Skip the ID resource.
        } else if (actualItem != null && actualItem.getName().startsWith("$")) {
          //noinspection AssignmentToForLoopParameter
          i--; // Ignore the resource corresponding to the extracted aapt tag.
        } else if (expectedItem != null && isEmptyAttr(expectedItem)) {
          //noinspection AssignmentToForLoopParameter
          j--; // Ignore empty attr definitions since they are not produced by AAPT2.
        } else if (expectedItem != null &&
                   findEquivalentResourceInMatchingConfiguration(expectedItem, actualItems, j - 1, enumMap) != null) {
          //noinspection AssignmentToForLoopParameter
          j--;  // AAPT2 may eliminate redundant resource definitions for matching configurations.
        } else {
          if (expectedItem == null) {
            TestCase.fail("Unexpected ResourceItem at position " + j);
          }
          TestCase.assertTrue("Different ResourceItem at position " + i,
                     previousItem != null && areEquivalent(expectedItem, previousItem, packagePrefix));
          TestCase.assertTrue("Different ResourceValue at position " + i,
                              areEquivalentResourceValues(expectedItem.getResourceValue(), previousItem.getResourceValue(), false, enumMap));
          FolderConfiguration expectedConfiguration = expectedItem.getConfiguration();
          FolderConfiguration previousConfiguration = previousItem.getConfiguration();
          ScreenSizeQualifier expectedQualifier = expectedConfiguration.getScreenSizeQualifier();
          ScreenSizeQualifier previousQualifier = previousConfiguration.getScreenSizeQualifier();
          TestCase.assertTrue("Screen size does not match at position " + i, previousQualifier.isMatchFor(expectedQualifier));
          FolderConfiguration config = FolderConfiguration.copyOf(previousConfiguration);
          config.setScreenSizeQualifier(expectedQualifier);
          TestCase.assertEquals("Different FolderConfiguration at position " + i, expectedConfiguration, config);
          //noinspection AssignmentToForLoopParameter
          j--;
        }
      } else {
        TestCase.assertTrue("Different ResourceItem at position " + i, areEquivalent(expectedItem, actualItem, packagePrefix));
        TestCase.assertTrue("Different ResourceValue at position " + i,
                            areEquivalentResourceValues(expectedItem.getResourceValue(), actualItem.getResourceValue(), false, enumMap));
        previousItem = actualItem;
      }
    }

    for (ResourceType type : ResourceType.values()) {
      List<ResourceReference> expectedPublic = expected.getPublicResources(expected.getNamespace(), type)
          .stream()
          .map(r -> new ResourceReference(r.getNamespace(), r.getType(), r.getName()))
          .sorted()
          .distinct()
          .collect(Collectors.toList());
      List<ResourceReference> actualPublic = actual.getPublicResources(actual.getNamespace(), type)
          .stream()
          .map(r -> new ResourceReference(r.getNamespace(), r.getType(), r.getName()))
          .sorted()
          .distinct()
          .collect(Collectors.toList());
      TestCase.assertEquals("Number of public resources doesn't match for type " + type.getName(), expectedPublic.size(), actualPublic.size());
      for (int i = 0; i < expectedPublic.size(); i++) {
        ResourceReference expectedItem = expectedPublic.get(i);
        ResourceReference actualItem = actualPublic.get(i);
        TestCase.assertEquals("Public resource difference at position " + i + " for type " + type.getName(), expectedItem, actualItem);
      }
    }
  }

  /**
   * Searches {@code resourcesToSearch} backwards starting from {@code startIndex - 1} for a resource with the same type,
   * namespace, name, and value as the given {@code resource} in a configuration that matches configuration of {@code resource}
   * according to the {@link FolderConfiguration#isMatchFor(FolderConfiguration)} method.
   *
   * @param resource the reference resource
   * @param resourcesToSearch the resources to search sorted by type, namespace and name.
   * @param startIndex the index of the resource to start the search from
   * @param enumMap the map used to establish equivalence between symbolic labels and numeric values of flags and enumerators.
   *     The map is keyed by attribute names. The values are maps from symbolic labels to the corresponding numerical values.
   * @return the found equivalent resource, or null if not found
   */
  @Nullable
  private static ResourceItem findEquivalentResourceInMatchingConfiguration(
      @NotNull ResourceItem resource, @NotNull List<ResourceItem> resourcesToSearch, int startIndex,
      @NotNull Map<String, Map<String, Integer>> enumMap) {
    for (int i = startIndex; i >= 0; i--) {
      ResourceItem candidateMatch = resourcesToSearch.get(i);
      if (candidateMatch.getType() != resource.getType() ||
          !candidateMatch.getName().equals(resource.getName()) ||
          !candidateMatch.getNamespace().equals(resource.getNamespace())) {
        break;
      }
      if (candidateMatch.getConfiguration().isMatchFor(resource.getConfiguration()) &&
          areEquivalentResourceValues(candidateMatch.getResourceValue(), resource.getResourceValue(), false, enumMap)) {
        return candidateMatch;
      }
    }
    return null;
  }

  private static boolean isEmptyAttr(@NotNull ResourceItem item) {
    if (item.getType() != ResourceType.ATTR) {
      return false;
    }
    AttrResourceValue resourceValue = (AttrResourceValue)item.getResourceValue();
    return resourceValue.getFormats().isEmpty() && resourceValue.getAttributeValues().isEmpty();
  }

  @NotNull
  private static String getPackagePrefix(@NotNull SingleNamespaceResourceRepository repository) {
    return repository.getPackageName().replace('.', '/') + '/';
  }

  private static boolean areEquivalent(@NotNull ResourceItem item1, @NotNull ResourceItem item2, @NotNull String packagePrefix) {
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
    // ID resources in AARv2 always belong to the default configuration.
    if (item1.getType() != ResourceType.ID && !Objects.equals(item1.getConfiguration(), item2.getConfiguration())) {
      return false;
    }
    if (item1.isFileBased() != item2.isFileBased()) {
      return false;
    }
    if (item1.isFileBased() && !areEquivalentSources(item1.getSource(), item2.getSource(), packagePrefix)) {
      return false;
    }
    if (!areEquivalentSources(item1.getOriginalSource(), item2.getOriginalSource(), packagePrefix)) {
      return false;
    }
    if (item1 instanceof ResourceItemWithVisibility && item2 instanceof ResourceItemWithVisibility &&
        // Don't distinguish between PRIVATE and PRIVATE_XML_ONLY because AarSourceResourceRepository doesn't make this distinction.
        (((ResourceItemWithVisibility)item1).getVisibility() == ResourceVisibility.PUBLIC) !=
        (((ResourceItemWithVisibility)item2).getVisibility() == ResourceVisibility.PUBLIC)) {
      return false;
    }
    return true;
  }

  private static boolean areEquivalentSources(@Nullable PathString path1, @Nullable PathString path2, String packagePrefix) {
    if (Objects.equals(path1, path2)) {
      return true;
    }
    if (path1 != null && path2 != null) {
      URI filesystemUri1 = path1.getFilesystemUri();
      URI filesystemUri2 = path2.getFilesystemUri();
      URI nonFileUri = filesystemUri2;
      if (filesystemUri2.getScheme().equals("file") && !filesystemUri1.getScheme().equals("file")) {
        PathString temp = path1;
        path1 = path2;
        path2 = temp;
        nonFileUri = path1.getFilesystemUri();
      }
      String portablePath1 = path1.getPortablePath();
      String portablePath2 = path2.getPortablePath();
      if (nonFileUri.getScheme().equals("apk")) {
        return portablePath1.equals(portablePath2.replace("/res.apk!/", "/"));
      }
      if (nonFileUri.getScheme().equals("jar")) {
        return portablePath1.equals(portablePath2.replace("/res-src.jar!/" + packagePrefix + "0/", "/"));
      }
    }
    return false;
  }

  private static boolean areEquivalentResourceValues(@Nullable ResourceValue value1, @Nullable ResourceValue value2,
                                                     boolean forStyleableAttrs, @NotNull Map<String, Map<String, Integer>> enumMap) {
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
        if (!areEquivalentResourceValues(attrs1.get(i), attrs2.get(i), true, enumMap)) {
          return false;
        }
      }
    } else if ((value1 instanceof StyleableResourceValue) != (value2 instanceof StyleableResourceValue)) {
      return false;
    }

    if (value1 instanceof AttrResourceValue && value2 instanceof AttrResourceValue) {
      AttrResourceValue attr1 = (AttrResourceValue)value1;
      AttrResourceValue attr2 = (AttrResourceValue)value2;
      // Descriptions of top-level attrs may not match due to different order in which resource files
      // are processed by AAPT2 and AarSourceResourceRepository.
      if (forStyleableAttrs && !Objects.equals(attr1.getDescription(), attr2.getDescription())) {
        return false;
      }
      if (!areEquivalentGroupNames(attr1.getGroupName(), attr2.getGroupName())) {
        return false;
      }

      if (!forStyleableAttrs || !(attr1 instanceof AarAttrReference) && !(attr2 instanceof AarAttrReference)) {
        if (!Objects.equals(attr1.getFormats(), attr2.getFormats())) {
          return false;
        }
        Map<String, Integer> attrValues1 = attr1.getAttributeValues();
        Map<String, Integer> attrValues2 = attr2.getAttributeValues();
        if (!attrValues1.equals(attrValues2)) {
          return false;
        }
        for (String valueName : attrValues1.keySet()) {
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
      List<StyleItemResourceValue> styleItems1 = new ArrayList<>(style1.getDefinedItems());
      List<StyleItemResourceValue> styleItems2 = new ArrayList<>(style2.getDefinedItems());
      if (styleItems1.size() != styleItems2.size()) {
        return false;
      }
      styleItems1.sort(VALUE_COMPARATOR);
      styleItems2.sort(VALUE_COMPARATOR);
      for (int i = 0; i < styleItems1.size(); i++) {
        StyleItemResourceValue item1 = styleItems1.get(i);
        StyleItemResourceValue item2 = styleItems2.get(i);
        if (!areEquivalentResourceValues(item1, item2, true, enumMap)) {
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
        if (!areEquivalentValues(array1.getElement(i), array2.getElement(i))) {
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
        if (!plural1.getQuantity(i).equals(plural2.getQuantity(i)) || !areEquivalentValues(plural1.getValue(i), plural2.getValue(i))) {
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
    if (value2 instanceof AarResourceItem) {
      if (v2.startsWith("apk:")) {
        String[] parts = v2.split(":");
        if (parts.length == 3 && parts[1].endsWith("/res.apk") &&
            (parts[1].substring(0, parts[1].length() - "/res.apk".length() + 1) + parts[2]).equals(v1)) {
          return true;
        }
      }
      return true;
    }

    switch (value1.getResourceType()) {
      case COLOR:
      case DRAWABLE:
        if (v1.startsWith("#") && v1.equalsIgnoreCase(v2)) {
          return true; // Hexadecimal representations of the same color may differ by case.
        }
        break;

      case STRING:
        return areEquivalentStrings(v1, v2);

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
    if (!v1.isEmpty() && !v2.isEmpty() && Character.isLetter(v1.charAt(0)) && (Character.isDigit(v2.charAt(0)) || v2.charAt(0) == '-')) {
      // The second value is a number, but the first value is not. Try to convert the first value to a number
      // and compare again.
      try {
        if (getNumericValue(value1.getName(), v1, enumMap) == Long.decode(v2).intValue()) {
          return true;
        }
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
    if (!areEquivalentValues(v1, v2)) {
      return false;
    }
    return true;
  }

  private static boolean areEquivalentGroupNames(@Nullable String groupName1, @Nullable String groupName2) {
    // Proto resource repository always returns null attr group names, so don't compare group names if at least one of them is null.
    return groupName1 == null || groupName2 == null || groupName1.equals(groupName2);
  }

  private static int getNumericValue(@NotNull String attributeName, @NotNull String value,
                                     @NotNull Map<String, Map<String, Integer>> enumMap) {
    Map<String, Integer> map = enumMap.get(attributeName);
    if (map == null) {
      throw new IllegalArgumentException(attributeName);
    }
    int result = 0;
    for (String name : FLAG_SPLITTER.split(value)) {
      Integer v = map.get(name);
      if (v == null) {
        throw new IllegalArgumentException(value);
      }
      result |= v;
    }
    return result;
  }

  @NotNull
  private static String normalizeDimensionValue(@NotNull String value) {
    Matcher matcher = DIMEN_PATTERN.matcher(value);
    if (!matcher.matches()) {
      return value;
    }
    String number = trimInsignificantZeros(matcher.group("number"));
    String suffix = matcher.group("suffix");
    if (suffix.equals("dip")) {
      suffix = "dp";
    }
    return number + suffix;
  }

  @NotNull
  private static String normalizeNumericValue(@NotNull String value) {
    Matcher matcher = NUMBER_PATTERN.matcher(value);
    if (!matcher.matches()) {
      return value;
    }
    return trimInsignificantZeros(value);
  }

  private static boolean areEquivalentValues(@NotNull String v1, @NotNull String v2) {
    if (v1.equals(v2)) {
      return true;
    }
    if (normalizeNumericValue(v1).equals(normalizeNumericValue(v2))) {
      return true;
    }
    if (v1.startsWith("#") && v1.equalsIgnoreCase(v2)) {
      return true;
    }
    return areEquivalentStrings(v1, v2);
  }

  private static boolean areEquivalentStrings(@NotNull String v1, @NotNull String v2) {
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
    return false;
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
    StringBuilder formatSpecifier = new StringBuilder();
    boolean afterBackslash = false;
    boolean inFormatSpecifier = false;
    for (int i = 0; i < formatStr.length(); i++) {
      char c = formatStr.charAt(i);
      if (inFormatSpecifier) {
        appendWithEscaping(c, formatSpecifier, afterBackslash);
        if (c == 's' || c == 'd') {
          buf.append("(\\$\\{.*\\}|\\(.*\\)|").append(formatSpecifier).append(')');
          formatSpecifier.setLength(0);
          inFormatSpecifier = false;
        }
      } else {
        if (c == '%') {
          if (afterBackslash) {
            buf.append(c);
            afterBackslash = false;
          }
          else {
            formatSpecifier.append(c);
            inFormatSpecifier = true;
          }
        }
        else {
          afterBackslash = appendWithEscaping(c, buf, afterBackslash);
        }
      }
    }
    return Pattern.compile(buf.toString());
  }

  private static boolean appendWithEscaping(char c, @NotNull StringBuilder destination, boolean afterBackslash) {
    switch (c) {
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
        destination.append('\\').append(c);
        afterBackslash = c == '\\' && !afterBackslash;
        break;
      default:
        destination.append(c);
        afterBackslash = false;
        break;
    }
    return afterBackslash;
  }

  /**
   * Values of flags and enumerators are stored in res.apk in numerical form. In order to be able to compare
   * contents of a resource repository loaded from res.apk to contents of a repository loaded from the original
   * XML files, we convert symbolic representation of flag and enum values to numerical form. The returned map is
   * keyed by attribute names. The values are maps from symbolic labels to the corresponding numerical values.
   */
  static Map<String, Map<String, Integer>> loadEnumMap(@NotNull Path sdkResFolder) throws Exception {
    Path file = sdkResFolder.resolve("values/attrs.xml");
    Map<String, Map<String, Integer>> map = new HashMap<>();
    XmlPullParser xmlPullParser = XmlPullParserFactory.newInstance().newPullParser();
    xmlPullParser.setInput(Files.newInputStream(file), null);
    int eventType = xmlPullParser.getEventType();
    String attr = null;
    while (eventType != XmlPullParser.END_DOCUMENT) {
      if (eventType == XmlPullParser.START_TAG) {
        if (TAG_ATTR.equals(xmlPullParser.getName())) {
          attr = xmlPullParser.getAttributeValue(null, ATTR_NAME);
        } else if (TAG_ENUM.equals(xmlPullParser.getName()) || TAG_FLAG.equals(xmlPullParser.getName())) {
          String name = xmlPullParser.getAttributeValue(null, ATTR_NAME);
          String value = xmlPullParser.getAttributeValue(null, ATTR_VALUE);
          // Integer.decode cannot handle "ffffffff", see JDK issue 6624867.
          int i = Long.decode(value).intValue();
          assert attr != null;
          Map<String, Integer> attributeMap = map.get(attr);
          if (attributeMap == null) {
            attributeMap = new HashMap<>();
            map.put(attr, attributeMap);
          }
          attributeMap.put(name, i);
        }
      } else if (eventType == XmlPullParser.END_TAG) {
        if (TAG_ATTR.equals(xmlPullParser.getName())) {
          attr = null;
        }
      }
      eventType = xmlPullParser.next();
    }
    return map;
  }

  private static void updateEnumMap(@NotNull Map<String, Map<String, Integer>> enumMap, @NotNull AarSourceResourceRepository repository) {
    ResourceNamespace namespace = repository.getNamespace();
    Collection<ResourceItem> items = repository.getResources(namespace, ResourceType.ATTR).values();
    for (ResourceItem item: items) {
      ResourceValue attr = item.getResourceValue();
      if (attr instanceof AttrResourceValue) {
        Map<String, Integer> attributeValues = ((AttrResourceValue)attr).getAttributeValues();
        if (!attributeValues.isEmpty()) {
          Map<String, Integer> map = enumMap.computeIfAbsent(attr.getName(), name -> new HashMap<>());
          map.putAll(attributeValues);
        }
      }
    }
  }

  @NotNull
  private static Path getSdkResFolder() {
    String sdkPath = TestUtils.getSdk().toString();
    String platformDir = TestUtils.getLatestAndroidPlatform();
    return Paths.get(sdkPath, "platforms", platformDir, "/data/res");
  }

  private static void checkVisibility(@NotNull AarProtoResourceRepository repository) {
    Collection<ResourceItem> items = repository.getResources(repository.getNamespace(), ResourceType.STYLEABLE).values();
    TestCase.assertFalse(items.isEmpty());
    for (ResourceItem item : items) {
      TestCase.assertEquals(ResourceVisibility.PUBLIC, ((ResourceItemWithVisibility)item).getVisibility());
    }

    items = repository.getResources(repository.getNamespace(), ResourceType.DRAWABLE).values();
    TestCase.assertFalse(items.isEmpty());
    for (ResourceItem item : items) {
      TestCase.assertEquals(ResourceVisibility.PRIVATE_XML_ONLY, ((ResourceItemWithVisibility)item).getVisibility());
    }
  }

  private static void checkSourceAttachments(@NotNull AarProtoResourceRepository repository, @NotNull Path aarFolder) {
    List<ResourceItem> resources = repository.getAllResources();
    String xmlFilePrefix = "jar://" + aarFolder.resolve("res-src.jar").toString() + "!/" + getPackagePrefix(repository) + "0/res/";
    String nonXmlFilePrefix = "apk://" + aarFolder.resolve("res.apk").toString() + "!/res/";
    for (ResourceItem resource : resources) {
      PathString originalSource = resource.getOriginalSource();
      assertThat(originalSource).isNotNull();
      if (originalSource.getFileName().endsWith(".xml")) {
        assertThat(originalSource.toString()).startsWith(xmlFilePrefix);
      }
      else {
        assertThat(originalSource.toString()).startsWith(nonXmlFilePrefix);
      }
    }
  }

  public void testLoading() throws Exception {
    // Load enums and flags defined by the Android framework.
    Map<String, Map<String, Integer>> enumMap = loadEnumMap(getSdkResFolder());

    long loadTimeFromSources = 0;
    long loadTimeFromResApk = 0;
    int count = PRINT_STATS ? 100 : 1;
    for (int i = 0; i < count; i++) {
      ResourceNamespace namespace = ResourceNamespace.fromPackageName(LIBRARY_PACKAGE);
      long start = System.currentTimeMillis();
      AarSourceResourceRepository fromSources =
          AarSourceResourceRepository.createForTest(myAarFolder.resolve(SdkConstants.FD_RES), namespace, LIBRARY_NAME);
      loadTimeFromSources += System.currentTimeMillis() - start;
      start = System.currentTimeMillis();
      AarProtoResourceRepository fromResApk =
          AarProtoResourceRepository.create(myAarFolder.resolve(SdkConstants.FN_RESOURCE_STATIC_LIBRARY), LIBRARY_NAME);
      loadTimeFromResApk += System.currentTimeMillis() - start;
      assertEquals(LIBRARY_NAME, fromResApk.getLibraryName());
      assertEquals(namespace, fromResApk.getNamespace());
      if (i == 0) {
        // Update enumMap by adding enums defined by the library we are testing with.
        updateEnumMap(enumMap, fromSources);
        compareContents(fromSources, fromResApk, enumMap);
        checkVisibility(fromResApk);
        checkSourceAttachments(fromResApk, myAarFolder);
      }
    }
    if (PRINT_STATS) {
      System.out.println("Load time from sources: " + loadTimeFromSources / (count * 1000.)
                         + " sec, from res.apk: " + loadTimeFromResApk / (count * 1000.) + " sec");
    }
  }
}
