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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview;

import com.android.tools.idea.gradle.structure.configurables.android.dependencies.module.treeview.RootNode.ArtifactNameComparator;
import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static org.fest.assertions.Assertions.assertThat;

/**
 * Tests for {@link ArtifactNameComparator}.
 */
public class ArtifactNameComparatorTest {
  @Test
  public void testCompare() {
    List<String> names = Lists.newArrayList(ARTIFACT_UNIT_TEST, ARTIFACT_MAIN, ARTIFACT_ANDROID_TEST);
    Collections.sort(names, ArtifactNameComparator.INSTANCE);
    assertThat(names).containsSequence(ARTIFACT_MAIN, ARTIFACT_ANDROID_TEST, ARTIFACT_UNIT_TEST);
  }
}