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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.InstantRun;
import com.android.tools.idea.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.model.impl.IdeAndroidArtifactImpl;
import com.android.tools.idea.gradle.model.stubs.AndroidArtifactStub;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCache;
import com.android.tools.idea.gradle.project.sync.idea.svs.ModelCacheTesting;
import java.util.Objects;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

/** Tests for {@link IdeAndroidArtifactImpl}. */
public class IdeAndroidArtifactImplTest {
    private ModelCacheTesting myModelCache;
    private GradleVersion myGradleVersion;

    @Before
    public void setUp() throws Exception {
        myModelCache = ModelCache.createForTesting();
        myGradleVersion = GradleVersion.parse("3.2");
    }

    @Test
    public void model1_dot_5() {
        AndroidArtifactStub original =
                new AndroidArtifactStub(AndroidProject.ARTIFACT_MAIN) {
                    @Override
                    @NonNull
                    public InstantRun getInstantRun() {
                        throw new UnsupportedOperationException(
                                "Unsupported method: AndroidArtifact.getInstantRun()");
                    }

                    @Override
                    public int hashCode() {
                        return Objects.hash(
                                getName(),
                                getCompileTaskName(),
                                getAssembleTaskName(),
                                getClassesFolder(),
                                getJavaResourcesFolder(),
                                getDependencies(),
                                getCompileDependencies(),
                                getDependencyGraphs(),
                                getIdeSetupTaskNames(),
                                getGeneratedSourceFolders(),
                                getVariantSourceProvider(),
                                getMultiFlavorSourceProvider(),
                                getOutputs(),
                                getApplicationId(),
                                getSourceGenTaskName(),
                                getGeneratedResourceFolders(),
                                getSigningConfigName(),
                                getAbiFilters(),
                                getNativeLibraries(),
                                isSigned());
                    }
                };
        IdeAndroidArtifact artifact = myModelCache.androidArtifactFrom(original, myGradleVersion);
    }

    /**
     * Older AGPs may return null for AbiFilters, verify that IdeAndroidArtifactImpl normalizes that
     * to empty set.
     */
    @Test
    public void nullAbiFilterMappedToEmptySet() throws Throwable {
        AndroidArtifact original =
                new AndroidArtifactStub(AndroidProject.ARTIFACT_MAIN) {
                    @Nullable
                    @Override
                    public Set<String> getAbiFilters() {
                        return null;
                    }
                };
        IdeAndroidArtifactImpl copy = myModelCache.androidArtifactFrom(original, myGradleVersion);
        assertThat(original.getAbiFilters()).isNull();
        assertThat(copy.getAbiFilters()).isEmpty();
    }
}
