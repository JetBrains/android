/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.npw.module;

import static com.android.tools.idea.flags.StudioFlags.NPW_TEMPLATES_AUTOMOTIVE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.Mockito.when;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.npw.benchmark.NewBenchmarkModuleDescriptionProvider;
import com.android.tools.idea.npw.dynamicapp.NewDynamicAppModuleDescriptionProvider;
import com.android.tools.idea.npw.importing.ImportModuleGalleryEntryProvider;
import com.android.tools.idea.npw.java.NewJavaModuleDescriptionProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.mockito.Mockito;

public class ChooseModuleTypeStepTest extends AndroidGradleTestCase {
  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.NPW_BENCHMARK_TEMPLATE_MODULE.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testSortSingleModuleEntries() {
    Assert.assertThat(sort("Phone & Tablet Module"), equalToList("Phone & Tablet Module"));
  }

  public void testSortTwoModuleEntries() {
    Assert.assertThat(sort("Android Library", "Phone & Tablet Module"), equalToList("Phone & Tablet Module", "Android Library"));
    Assert.assertThat(sort("A", "Phone & Tablet Module"), equalToList("Phone & Tablet Module", "A"));
    Assert.assertThat(sort("Wear OS Module", "A"), equalToList("Wear OS Module", "A"));
    Assert.assertThat(sort("C", "A"), equalToList("A", "C"));
  }

  public void testSortFullModuleEntries() {
    Assert.assertThat(
      sort("Z", "Import .JAR/.AAR Package", "Android Library", "Phone & Tablet Module",
           "Wear OS Module", "Android TV Module", "Import Gradle Project", "Import Eclipse ADT Project", "Google Cloud Module",
           "Java Library", "Benchmark Module", "A"),
      equalToList("Phone & Tablet Module", "Android Library", "Wear OS Module",
                  "Android TV Module", "Import Gradle Project", "Import Eclipse ADT Project", "Import .JAR/.AAR Package", "Java Library",
                  "Google Cloud Module", "Benchmark Module", "A", "Z")
    );
  }

  /**
   * This test exists to ensure that template names have stayed consistent. If a template name has changed and we should update our
   * module order, please update {@link ChooseModuleTypeStep#sortModuleEntries(List)}
   */
  public void testSortExistingModuleEntries_FlagFalse() {
    // Note: Cloud Module is not in the class path, so we don't test it (is the last one anyway)
    StudioFlags.NPW_BENCHMARK_TEMPLATE_MODULE.override(true);
    ArrayList<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>();
    moduleDescriptions.addAll(new ImportModuleGalleryEntryProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewAndroidModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewDynamicAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewJavaModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewBenchmarkModuleDescriptionProvider().getDescriptions(getProject()));

    List<String> sortedEntries = ChooseModuleTypeStep.sortModuleEntries(moduleDescriptions).stream()
      .map(ModuleGalleryEntry::getName).collect(Collectors.toList());

    List<String> expectedEntries = filterExpectedEntries(
      "Phone & Tablet Module", "Android Library", "Dynamic Feature Module", "Instant Dynamic Feature Module",
      "Automotive Module", "Wear OS Module", "Android TV Module", "Android Things Module", "Import Gradle Project",
      "Import Eclipse ADT Project", "Import .JAR/.AAR Package", "Java Library", "Benchmark Module"
    );

    Assert.assertThat(sortedEntries, equalTo(expectedEntries));
  }

  public void testSortExistingModuleEntries_FlagTrue() {
    // Note: Cloud Module is not in the class path, so we don't test it (is the last one anyway)
    StudioFlags.NPW_BENCHMARK_TEMPLATE_MODULE.override(true);
    ArrayList<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>();
    moduleDescriptions.addAll(new ImportModuleGalleryEntryProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewAndroidModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewDynamicAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewJavaModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewBenchmarkModuleDescriptionProvider().getDescriptions(getProject()));

    List<String> sortedEntries = ChooseModuleTypeStep.sortModuleEntries(moduleDescriptions).stream()
      .map(ModuleGalleryEntry::getName).collect(Collectors.toList());

    List<String> expectedEntries = filterExpectedEntries(
      "Phone & Tablet Module", "Android Library", "Dynamic Feature Module", "Instant Dynamic Feature Module", "Automotive Module",
      "Wear OS Module",
      "Android TV Module", "Android Things Module", "Import Gradle Project", "Import Eclipse ADT Project",
      "Import .JAR/.AAR Package", "Java Library", "Benchmark Module"
    );

    Assert.assertThat(sortedEntries, equalTo(expectedEntries));
  }

