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

import com.android.builder.model.ApiVersion;
import com.android.tools.idea.gradle.model.impl.IdeApiVersionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

/** Tests for {@link IdeApiVersionImpl}. */
public class IdeApiVersionTest {

    @Test
    public void constructor() throws Throwable {
        ApiVersion original = new ApiVersion() {
          @Override
          public int getApiLevel() {
            return 1;
          }

          @Nullable
          @Override
          public String getCodename() {
            return "codename";
          }

          @NotNull
          @Override
          public String getApiString() {
            return "apiString";
          }
        };
        IdeApiVersionImpl copy = new IdeApiVersionImpl(1, "codename", "apiString");
        IdeModelTestUtils.assertEqualsOrSimilar(original, copy);
        IdeModelTestUtils.verifyUsageOfImmutableCollections(copy);
    }
}
