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
import com.android.ide.common.rendering.api.*;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceItemWithVisibility;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.ide.common.resources.configuration.ScreenSizeQualifier;
import com.android.ide.common.util.PathString;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.resources.ResourceUrl;
import com.android.resources.ResourceVisibility;
import com.android.testutils.TestUtils;
import com.android.utils.XmlUtils;
import com.google.common.base.Splitter;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tests for {@link AarProtoResourceRepository}.
 */
public class AarProtoResourceRepositoryTest extends AndroidTestCase {
  /** Enables printing of repository loading statistics. */
  private static final boolean PRINT_STATS = false;

  private static final String LIBRARY_NAME = "design-27.0.2";
  private static final String LIBRARY_PACKAGE = "android.support.design";

  private static final String TAG_ATTR = "attr";
  private static final String TAG_ENUM = "enum";
  private static final String TAG_FLAG = "flag";
  private static final String ATTR_NAME = "name";
  private static final String ATTR_VALUE = "value";
  private static final Pattern DIMEN_PATTERN = Pattern.compile("(?<number>-?\\d+(\\.\\d*)?)(?<suffix>px|dp|dip|sp|pt|in|mm|)");
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

  private File myAarFolder;
  /**
   * Values of flags and enumerators are stored in res.apk in numerical form. In order to be able to compare
   * contents of a resource repository loaded from res.apk to contents of a repository loaded from the original
   * XML files, we convert symbolic representation of flag and enum values to numerical form. This map is
   * keyed by attribute names. The values are maps from symbolic labels to the corresponding numerical values.
   */
  private Map<String, Map<String, Integer>> myEnumMap;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myAarFolder = new File(myFixture.getTestDataPath(), "design_aar");
  }

  private void compareContents(@NotNull AbstractResourceRepository expected, @NotNull AbstractResourceRepository actual) {
    List<ResourceItem> expectedItems = new ArrayList<>(expected.getAllResourceItems());
    List<ResourceItem> actualItems = new ArrayList<>(actual.getAllResourceItems());

    expectedItems.sort(ITEM_COMPARATOR);
    actualItems.sort(ITEM_COMPARATOR);

    ResourceItem previousItem = null;
    for (int i = 0, j = 0; i < expectedItems.size() || j < actualItems.size(); i++, j++) {
      ResourceItem expectedItem = i < expectedItems.size() ? expectedItems.get(i) : null;
      ResourceItem actualItem = j < actualItems.size() ? actualItems.get(j) : null;
      if (actualItem == null || expectedItem == null || !areEquivalent(expectedItem, actualItem)) {
        if (actualItem != null && actualItem.getType() == ResourceType.ID) {
          // AarSourceResourceRepository does not create ID resources for some attr values and for inline ID declarations ("@+id/name").
          i--; // Skip the ID resource.
        } else if (actualItem != null && actualItem.getName().startsWith("$")) {
          i--; // Ignore the resource corresponding to the extracted aapt tag.
        } else {
          if (expectedItem == null) {
            fail("Unexpected ResourceItem at position " + j);
          }
          assertTrue("Different ResourceItem at position " + i, previousItem != null && areEquivalent(expectedItem, previousItem));
          assertTrue("Different ResourceValue at position " + i,
                     areEquivalentResourceValues(expectedItem.getResourceValue(), previousItem.getResourceValue()));
          FolderConfiguration expectedConfiguration = expectedItem.getConfiguration();
          FolderConfiguration previousConfiguration = previousItem.getConfiguration();
          ScreenSizeQualifier expectedQualifier = expectedConfiguration.getScreenSizeQualifier();
          ScreenSizeQualifier previousQualifier = previousConfiguration.getScreenSizeQualifier();
          assertTrue("Screen size does not match at position " + i, previousQualifier.isMatchFor(expectedQualifier));
          FolderConfiguration config = FolderConfiguration.copyOf(previousConfiguration);
          config.setScreenSizeQualifier(expectedQualifier);
          assertEquals("Different FolderConfiguration at position " + i, expectedConfiguration, config);
          j--;
        }
      } else {
        assertTrue("Different ResourceItem at position " + i, areEquivalent(expectedItem, actualItem));
        assertTrue("Different ResourceValue at position " + i,
                   areEquivalentResourceValues(expectedItem.getResourceValue(), actualItem.getResourceValue()));
        previousItem = actualItem;
      }
    }

    for (ResourceType type : ResourceType.values()) {
      List<ResourceItem> expectedPublic = new ArrayList<>(expected.getPublicResourcesOfType(type));
      List<ResourceItem> actualPublic = new ArrayList<>(actual.getPublicResourcesOfType(type));
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
    // ID resources in AARv2 always belong to the default configuration.
    if (item1.getType() != ResourceType.ID && !Objects.equals(item1.getConfiguration(), item2.getConfiguration())) {
      return false;
    }
    // TODO: AarValueResourceItem.getSource() method hasn't been fully implemented yet.
    if (item1 instanceof AarValueResourceItem || item2 instanceof AarValueResourceItem) {
      return true;
    }
    return areEquivalentSources(item1.getSource(), item2.getSource());
  }

  private static boolean areEquivalentSources(@Nullable PathString path1, @Nullable PathString path2) {
    if (Objects.equals(path1, path2)) {
      return true;
    }
    if (path1 != null && path2 != null) {
      URI filesystemUri1 = path1.getFilesystemUri();
      URI filesystemUri2 = path2.getFilesystemUri();
      if (filesystemUri1.equals(filesystemUri2)) {
        return path1.getPortablePath().replace("/res/res/", "/res/").equals(path2.getPortablePath().replace("/res/res/", "/res/"));
      } else {
        URI nonFileUri = filesystemUri2;
        if (filesystemUri2.getScheme().equals("file") && !filesystemUri1.getScheme().equals("file")) {
          PathString temp = path1;
          path1 = path2;
          path2 = temp;
          nonFileUri = path1.getFilesystemUri();
        }
        if (nonFileUri.getScheme().equals("apk")) {
          return path1.getPortablePath().equals(path2.getPortablePath().replace("/res.apk!/", "/"));
        }
      }
    }
    return false;
  }

  private boolean areEquivalentResourceValues(@Nullable ResourceValue value1, @Nullable ResourceValue value2) {
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

    List<AttrResourceValue> attrs1 =
      value1 instanceof StyleableResourceValue ? ((StyleableResourceValue)value1).getAllAttributes() : null;
    List<AttrResourceValue> attrs2 =
      value2 instanceof StyleableResourceValue ? ((StyleableResourceValue)value2).getAllAttributes() : null;
    if (!Objects.equals(attrs1, attrs2)) {
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
    if (value2 instanceof AarResourceItem && !(value1 instanceof AarResourceItem)) {
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
    if (!v1.isEmpty() && !v2.isEmpty() && Character.isLetter(v1.charAt(0)) && (Character.isDigit(v2.charAt(0)) || v2.charAt(0) == '-')) {
      // The second value is a number, but the first value is not. Try to convert the first value to a number
      // and compare again.
      try {
        if (getNumericValue(value1.getName(), v1) == Long.decode(v2).intValue()) {
          return true;
        }
      } catch (IllegalArgumentException e) {
        return false;
      }
    }
    return false;
  }

  private int getNumericValue(@NotNull String attributeName, @NotNull String value) {
    Map<String, Integer> map = myEnumMap.get(attributeName);
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

  private static Map<String, Map<String, Integer>> loadEnumMap() throws Exception {
    File res = getSdkResFolder();
    File file = new File(res, "values/attrs.xml");
    Map<String, Map<String, Integer>> map = new HashMap<>();
    XmlPullParser xmlPullParser = XmlPullParserFactory.newInstance().newPullParser();
    xmlPullParser.setInput(new FileInputStream(file), null);
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

  private void updateEnumMap(@NotNull AarSourceResourceRepository repository) {
    ResourceNamespace namespace = repository.getNamespace();
    List<ResourceItem> items = repository.getResourceItems(namespace, ResourceType.STYLEABLE);
    for (ResourceItem item : items) {
      ResourceValue value = item.getResourceValue();
      if (value instanceof StyleableResourceValue) {
        List<AttrResourceValue> attributes = ((StyleableResourceValue)value).getAllAttributes();
        for (AttrResourceValue attribute : attributes) {
          Map<String, Integer> map = myEnumMap.get(attribute.getName());
          if (map == null) {
            map = new HashMap<>();
            myEnumMap.put(attribute.getName(), map);
          }
          Map<String, Integer> attributeValues = attribute.getAttributeValues();
          if (attributeValues != null) {
            map.putAll(attributeValues);
          }
        }
      }
    }
  }

  @NotNull
  private static File getSdkResFolder() {
    String sdkPath = TestUtils.getSdk().toString();
    String platformDir = TestUtils.getLatestAndroidPlatform();
    return new File(sdkPath + "/platforms/" + platformDir + "/data/res");
  }

  private static void checkVisibility(@NotNull AarProtoResourceRepository repository) {
    List<ResourceItem> items = repository.getResourceItems(repository.getNamespace(), ResourceType.STYLEABLE);
    assertFalse(items.isEmpty());
    for (ResourceItem item : items) {
      assertEquals(ResourceVisibility.PUBLIC, ((ResourceItemWithVisibility)item).getVisibility());
    }

    items = repository.getResourceItems(repository.getNamespace(), ResourceType.DRAWABLE);
    assertFalse(items.isEmpty());
    for (ResourceItem item : items) {
      assertEquals(ResourceVisibility.PRIVATE_XML_ONLY, ((ResourceItemWithVisibility)item).getVisibility());
    }
  }

  public void testLoading() throws Exception {
    myEnumMap = loadEnumMap();

    long loadTimeFromSources = 0;
    long loadTimeFromResApk = 0;
    int count = PRINT_STATS ? 100 : 1;
    for (int i = 0; i < count; i++) {
      ResourceNamespace namespace = ResourceNamespace.fromPackageName(LIBRARY_PACKAGE);
      long start = System.currentTimeMillis();
      AarSourceResourceRepository
        fromSources = AarSourceResourceRepository.createForTest(new File(myAarFolder, SdkConstants.FD_RES), namespace, LIBRARY_NAME);
      loadTimeFromSources += System.currentTimeMillis() - start;
      start = System.currentTimeMillis();
      AarProtoResourceRepository fromResApk =
        AarProtoResourceRepository.createProtoRepository(new File(myAarFolder, SdkConstants.FN_RESOURCE_STATIC_LIBRARY), LIBRARY_NAME);
      loadTimeFromResApk += System.currentTimeMillis() - start;
      assertEquals(LIBRARY_NAME, fromResApk.getLibraryName());
      assertEquals(namespace, fromResApk.getNamespace());
      if (i == 0) {
        updateEnumMap(fromSources);
        compareContents(fromSources, fromResApk);
        checkVisibility(fromResApk);
      }
    }
    if (PRINT_STATS) {
      System.out.println("Load time from sources: " + loadTimeFromSources / (count * 1000.)
                         + " sec, from res.apk: " + loadTimeFromResApk / (count * 1000.) + " sec");
    }
  }
}
