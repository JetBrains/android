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

import static com.google.common.truth.Truth.assertThat;

import com.android.ide.common.rendering.api.ArrayResourceValue;
import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.DensityBasedResourceValue;
import com.android.ide.common.rendering.api.PluralsResourceValue;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.StyleItemResourceValue;
import com.android.ide.common.rendering.api.StyleResourceValue;
import com.android.ide.common.rendering.api.StyleableResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.multi.CompatibilityRenderTarget;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link FrameworkResourceRepository}.
 */
public class FrameworkResourceRepositoryTest extends AndroidTestCase {
  /** Enables printing of repository statistics. */
  private static final boolean PRINT_STATS = false;

  private Path myResourceFolder;

  private void deleteRepositoryCache() throws IOException {
    for (boolean withLocaleResources : new boolean[] {false, true}) {
      Files.deleteIfExists(FrameworkResourceRepository.getCacheFile(myResourceFolder, withLocaleResources));
    }
  }

  /**
   * Returns the resource folder of the Android framework resources used by LayoutLib.
   */
  @NotNull
  private Path getSdkResFolder() {
    ConfigurationManager manager = ConfigurationManager.getOrCreateInstance(myModule);
    IAndroidTarget target = manager.getHighestApiTarget();
    if (target == null) {
      fail();
    }
    CompatibilityRenderTarget compatibilityTarget = StudioEmbeddedRenderTarget.getCompatibilityTarget(target);
    return Paths.get(compatibilityTarget.getLocation(), "data", "res").normalize();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myResourceFolder = getSdkResFolder();
    deleteRepositoryCache();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      deleteRepositoryCache();
    } finally {
      super.tearDown();
    }
  }

  public void testLoading() throws Exception {
    for (boolean withLocaleResources : new boolean[] {true, false}) {
      long loadTimeWithoutCache = 0;
      long loadTimeWithCache = 0;
      // Test loading without cache.
      int count = PRINT_STATS ? 100 : 1;
      if (PRINT_STATS) {
        long start = System.currentTimeMillis();
        for (int i = 0; i < count; ++i) {
          FrameworkResourceRepository.create(myResourceFolder.toFile(), withLocaleResources, false);
        }
        loadTimeWithoutCache += System.currentTimeMillis() - start;
      }

      FrameworkResourceRepository fromSourceFiles =
          FrameworkResourceRepository.create(myResourceFolder.toFile(), withLocaleResources, true);
      assertFalse(fromSourceFiles.isLoadedFromCache());
      checkContents(fromSourceFiles);

      // Test loading from cache.
      fromSourceFiles.waitUntilPersistentCacheCreated();
      for (int i = 0; i < count; ++i) {
        long start = System.currentTimeMillis();
        FrameworkResourceRepository fromCache =
            FrameworkResourceRepository.create(myResourceFolder.toFile(), withLocaleResources, true);
        loadTimeWithCache += System.currentTimeMillis() - start;
        if (i == 0) {
          assertTrue(fromCache.isLoadedFromCache());
          compareContents(fromSourceFiles, fromCache);
          checkContents(fromCache);
        }
      }

      if (PRINT_STATS) {
        String type = withLocaleResources ? "Load time" : "Load time without locale resources";
        System.out.println(type + " without cache: " + loadTimeWithoutCache / (count * 1000.)
                           + " sec, with cache " + loadTimeWithCache / (count * 1000.) + " sec");
      }
    }
  }

  private static void compareContents(@NotNull ResourceRepository expected, @NotNull ResourceRepository actual) {
    List<ResourceItem> expectedItems = new ArrayList<>(expected.getAllResources());
    List<ResourceItem> actualItems = new ArrayList<>(actual.getAllResources());

    Comparator<ResourceItem> comparator = (item1, item2) -> {
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
      return item1.getSource().compareTo(item2.getSource());
    };
    expectedItems.sort(comparator);
    actualItems.sort(comparator);
    assertEquals(expectedItems.size(), actualItems.size());
    for (int i = 0; i < expectedItems.size(); i++) {
      ResourceItem expectedItem = expectedItems.get(i);
      ResourceItem actualItem = actualItems.get(i);
      assertTrue("Different ResourceItem at position " + i, areEquivalent(expectedItem, actualItem));
      assertEquals("Different FolderConfiguration at position " + i, expectedItem.getConfiguration(), actualItem.getConfiguration());
      ResourceValue expectedValue = expectedItem.getResourceValue();
      ResourceValue actualValue = actualItem.getResourceValue();
      if (!areEquivalentResourceValues(expectedValue, actualValue)) {
        assertEquals("Different ResourceValue at position " + i, expectedValue, actualValue);
      }
    }

    for (ResourceType type : ResourceType.values()) {
      List<ResourceItem> expectedPublic = new ArrayList<>(expected.getPublicResources(ResourceNamespace.ANDROID, type));
      List<ResourceItem> actualPublic = new ArrayList<>(actual.getPublicResources(ResourceNamespace.ANDROID, type));
      assertEquals("Number of public resources doesn't match for type " + type.getName(), expectedPublic.size(), actualPublic.size());
      expectedPublic.sort(comparator);
      actualPublic.sort(comparator);
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
      if (!attrValues1.equals(attrValues2)) {
        return false;
      }
      for (String valueName: attrValues1.keySet()) {
        if (!Objects.equals(attr1.getValueDescription(valueName), attr2.getValueDescription(valueName))) {
          return false;
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
    if (!Objects.equals(v1, v2)) {
      return false;
    }

    return true;
  }

  private static void checkContents(@NotNull ResourceRepository repository) {
    checkPublicResourcesCount(repository);
    checkAttributes(repository);
    checkIdResources(repository);
  }

  private static void checkAttributes(@NotNull ResourceRepository repository) {
    // `typeface` is declared first at top-level and later referenced from within `<declare-styleable>`.
    // Make sure the later reference doesn't shadow the original definition.
    AttrResourceValue attrValue = getAttrValue(repository, "typeface");
    assertThat(attrValue).isNotNull();
    assertThat(attrValue.getFormats()).containsExactly(AttributeFormat.ENUM);
    assertThat(attrValue.getDescription()).isEqualTo("Default text typeface.");
    assertThat(attrValue.getGroupName()).isEqualTo("Other non-theme attributes");
    Map<String, Integer> valueMap = attrValue.getAttributeValues();
    assertThat(valueMap.size()).isEqualTo(4);
    assertThat(valueMap).containsEntry("monospace", 3);
    assertThat(attrValue.getValueDescription("monospace")).isNull();

    // `appCategory` is defined only in attr_manifest.xml.
    attrValue = getAttrValue(repository, "appCategory");
    assertThat(attrValue).isNotNull();
    assertThat(attrValue.getFormats()).containsExactly(AttributeFormat.ENUM);
    assertThat(attrValue.getDescription()).startsWith("Declare the category of this app");
    assertThat(attrValue.getGroupName()).isNull();
    valueMap = attrValue.getAttributeValues();
    assertThat(valueMap.size()).isAtLeast(7);
    assertThat(valueMap).containsEntry("maps", 6);
    assertThat(attrValue.getValueDescription("maps")).contains("navigation");
  }

  private static AttrResourceValue getAttrValue(@NotNull ResourceRepository repository,
                                                @NotNull String attrName) {
    ResourceItem attrItem = repository.getResources(ResourceNamespace.ANDROID, ResourceType.ATTR, attrName).get(0);
    return (AttrResourceValue)attrItem.getResourceValue();
  }

  private static void checkIdResources(@NotNull ResourceRepository repository) {
    List<ResourceItem> items = repository.getResources(ResourceNamespace.ANDROID, ResourceType.ID, "mode_normal");
    items = items.stream().filter(item -> item.getConfiguration().isDefault()).collect(Collectors.toList());
    assertThat(items).hasSize(1);
  }

  private static void checkPublicResourcesCount(@NotNull ResourceRepository repository) {
    List<ResourceItem> resourceItems = repository.getAllResources();
    assertTrue("Too few resources: " + resourceItems.size(), resourceItems.size() >= 10000);
    for (ResourceItem item : resourceItems) {
      assertEquals(ResourceNamespace.ANDROID, item.getNamespace());
    }
    ImmutableMap<ResourceType, Integer> expectations = ImmutableMap.of(
      ResourceType.STYLE, 700,
      ResourceType.ATTR, 1200,
      ResourceType.DRAWABLE, 600,
      ResourceType.ID, 60,
      ResourceType.LAYOUT, 20
    );
    for (ResourceType type : ResourceType.values()) {
      Collection<ResourceItem> publicItems = repository.getPublicResources(ResourceNamespace.ANDROID, type);
      Integer minExpected = expectations.get(type);
      if (minExpected != null) {
        assertTrue("Too few public resources of type " + type.getName(), publicItems.size() >= minExpected);
      }
    }
  }
}
