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

import com.android.builder.model.TestOptions;
import com.android.tools.idea.gradle.model.impl.IdeTestOptionsImpl;
import org.junit.Test;

public class IdeTestOptionsTest {

    @Test
    public void constructor() throws Throwable {
        TestOptions original = new TestOptions() {
            @Override
            public boolean getAnimationsDisabled() {
                return false;
            }

            @Override
            public Execution getExecution() {
                return null;
            }
        };
        IdeTestOptionsImpl copy = new IdeTestOptionsImpl(false,null);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }
}
