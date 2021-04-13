/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.model;

import static com.android.tools.idea.Projects.getBaseDirPath;
import static com.android.tools.idea.gradle.stubs.android.AndroidProjectStub.toIdeAndroidProject;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.setupTestProjectFromAndroidModel;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.gradle.model.impl.IdeAndroidProjectImpl;
import com.android.tools.idea.gradle.project.sync.ModelCacheKt;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

/** Tests for {@link IdeAndroidProjectImpl}. */
public class IdeAndroidProjectImplTest extends AndroidGradleTestCase {

    @Test
    public void testDefaultVariantHeuristicTest_allVariantsRemoved() {
        assertThat(ModelCacheKt.getDefaultVariant(ImmutableList.of())).isNull();
    }

    @Test
    public void testDefaultVariantHeuristicTest_picksDebug() {
      assertThat(ModelCacheKt.getDefaultVariant(ImmutableList.of("a", "z", "debug", "release")))
                .isEqualTo("debug");
    }

    @Test
    public void testDefaultVariantHeuristicTest_picksDebugWithFlavors() {
      assertThat(ModelCacheKt.getDefaultVariant(ImmutableList.of("aRelease", "bRelease", "bDebug", "cDebug")))
                .isEqualTo("bDebug");
    }

    @Test
    public void testDefaultVariantHeuristicTest_alphabeticalFallback() {
        assertThat(ModelCacheKt.getDefaultVariant(ImmutableList.of("a", "b"))).isEqualTo("a");
    }
}
