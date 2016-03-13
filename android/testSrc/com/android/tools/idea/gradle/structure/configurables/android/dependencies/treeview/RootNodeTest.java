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

import com.android.tools.idea.gradle.structure.model.android.PsAndroidDependency;
import com.android.tools.idea.gradle.structure.model.android.PsDependencyContainer;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.android.builder.model.AndroidProject.ARTIFACT_MAIN;
import static com.android.builder.model.AndroidProject.ARTIFACT_UNIT_TEST;
import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RootNode}.
 */
public class RootNodeTest {
  private PsAndroidDependency myD1;
  private PsAndroidDependency myD2;
  private PsAndroidDependency myD3;

  @Before
  public void setUp() {
    myD1 = mock(PsAndroidDependency.class);
    when(myD1.toString()).thenReturn("d1");

    myD2 = mock(PsAndroidDependency.class);
    when(myD2.toString()).thenReturn("d2");

    myD3 = mock(PsAndroidDependency.class);
    when(myD3.toString()).thenReturn("d3");
  }

  @Test
  public void testGroupingByArtifacts1() {
    // v1 main
    //    d1
    //    d2
    // v2 main
    //    d1
    //    d2
    // v3 main
    //    d1

    when(myD1.getContainers()).thenReturn(
      Sets.newHashSet(container("v1", ARTIFACT_MAIN),
                      container("v2", ARTIFACT_MAIN),
                      container("v3", ARTIFACT_MAIN)
      ));
    when(myD2.getContainers()).thenReturn(
      Sets.newHashSet(container("v1", ARTIFACT_MAIN),
                      container("v2", ARTIFACT_MAIN)
      ));

    List<PsAndroidDependency> allDependencies = Lists.newArrayList(myD1, myD2);
    Map<List<PsDependencyContainer>, List<PsAndroidDependency>> groups = RootNode.group(allDependencies);
    assertThat(groups).hasSize(2);

    List<PsDependencyContainer> group = Lists.newArrayList(container("v3", ARTIFACT_MAIN));
    List<PsAndroidDependency> dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD1);

    group = Lists.newArrayList(container("v1", ARTIFACT_MAIN),
                               container("v2", ARTIFACT_MAIN));
    dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD1, myD2);
  }

  @Test
  public void testGroupingByArtifacts2() {
    // v1 main
    //    d2
    //    d3
    // v1 unit test
    //    d1
    // v2 main
    //    d2
    // v2 unit test
    //    d1

    when(myD1.getContainers()).thenReturn(
      Sets.newHashSet(container("v1", ARTIFACT_UNIT_TEST),
                      container("v2", ARTIFACT_UNIT_TEST)
      ));
    when(myD2.getContainers()).thenReturn(
      Sets.newHashSet(container("v1", ARTIFACT_MAIN),
                      container("v2", ARTIFACT_MAIN)
      ));
    when(myD3.getContainers()).thenReturn(
      Sets.newHashSet(container("v1", ARTIFACT_MAIN)
      ));

    List<PsAndroidDependency> allDependencies = Lists.newArrayList(myD1, myD2, myD3);
    Map<List<PsDependencyContainer>, List<PsAndroidDependency>> groups = RootNode.group(allDependencies);
    assertThat(groups).hasSize(4);

    List<PsDependencyContainer> group = Lists.newArrayList(container("v1", ARTIFACT_MAIN));
    List<PsAndroidDependency> dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD2, myD3);

    group = Lists.newArrayList(container("v1", ARTIFACT_UNIT_TEST));
    dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD1);

    group = Lists.newArrayList(container("v2", ARTIFACT_MAIN));
    dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD2);

    group = Lists.newArrayList(container("v2", ARTIFACT_UNIT_TEST));
    dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD1);
  }

  @Test
  public void testGroupingByArtifacts3() {
    // v1 main
    //    d2
    // v1 unit test
    //    d1
    // v2 main
    //    d2
    // v2 unit test
    //    d1

    when(myD1.getContainers()).thenReturn(
      Sets.newHashSet(container("v1", ARTIFACT_UNIT_TEST),
                      container("v2", ARTIFACT_UNIT_TEST)
      ));
    when(myD2.getContainers()).thenReturn(
      Sets.newHashSet(container("v1", ARTIFACT_MAIN),
                      container("v2", ARTIFACT_MAIN)
      ));

    List<PsAndroidDependency> allDependencies = Lists.newArrayList(myD1, myD2);
    Map<List<PsDependencyContainer>, List<PsAndroidDependency>> groups = RootNode.group(allDependencies);
    assertThat(groups).hasSize(2);

    List<PsDependencyContainer> group = Lists.newArrayList(container("v1", ARTIFACT_MAIN),
                                                           container("v2", ARTIFACT_MAIN));
    List<PsAndroidDependency> dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD2);

    group = Lists.newArrayList(container("v1", ARTIFACT_UNIT_TEST),
                               container("v2", ARTIFACT_UNIT_TEST));
    dependencies = groups.get(group);
    assertThat(dependencies).containsOnly(myD1);
  }

  @NotNull
  private static PsDependencyContainer container(@NotNull String variant, @NotNull String artifact) {
    return new PsDependencyContainer(variant, artifact);
  }
}