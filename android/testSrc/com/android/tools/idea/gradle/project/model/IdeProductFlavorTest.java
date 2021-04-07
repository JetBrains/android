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

import com.android.annotations.NonNull;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.VectorDrawablesOptions;
import com.android.tools.idea.gradle.model.impl.IdeProductFlavorImpl;
import com.android.tools.idea.gradle.model.stubs.ProductFlavorStub;
import com.android.testutils.Serialization;
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCache;
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCacheTesting;
import java.io.Serializable;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeProductFlavorImpl}. */
public class IdeProductFlavorTest {
    private ModelCacheTesting myModelCache;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
    }

    @Test
    public void serializable() {
        assertThat(IdeProductFlavorImpl.class).isAssignableTo(Serializable.class);
    }

    @Test
    public void serialization() throws Exception {
        IdeProductFlavorImpl buildType = myModelCache.productFlavorFrom(new ProductFlavorStub());
        byte[] bytes = Serialization.serialize(buildType);
        Object o = Serialization.deserialize(bytes);
        assertEquals(buildType, o);
    }

    @Test
    public void model1_dot_5() {
        ProductFlavor original =
                new ProductFlavorStub() {
                    @Override
                    @NonNull
                    public VectorDrawablesOptions getVectorDrawables() {
                        throw new UnsupportedOperationException("getVectorDrawables");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getResValues(),
                                getProguardFiles(),
                                getConsumerProguardFiles(),
                                getManifestPlaceholders(),
                                getApplicationIdSuffix(),
                                getVersionNameSuffix(),
                                getTestInstrumentationRunnerArguments(),
                                getResourceConfigurations(),
                                getDimension(),
                                getApplicationId(),
                                getVersionCode(),
                                getVersionName(),
                                getMinSdkVersion(),
                                getTargetSdkVersion(),
                                getMaxSdkVersion(),
                                getRenderscriptTargetApi(),
                                getRenderscriptSupportModeEnabled(),
                                getRenderscriptSupportModeBlasEnabled(),
                                getRenderscriptNdkModeEnabled(),
                                getTestApplicationId(),
                                getTestInstrumentationRunner(),
                                getTestHandleProfiling(),
                                getTestFunctionalTest(),
                                getSigningConfig(),
                                getWearAppUnbundled());
                    }
                };
        IdeProductFlavorImpl copy = myModelCache.productFlavorFrom(original);
        assertThat(copy.getVectorDrawables()).isNull();
    }
}
