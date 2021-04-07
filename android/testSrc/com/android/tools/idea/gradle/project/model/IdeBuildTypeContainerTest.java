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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.gradle.model.impl.IdeBuildTypeContainerImpl;
import com.android.tools.idea.gradle.model.stubs.BuildTypeContainerStub;
import com.android.testutils.Serialization;
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCache;
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCacheTesting;
import java.io.Serializable;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeBuildTypeContainerImpl}. */
public class IdeBuildTypeContainerTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void serializable() {
        assertThat(IdeBuildTypeContainerImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeBuildTypeContainerImpl buildType =
                myModelCache.buildTypeContainerFrom(new BuildTypeContainerStub());
        byte[] bytes = Serialization.serialize(buildType);
        Object o = Serialization.deserialize(bytes);
        assertEquals(buildType, o);
    }
}
