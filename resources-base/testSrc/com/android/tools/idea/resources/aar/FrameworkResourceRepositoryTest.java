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

import static com.android.SdkConstants.FD_DATA;
import static com.android.SdkConstants.FD_RES;
import static com.android.tools.idea.res.ResourceAsserts.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import com.android.ide.common.rendering.api.AttrResourceValue;
import com.android.ide.common.rendering.api.AttributeFormat;
import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceItemWithVisibility;
import com.android.ide.common.resources.ResourceRepository;
import com.android.resources.ResourceType;
import com.android.resources.ResourceVisibility;
import com.android.sdklib.IAndroidTarget;
import com.android.utils.PathUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.intellij.testFramework.PlatformTestCase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import org.jetbrains.android.sdk.StudioEmbeddedRenderTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Tests for {@link FrameworkResourceRepository}.
 */
public class FrameworkResourceRepositoryTest extends PlatformTestCase {
  /** Enables printing of repository statistics. */
  private static final boolean PRINT_STATS = false;

  private Path myResourceFolder;
  private Path myTempDir;

  @NotNull
  private Path getCacheFile() {
    return myTempDir.resolve("cache.bin");
  }

  @NotNull
  private CachingData createCachingData(@Nullable Executor cacheCreationExecutor) {
    return new CachingData(getCacheFile(), "", "", cacheCreationExecutor);
  }

  /**
   * Returns the resource folder of the Android framework resources used by LayoutLib.
   */
  @NotNull
  private static Path getFrameworkResDir() {
    IAndroidTarget renderTarget = StudioEmbeddedRenderTarget.getInstance();
    return Paths.get(renderTarget.getLocation(), FD_DATA, FD_RES).normalize();
  }

  /** Returns the path of a freshly built framework_res.jar. */
  @NotNull
  private Path getFrameworkResJar() throws IOException {
    Path path = myTempDir.resolve("framework_res.jar");
    FrameworkResJarCreator.createJar(getFrameworkResDir(), path);
    return path;
  }

  private static void assertVisibility(
      @NotNull ResourceRepository repository, @NotNull ResourceType type, @NotNull String name, @NotNull ResourceVisibility visibility) {
    List<ResourceItem> resources = repository.getResources(ResourceNamespace.ANDROID, type, name);
    assertThat(resources).isNotEmpty();
    assertThat(((ResourceItemWithVisibility)resources.get(0)).getVisibility()).isEqualTo(visibility);
  }

  private static void compareContents(@NotNull ResourceRepository expected, @NotNull ResourceRepository actual) {
    List<ResourceItem> expectedItems = new ArrayList<>(expected.getAllResources());
    List<ResourceItem> actualItems = new ArrayList<>(actual.getAllResources());

    Comparator<ResourceItem> comparator = Comparator
        .comparing(ResourceItem::getType)
        .thenComparing(ResourceItem::getNamespace)
        .thenComparing(ResourceItem::getName)
        .thenComparing(ResourceItem::getSource);
    expectedItems.sort(comparator);
    actualItems.sort(comparator);
    assertThat(actualItems.size()).isEqualTo(expectedItems.size());
    for (int i = 0; i < expectedItems.size(); i++) {
      ResourceItem expectedItem = expectedItems.get(i);
      ResourceItem actualItem = actualItems.get(i);
      assertThat(actualItem).isEquivalentTo(expectedItem);
    }

    for (ResourceType type : ResourceType.values()) {
      List<ResourceItem> expectedPublic = new ArrayList<>(expected.getPublicResources(ResourceNamespace.ANDROID, type));
      List<ResourceItem> actualPublic = new ArrayList<>(actual.getPublicResources(ResourceNamespace.ANDROID, type));
      assertWithMessage("Number of public resources doesn't match for type " + type.getName())
          .that(actualPublic.size()).isEqualTo(expectedPublic.size());
      expectedPublic.sort(comparator);
      actualPublic.sort(comparator);
      for (int i = 0; i < expectedPublic.size(); i++) {
        ResourceItem expectedItem = expectedPublic.get(i);
        ResourceItem actualItem = actualPublic.get(i);
        assertThat(actualItem).isEquivalentTo(expectedItem);
      }
    }
  }

