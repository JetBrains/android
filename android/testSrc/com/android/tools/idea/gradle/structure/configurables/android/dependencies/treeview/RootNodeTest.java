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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.android.tools.idea.gradle.structure.model.android.PsdAndroidDependencyModel;
import com.google.common.collect.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RootNode}.
 */
public class RootNodeTest {
  private PsdAndroidDependencyModel myD1;
  private PsdAndroidDependencyModel myD2;
  private PsdAndroidDependencyModel myD3;
  private PsdAndroidDependencyModel myD4;
  private PsdAndroidDependencyModel myD5;
  private PsdAndroidDependencyModel myD6;
  private List<PsdAndroidDependencyModel> myDependencies;

  @Before
  public void setUp() {
    myD1 = mock(PsdAndroidDependencyModel.class);
    myD2 = mock(PsdAndroidDependencyModel.class);
    myD3 = mock(PsdAndroidDependencyModel.class);
    myD4 = mock(PsdAndroidDependencyModel.class);
    myD5 = mock(PsdAndroidDependencyModel.class);
    myD6 = mock(PsdAndroidDependencyModel.class);

    myDependencies = Lists.newArrayList(myD1, myD2, myD3, myD4, myD5, myD6);
  }

  @Test
  public void testGroupingByVariants() {
    when(myD1.getVariants()).thenReturn(Lists.newArrayList("v1", "v2", "v6"));
    when(myD1.toString()).thenReturn("d1");

    when(myD2.getVariants()).thenReturn(Lists.newArrayList("v3"));
    when(myD2.toString()).thenReturn("d2");

    when(myD3.getVariants()).thenReturn(Lists.newArrayList("v1", "v2"));
    when(myD3.toString()).thenReturn("d3");

    when(myD4.getVariants()).thenReturn(Lists.newArrayList("v3"));
    when(myD4.toString()).thenReturn("d4");

    when(myD5.getVariants()).thenReturn(Lists.newArrayList("v4"));
    when(myD5.toString()).thenReturn("d5");

    when(myD6.getVariants()).thenReturn(Lists.newArrayList("v3"));
    when(myD6.toString()).thenReturn("d6");

    Map<List<String>, List<PsdAndroidDependencyModel>> groups = RootNode.groupVariants(myDependencies);

    List<String> group = Lists.newArrayList("v3");
    List<PsdAndroidDependencyModel> dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD2, myD4, myD6);

    group = Lists.newArrayList("v1", "v2");
    dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD1, myD3);

    group = Lists.newArrayList("v4");
    dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD5);
  }
}