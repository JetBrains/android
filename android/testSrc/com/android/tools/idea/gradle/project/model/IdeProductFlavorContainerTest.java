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

import static com.android.testutils.Serialization.deserialize;
import static com.android.testutils.Serialization.serialize;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.gradle.model.impl.IdeProductFlavorContainerImpl;
import com.android.tools.idea.gradle.model.stubs.ProductFlavorContainerStub;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeProductFlavorContainerImpl}. */
public class IdeProductFlavorContainerTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void serializable() {
        assertThat(IdeProductFlavorContainerImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeProductFlavorContainerImpl container =
                myModelCache.productFlavorContainerFrom(new ProductFlavorContainerStub());
        byte[] bytes = serialize(container);
        Object o = deserialize(bytes);
        assertEquals(container, o);
    }
}
