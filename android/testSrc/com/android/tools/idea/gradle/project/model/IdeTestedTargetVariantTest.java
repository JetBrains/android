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
import static org.junit.Assert.assertEquals;

import com.android.builder.model.TestedTargetVariant;
import com.android.tools.idea.gradle.model.impl.IdeTestedTargetVariantImpl;
import com.android.tools.idea.gradle.model.stubs.TestedTargetVariantStub;
import com.android.testutils.Serialization;
import com.android.tools.idea.gradle.project.sync.ModelCache;
import com.android.tools.idea.gradle.project.sync.ModelCacheTesting;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeTestedTargetVariantImpl}. */
public class IdeTestedTargetVariantTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void serializable() {
        assertThat(IdeTestedTargetVariantImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeTestedTargetVariantImpl targetVariant =
                myModelCache.testedTargetVariantFrom(new TestedTargetVariantStub());
        byte[] bytes = Serialization.serialize(targetVariant);
        Object o = Serialization.deserialize(bytes);
        assertEquals(targetVariant, o);
    }

    @Test
    public void constructor() throws Throwable {
        TestedTargetVariant original = new TestedTargetVariantStub();
        IdeTestedTargetVariantImpl copy = myModelCache.testedTargetVariantFrom(original);
        assertEqualsOrSimilar(original, copy);
        verifyUsageOfImmutableCollections(copy);
    }
}
