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
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link VariantSelector}.
 */
public class VariantSelectorTest {
  private AndroidProject myAndroidProject;
  private VariantSelector myVariantSelector;

  @Before
  public void setUp() {
    myVariantSelector = new VariantSelector();
    myAndroidProject = mock(AndroidProject.class);
  }

  @Test
  public void findVariantToSelectWithDebugVariant() {
    Variant debugVariant = mock(Variant.class);
    Variant releaseVariant = mock(Variant.class);

    when(myAndroidProject.getVariants()).thenReturn(Lists.newArrayList(debugVariant, releaseVariant));
    when(debugVariant.getName()).thenReturn("debug");
    when(releaseVariant.getName()).thenReturn("release");

    Variant variant = myVariantSelector.findVariantToSelect(myAndroidProject);
    assertSame(debugVariant, variant);
  }

  @Test
  public void findVariantToSelectWithoutDebugVariant() {
    Variant aVariant = mock(Variant.class);
    Variant bVariant = mock(Variant.class);

    when(myAndroidProject.getVariants()).thenReturn(Lists.newArrayList(bVariant, aVariant));
    when(aVariant.getName()).thenReturn("a");
    when(bVariant.getName()).thenReturn("b");

    Variant variant = myVariantSelector.findVariantToSelect(myAndroidProject);
    assertSame(aVariant, variant);
  }

  @Test
  public void getVariantToSelectWithoutVariants() {
    when(myAndroidProject.getVariants()).thenReturn(Collections.emptyList());
    Variant variant = myVariantSelector.findVariantToSelect(myAndroidProject);
    assertNull(variant);
  }
}