/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.model.stubs;

import com.android.annotations.NonNull;
import com.android.builder.model.BuildType;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.SourceProviderContainer;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Objects;

public final class BuildTypeContainerStub extends BaseStub implements BuildTypeContainer {
    @NonNull private final BuildType myBuildType;
    @NonNull private final SourceProvider mySourceProvider;
    @NonNull private final Collection<SourceProviderContainer> myExtraSourceProviders;

    public BuildTypeContainerStub() {
        this(
                new BuildTypeStub(),
                new SourceProviderStub(),
                Lists.newArrayList(new SourceProviderContainerStub()));
    }

    public BuildTypeContainerStub(
            @NonNull BuildType type,
            @NonNull SourceProvider sourceProvider,
            @NonNull Collection<SourceProviderContainer> extraSourceProviders) {
        myBuildType = type;
        mySourceProvider = sourceProvider;
        myExtraSourceProviders = extraSourceProviders;
    }

    @Override
    @NonNull
    public BuildType getBuildType() {
        return myBuildType;
    }

    @Override
    @NonNull
    public SourceProvider getSourceProvider() {
        return mySourceProvider;
    }

    @Override
    @NonNull
    public Collection<SourceProviderContainer> getExtraSourceProviders() {
        return myExtraSourceProviders;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BuildTypeContainer)) {
            return false;
        }
        BuildTypeContainer stub = (BuildTypeContainer) o;
        return Objects.equals(getBuildType(), stub.getBuildType())
                && Objects.equals(getSourceProvider(), stub.getSourceProvider())
                && Objects.equals(getExtraSourceProviders(), stub.getExtraSourceProviders());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getBuildType(), getSourceProvider(), getExtraSourceProviders());
    }

    @Override
    public String toString() {
        return "BuildTypeContainerStub{"
                + "myBuildType="
                + myBuildType
                + ", mySourceProvider="
                + mySourceProvider
                + ", myExtraSourceProviders="
                + myExtraSourceProviders
                + "}";
    }
}
