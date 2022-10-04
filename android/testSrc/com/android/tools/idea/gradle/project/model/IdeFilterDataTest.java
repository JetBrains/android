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

import static com.android.tools.idea.gradle.project.model.IdeModelTestUtils.assertEqualsOrSimilar;
import static com.android.tools.idea.gradle.project.model.IdeModelTestUtils.verifyUsageOfImmutableCollections;

import com.android.build.FilterData;
import com.android.tools.idea.gradle.model.impl.IdeFilterDataImpl;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/** Tests for {@link IdeFilterDataImpl}. */
public class IdeFilterDataTest {

    @Test
    public void constructor() throws Throwable {
        FilterData original = new FilterData() {
          @NotNull
          @Override
          public String getIdentifier() {
            return "identifier";
          }

          @NotNull
          @Override
          public String getFilterType() {
            return "filterType";
          }
        };
        IdeFilterDataImpl copy = new IdeFilterDataImpl("identifier", "filterType");
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }
}
