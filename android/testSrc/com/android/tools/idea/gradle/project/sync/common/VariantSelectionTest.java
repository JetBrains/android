/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.common;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.project.sync.idea.issues.AndroidSyncException;
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelConverter;
import com.android.tools.idea.gradle.project.sync.idea.svs.VariantGroup;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VariantSelectionTest {
  private AndroidProject myAndroidProject;

  @Before
  public void setUp() {
    myAndroidProject = mock(AndroidProject.class);
  }

  @Test
  public void findVariantFromVariantGroup() {
    Variant debugVariant = mock(Variant.class);
    Variant releaseVariant = mock(Variant.class);

    when(debugVariant.getName()).thenReturn("debug");
    when(releaseVariant.getName()).thenReturn("release");

    when(myAndroidProject.getVariants()).thenReturn(Collections.emptyList());
    VariantGroup group = new VariantGroup(ImmutableList.of(debugVariant, releaseVariant), ImmutableList.of());

    Variant variant = ModelConverter.findVariantToSelect(myAndroidProject, group);
    assertSame(debugVariant, variant);
  }

  @Test
  public void findVariantToSelectWithDebugVariant() {
    Variant debugVariant = mock(Variant.class);
    Variant releaseVariant = mock(Variant.class);

    when(myAndroidProject.getVariants()).thenReturn(Lists.newArrayList(debugVariant, releaseVariant));
    when(debugVariant.getName()).thenReturn("debug");
    when(releaseVariant.getName()).thenReturn("release");

    Variant variant = ModelConverter.findVariantToSelect(myAndroidProject, null);
    assertSame(debugVariant, variant);
  }

  @Test
  public void findVariantToSelectWithoutDebugVariant() {
    Variant aVariant = mock(Variant.class);
    Variant bVariant = mock(Variant.class);

    when(myAndroidProject.getVariants()).thenReturn(Lists.newArrayList(bVariant, aVariant));
    when(aVariant.getName()).thenReturn("a");
    when(bVariant.getName()).thenReturn("b");

    Variant variant = ModelConverter.findVariantToSelect(myAndroidProject, null);
    assertSame(aVariant, variant);
  }

  @Test
  public void getVariantToSelectWithoutVariants() {
    when(myAndroidProject.getVariants()).thenReturn(Collections.emptyList());
    when(myAndroidProject.getName()).thenReturn("project");
    try {
      ModelConverter.findVariantToSelect(myAndroidProject, null);
      fail();
    } catch (AndroidSyncException err) {
      // Expected
      assertThat(err.getMessage()).isEqualTo("No variants found for 'project'. Check build files to ensure at least one variant exists.");
    }
  }
}