  private static void checkContents(@NotNull ResourceRepository repository) {
    checkPublicResources(repository);
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

    // Check that ID resources defined using @+id syntax in layout XML files are present in the repository.
    // The following ID resource is defined by android:id="@+id/radio_power" in layout/power_dialog.xml.
    items = repository.getResources(ResourceNamespace.ANDROID, ResourceType.ID, "radio_power");
    assertThat(items).hasSize(1);
  }

  private static void checkPublicResources(@NotNull ResourceRepository repository) {
    List<ResourceItem> resourceItems = repository.getAllResources();
    assertWithMessage("Too few resources: " + resourceItems.size()).that(resourceItems.size()).isAtLeast(10000);
    for (ResourceItem item : resourceItems) {
      assertThat(item.getNamespace()).isEqualTo(ResourceNamespace.ANDROID);
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
        assertWithMessage("Too few public resources of type " + type.getName()).that(publicItems.size()).isAtLeast(minExpected);
      }
    }

    // Not mentioned in public.xml.
    assertVisibility(repository, ResourceType.STRING, "byteShort", ResourceVisibility.PRIVATE);
    // Defined at top level.
    assertVisibility(repository, ResourceType.STYLE, "Widget.DeviceDefault.Button.Colored", ResourceVisibility.PUBLIC);
    // Defined inside a <public-group>.
    assertVisibility(repository, ResourceType.ATTR, "packageType", ResourceVisibility.PUBLIC);
    assertVisibility(repository, ResourceType.DRAWABLE, "ic_info", ResourceVisibility.PRIVATE); // Due to the @hide comment
    assertVisibility(repository, ResourceType.ATTR, "__removed2", ResourceVisibility.PRIVATE); // Due to the naming convention
  }

  private static void checkLanguages(@NotNull FrameworkResourceRepository fromSourceFiles, @Nullable Set<String> languages) {
    if (languages == null) {
      assertThat(fromSourceFiles.getLanguageGroups().size()).isAtLeast(75);
    }
    else {
      assertThat(fromSourceFiles.getLanguageGroups()).isEqualTo(Sets.union(ImmutableSet.of(""), languages));
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myResourceFolder = getFrameworkResDir();
    myTempDir = Files.createTempDirectory("temp");
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      PathUtils.deleteRecursivelyIfExists(myTempDir);
    } finally {
      super.tearDown();
    }
  }

  public void testLoadingFromSourcesAndCache() throws Exception {
    for (Set<String> languages : Arrays.asList(ImmutableSet.<String>of(), ImmutableSet.of("fr", "it"), null)) {
      // Create persistent cache.
      PathUtils.deleteRecursivelyIfExists(myTempDir);
      FrameworkResourceRepository.create(myResourceFolder, languages, createCachingData(directExecutor()), false);

      long loadTimeFromSources = 0;
      long loadTimeFromCache = 0;
      int count = PRINT_STATS ? 100 : 1;
      for (int i = 0; i < count; ++i) {
        long start = System.currentTimeMillis();
        FrameworkResourceRepository fromSourceFiles = FrameworkResourceRepository.create(myResourceFolder, languages, null, false);
        loadTimeFromSources += System.currentTimeMillis() - start;
        if (i == 0) {
          checkLanguages(fromSourceFiles, languages);
          assertThat(fromSourceFiles.isLoadedFromCache()).isFalse();
          checkContents(fromSourceFiles);
        }
        start = System.currentTimeMillis();
        FrameworkResourceRepository fromCache =
            FrameworkResourceRepository.create(myResourceFolder, languages, createCachingData(null), false);
        loadTimeFromCache += System.currentTimeMillis() - start;
        if (i == 0) {
          checkLanguages(fromSourceFiles, languages);
          assertThat(fromCache.isLoadedFromCache()).isTrue();
          compareContents(fromSourceFiles, fromCache);
          checkContents(fromCache);
        }
      }

      if (PRINT_STATS) {
        String type = "Load time with " + (languages == null ? "all" : languages.size()) + " languages";
        System.out.println(type + " without cache: " + loadTimeFromSources / (count * 1000.)
                           + " sec, with cache " + loadTimeFromCache / (count * 1000.) + " sec");
      }
    }
  }

  public void testLoadingFromSourcesAndJar() throws Exception {
    Path frameworkResJar = getFrameworkResJar();
    for (Set<String> languages : Arrays.asList(ImmutableSet.<String>of(), ImmutableSet.of("fr", "de"), null)) {
      long loadTimeFromSources = 0;
      long loadTimeFromJar = 0;
      int count = PRINT_STATS ? 100 : 1;
      for (int i = 0; i < count; ++i) {
        long start = System.currentTimeMillis();
        FrameworkResourceRepository fromSourceFiles = FrameworkResourceRepository.create(myResourceFolder, languages, null, false);
        loadTimeFromSources += System.currentTimeMillis() - start;
        checkLanguages(fromSourceFiles, languages);
        if (i == 0) {
          assertThat(fromSourceFiles.isLoadedFromCache()).isFalse();
          checkContents(fromSourceFiles);
        }
        start = System.currentTimeMillis();
        FrameworkResourceRepository fromJar = FrameworkResourceRepository.create(frameworkResJar, languages, null, false);
        loadTimeFromJar += System.currentTimeMillis() - start;
        checkLanguages(fromSourceFiles, languages);
        if (i == 0) {
          assertThat(fromJar.isLoadedFromCache()).isFalse();
          compareContents(fromSourceFiles, fromJar);
          checkContents(fromJar);
        }
      }

      if (PRINT_STATS) {
        String type = "Load time with " + (languages == null ? "all" : languages.size()) + " languages";
        System.out.println(type + " from source files: " + loadTimeFromSources / (count * 1000.)
                           + " sec, from framework_res.jar file " + loadTimeFromJar / (count * 1000.) + " sec");
      }
    }
  }

  public void testIncrementalLoadingFromJar() throws Exception {
    Path frameworkResJar = getFrameworkResJar();
    FrameworkResourceRepository withFrench = FrameworkResourceRepository.create(frameworkResJar, ImmutableSet.of("fr"), null, false);
    checkLanguages(withFrench, ImmutableSet.of("fr"));
    assertThat(withFrench.isLoadedFromCache()).isFalse();
    checkContents(withFrench);
    FrameworkResourceRepository withFrenchAndGerman = withFrench.loadMissingLanguages(ImmutableSet.of("de"), null);
    checkLanguages(withFrenchAndGerman, ImmutableSet.of("fr", "de"));
    assertThat(withFrenchAndGerman.isLoadedFromCache()).isFalse();
    checkContents(withFrenchAndGerman);
  }

  public void testIncrementalLoadingFromCacheAndSources() {
    // Create persistent cache for language-neutral, French and German.
    CachingData cachingData = createCachingData(directExecutor());
    FrameworkResourceRepository.create(myResourceFolder, ImmutableSet.of("fr", "de"), cachingData, false);

    FrameworkResourceRepository withoutLanguages = FrameworkResourceRepository.create(myResourceFolder, ImmutableSet.of(), cachingData, false);
    checkLanguages(withoutLanguages, ImmutableSet.of());
    assertThat(withoutLanguages.isLoadedFromCache()).isTrue();
    assertThat(withoutLanguages.getNumberOfLanguageGroupsLoadedFromCache()).isEqualTo(1);
    checkContents(withoutLanguages);

    FrameworkResourceRepository withFrench = withoutLanguages.loadMissingLanguages(ImmutableSet.of("fr"), cachingData);
    checkLanguages(withFrench, ImmutableSet.of("fr"));
    assertThat(withFrench.isLoadedFromCache()).isTrue();
    assertThat(withFrench.getNumberOfLanguageGroupsLoadedFromCache()).isEqualTo(2);
    checkContents(withFrench);

    FrameworkResourceRepository withFrenchGermanItalian = withFrench.loadMissingLanguages(ImmutableSet.of("de", "it"), cachingData);
    checkLanguages(withFrenchGermanItalian, ImmutableSet.of("fr", "de", "it"));
    assertThat(withFrenchGermanItalian.isLoadedFromCache()).isFalse();
    // German should be loaded from cache, but Italian should not.
    assertThat(withFrenchGermanItalian.getNumberOfLanguageGroupsLoadedFromCache()).isEqualTo(3);
    checkContents(withFrenchGermanItalian);

    // Check that the previous repository loading created a cache file for Italian.
    FrameworkResourceRepository withItalian = FrameworkResourceRepository.create(myResourceFolder, ImmutableSet.of("it"), cachingData, false);
    checkLanguages(withItalian, ImmutableSet.of("it"));
    assertThat(withItalian.isLoadedFromCache()).isTrue();
    assertThat(withItalian.getNumberOfLanguageGroupsLoadedFromCache()).isEqualTo(2);
    checkContents(withItalian);
  }
}
