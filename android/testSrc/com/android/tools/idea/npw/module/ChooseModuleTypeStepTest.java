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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.instantapp.InstantAppSdks;
import com.android.tools.idea.npw.dynamicapp.NewDynamicAppModuleDescriptionProvider;
import com.android.tools.idea.npw.importing.ImportModuleGalleryEntryProvider;
import com.android.tools.idea.npw.instantapp.NewInstantAppModuleDescriptionProvider;
import com.android.tools.idea.npw.java.NewJavaModuleDescriptionProvider;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.google.common.collect.Lists;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.mockito.Mockito.when;

public class ChooseModuleTypeStepTest extends AndroidGradleTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();

    // Enable instant Apps (We can remove this later, when the SDK is made public)
    when(IdeComponents.mockApplicationService(InstantAppSdks.class, getTestRootDisposable()).isInstantAppSdkEnabled()).thenReturn(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.UAB_HIDE_INSTANT_MODULES_FOR_NON_FEATURE_PLUGIN_PROJECTS.clearOverride();
      StudioFlags.UAB_INSTANT_DYNAMIC_FEATURE_MODULE.clearOverride();
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
    Assert.assertThat(sort("Instant App", "A"), equalToList("Instant App", "A"));
    Assert.assertThat(sort("C", "A"), equalToList("A", "C"));
  }

  public void testSortFullModuleEntries() {
    Assert.assertThat(
      sort("Z", "Import .JAR/.AAR Package", "Android Library", "Phone & Tablet Module", "Instant App Feature Module", "Instant App",
           "Wear OS Module", "Android TV Module", "Import Gradle Project", "Import Eclipse ADT Project","Google Cloud Module",
           "Java Library", "A"),
      equalToList("Phone & Tablet Module", "Android Library", "Instant App", "Instant App Feature Module", "Wear OS Module",
                  "Android TV Module", "Import Gradle Project", "Import Eclipse ADT Project", "Import .JAR/.AAR Package", "Java Library",
                  "Google Cloud Module", "A", "Z")
    );
  }

  /**
   * This test exists to ensure that template names have stayed consistent. If a template name has changed and we should update our
   * module order, please update {@link ChooseModuleTypeStep#sortModuleEntries(List)}
   */
  public void testSortExistingModuleEntries_FlagFalse() {
    // Note: Cloud Module is not in the class path, so we don't test it (is the last one anyway)
    StudioFlags.UAB_HIDE_INSTANT_MODULES_FOR_NON_FEATURE_PLUGIN_PROJECTS.override(false);
    StudioFlags.UAB_INSTANT_DYNAMIC_FEATURE_MODULE.override(false);
    ArrayList<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>();
    moduleDescriptions.addAll(new ImportModuleGalleryEntryProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewAndroidModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewDynamicAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewInstantAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewJavaModuleDescriptionProvider().getDescriptions(getProject()));

    List<String> sortedEntries = ChooseModuleTypeStep.sortModuleEntries(moduleDescriptions).stream()
      .map(ModuleGalleryEntry::getName).collect(Collectors.toList());

    List<String> expectedEntries = Lists.newArrayList(
      "Phone & Tablet Module", "Android Library", "Dynamic Feature Module", "Instant App", "Instant App Feature Module", "Wear OS Module",
      "Android TV Module", "Android Things Module", "Import Gradle Project", "Import Eclipse ADT Project",
      "Import .JAR/.AAR Package", "Java Library"
    );

    Assert.assertThat(sortedEntries, equalTo(expectedEntries));
  }

  public void testSortExistingModuleEntries_FlagTrue() {
    // Note: Cloud Module is not in the class path, so we don't test it (is the last one anyway)
    StudioFlags.UAB_HIDE_INSTANT_MODULES_FOR_NON_FEATURE_PLUGIN_PROJECTS.override(true);
    StudioFlags.UAB_INSTANT_DYNAMIC_FEATURE_MODULE.override(false);
    ArrayList<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>();
    moduleDescriptions.addAll(new ImportModuleGalleryEntryProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewAndroidModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewDynamicAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewInstantAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewJavaModuleDescriptionProvider().getDescriptions(getProject()));

    List<String> sortedEntries = ChooseModuleTypeStep.sortModuleEntries(moduleDescriptions).stream()
                                                     .map(ModuleGalleryEntry::getName).collect(Collectors.toList());

    List<String> expectedEntries = Lists.newArrayList(
      "Phone & Tablet Module", "Android Library", "Dynamic Feature Module", "Wear OS Module",
      "Android TV Module", "Android Things Module", "Import Gradle Project", "Import Eclipse ADT Project",
      "Import .JAR/.AAR Package", "Java Library"
    );

    Assert.assertThat(sortedEntries, equalTo(expectedEntries));
  }

  public void testSortExistingModuleEntries_HideModulesFalse_InstantDynamicTrue() {
    StudioFlags.UAB_HIDE_INSTANT_MODULES_FOR_NON_FEATURE_PLUGIN_PROJECTS.override(false);
    StudioFlags.UAB_INSTANT_DYNAMIC_FEATURE_MODULE.override(true);
    ArrayList<ModuleGalleryEntry> moduleDescriptions = new ArrayList<>();
    moduleDescriptions.addAll(new ImportModuleGalleryEntryProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewAndroidModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewDynamicAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewInstantAppModuleDescriptionProvider().getDescriptions(getProject()));
    moduleDescriptions.addAll(new NewJavaModuleDescriptionProvider().getDescriptions(getProject()));

    List<String> sortedEntries = ChooseModuleTypeStep.sortModuleEntries(moduleDescriptions).stream()
                                                     .map(ModuleGalleryEntry::getName).collect(Collectors.toList());

    List<String> expectedEntries = Lists.newArrayList(
      "Phone & Tablet Module", "Android Library", "Dynamic Feature Module","Instant Dynamic Feature Module", "Instant App", "Instant App Feature Module", "Wear OS Module",
      "Android TV Module", "Android Things Module", "Import Gradle Project", "Import Eclipse ADT Project",
      "Import .JAR/.AAR Package", "Java Library"
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
}
