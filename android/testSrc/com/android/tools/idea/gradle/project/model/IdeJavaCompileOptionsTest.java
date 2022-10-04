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

import static com.android.tools.idea.gradle.project.model.IdeModelTestUtils.*;
import static com.google.common.truth.Truth.assertThat;

import com.android.builder.model.JavaCompileOptions;
import com.android.tools.idea.gradle.model.impl.IdeJavaCompileOptionsImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/** Tests for {@link IdeJavaCompileOptionsImpl}. */
public class IdeJavaCompileOptionsTest {

    @Test
    public void constructor() throws Throwable {
        JavaCompileOptions original = new JavaCompileOptions() {
            @NotNull
            @Override
            public String getEncoding() {
                return "encoding";
            }

            @NotNull
            @Override
            public String getSourceCompatibility() {
                return "sourceCompatibility";
            }

            @NotNull
            @Override
            public String getTargetCompatibility() {
                return "targetCompatibility";
            }

            @Override
            public boolean isCoreLibraryDesugaringEnabled() {
                return false;
            }
        };
        IdeJavaCompileOptionsImpl copy = new IdeJavaCompileOptionsImpl(
            "encoding", "sourceCompatibility", "targetCompatibility", false);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }
}