  public void testSortExistingModuleEntries_HideModulesFalse_InstantDynamicTrue() {
    StudioFlags.NPW_BENCHMARK_TEMPLATE_MODULE.override(true);
    ArrayList<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>();
    moduleDescriptions.addAll(new ImportModuleGalleryEntryProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewAndroidModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewDynamicAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewJavaModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewBenchmarkModuleDescriptionProvider().getDescriptions(getProject()));

    List<String> sortedEntries = ChooseModuleTypeStep.sortModuleEntries(moduleDescriptions).stream()
      .map(ModuleGalleryEntry::getName).collect(Collectors.toList());

    List<String> expectedEntries = filterExpectedEntries(
      "Phone & Tablet Module", "Android Library", "Dynamic Feature Module", "Instant Dynamic Feature Module",
      "Automotive Module", "Wear OS Module", "Android TV Module", "Android Things Module",
      "Import Gradle Project", "Import Eclipse ADT Project", "Import .JAR/.AAR Package", "Java Library", "Benchmark Module"
    );

    Assert.assertThat(sortedEntries, equalTo(expectedEntries));
  }

  public void testSortExistingModuleEntries_HideBenchmarkModule() {
    StudioFlags.NPW_BENCHMARK_TEMPLATE_MODULE.override(false);
    ArrayList<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>();
    moduleDescriptions.addAll(new ImportModuleGalleryEntryProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewAndroidModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewDynamicAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewJavaModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewBenchmarkModuleDescriptionProvider().getDescriptions(getProject()));

    List<String> sortedEntries = ChooseModuleTypeStep.sortModuleEntries(moduleDescriptions).stream()
      .map(ModuleGalleryEntry::getName).collect(Collectors.toList());

    List<String> expectedEntries = filterExpectedEntries(
      "Phone & Tablet Module", "Android Library", "Dynamic Feature Module", "Instant Dynamic Feature Module",
      "Automotive Module", "Wear OS Module", "Android TV Module", "Android Things Module",
      "Import Gradle Project", "Import Eclipse ADT Project", "Import .JAR/.AAR Package", "Java Library"
    );

    Assert.assertThat(sortedEntries, equalTo(expectedEntries));
  }

  @NotNull
  private static List<String> sort(@NotNull String... entries) {
    List<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>(entries.length);
    for (String entry : entries) {
      ModuleGalleryEntry moduleGalleryEntry = Mockito.mock(ModuleGalleryEntry.class);
      when(moduleGalleryEntry.getName()).thenReturn(entry);
      moduleDescriptions.add(moduleGalleryEntry);
    }

    List<ModuleGalleryEntry> sortedEntries = ChooseModuleTypeStep.sortModuleEntries(moduleDescriptions);
    Assert.assertEquals(entries.length, sortedEntries.size());

    List<String> result = new ArrayList<>(entries.length);
    for (ModuleGalleryEntry galleryEntry : sortedEntries) {
      result.add(galleryEntry.getName());
    }

    return result;
  }

  private static Matcher<List<String>> equalToList(String... operand) {
    return equalTo(Arrays.asList(operand));
  }

  /**
   * Filters a list of expected entries, excluding those that are disabled.
   */
  private static List<String> filterExpectedEntries(String... expectedEntries) {
    return Arrays.stream(expectedEntries)
      .filter(entry -> !entry.equals("Automotive Module") || NPW_TEMPLATES_AUTOMOTIVE.get())
      .collect(Collectors.toList());
  }
}
