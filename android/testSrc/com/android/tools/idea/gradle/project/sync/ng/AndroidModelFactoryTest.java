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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleModuleModels;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.intellij.testFramework.JavaProjectTestCase;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link AndroidModelFactory}.
 */
public class AndroidModelFactoryTest extends JavaProjectTestCase {
  @Mock private VariantSelector myVariantSelector;
  @Mock private IdeDependenciesFactory myDependenciesFactory;
  @Mock private GradleModuleModels myModuleModels;

  private AndroidModelFactory myAndroidModelFactory;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);

    myAndroidModelFactory = new AndroidModelFactory(myVariantSelector, myDependenciesFactory);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  public void testCreateAndroidModelInSingleVariantSync() {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    AndroidProjectStub androidProject = new AndroidProjectStub("test");
    VariantStub variant = androidProject.addVariant("debug");
    androidProject.setVariantNames("debug", "release");
    androidProject.clearVariants();

    when(myModuleModels.findModels(Variant.class)).thenReturn(singletonList(variant));

    AndroidModuleModel androidModel = myAndroidModelFactory.createAndroidModel(getModule(), androidProject, myModuleModels);
    assertNotNull(androidModel);

    IdeAndroidProject androidProjectCopy = androidModel.getAndroidProject();
    List<Variant> variants = new ArrayList<>(androidProjectCopy.getVariants());
    assertThat(variants).hasSize(1);

    Variant variantCopy = variants.get(0);
    assertEquals("debug",variantCopy.getName());
    assertSame(variantCopy, androidModel.getSelectedVariant());
    assertThat(androidProjectCopy.getVariantNames()).containsExactly("debug", "release");

    verify(myVariantSelector, never()).findVariantToSelect(androidProject);
  }

  public void testCreateAndroidModel() {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(false);

    AndroidProjectStub androidProject = new AndroidProjectStub("test");
    VariantStub variant = androidProject.addVariant("debug");

    when(myVariantSelector.findVariantToSelect(androidProject)).thenReturn(variant);
    AndroidModuleModel androidModel = myAndroidModelFactory.createAndroidModel(getModule(), androidProject, myModuleModels);
    assertNotNull(androidModel);

    IdeAndroidProject androidProjectCopy = androidModel.getAndroidProject();
    List<Variant> variants = new ArrayList<>(androidProjectCopy.getVariants());
    assertThat(variants).hasSize(1);

    Variant variantCopy = variants.get(0);
    assertEquals("debug",variantCopy.getName());
    assertSame(variantCopy, androidModel.getSelectedVariant());
  }

  public void testCreateAndroidModelInSingleVariantSyncWithMultipleVariants() {
    StudioFlags.SINGLE_VARIANT_SYNC_ENABLED.override(true);

    AndroidProjectStub androidProject = new AndroidProjectStub("test");
    VariantStub debugVariant = androidProject.addVariant("debug");
    VariantStub releaseVariant = androidProject.addVariant("release");

    androidProject.setVariantNames("debug", "release");
    androidProject.clearVariants();

    when(myModuleModels.findModels(Variant.class)).thenReturn(asList(debugVariant, releaseVariant));

    AndroidModuleModel androidModel = myAndroidModelFactory.createAndroidModel(getModule(), androidProject, myModuleModels);
    assertNotNull(androidModel);

    IdeAndroidProject androidProjectCopy = androidModel.getAndroidProject();
    List<Variant> variants = new ArrayList<>(androidProjectCopy.getVariants());
    assertThat(variants).hasSize(2);

    assertThat(variants.stream().map(Variant::getName).collect(toList())).containsExactly("debug", "release");
    assertThat(androidProjectCopy.getVariantNames()).containsExactly("debug", "release");
    assertSame(variants.get(1), androidModel.getSelectedVariant());
    verify(myVariantSelector, never()).findVariantToSelect(androidProject);
  }
}