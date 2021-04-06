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
import com.android.annotations.Nullable;
import com.android.builder.model.level2.GraphItem;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;

public final class GraphItemStub extends BaseStub implements GraphItem {
    @NonNull private final String myArtifactAddress;
    @NonNull private final List<GraphItem> myDependencies;
    @Nullable private final String myRequestedCoordinates;

    public GraphItemStub(@NonNull GraphItem... dependencies) {
        this("address", Lists.newArrayList(dependencies), "coordinates");
    }

    public GraphItemStub(
            @NonNull String artifactAddress,
            @NonNull List<GraphItem> dependencies,
            @Nullable String requestedCoordinates) {
        myArtifactAddress = artifactAddress;
        myDependencies = dependencies;
        myRequestedCoordinates = requestedCoordinates;
    }

    @Override
    @NonNull
    public String getArtifactAddress() {
        return myArtifactAddress;
    }

    @Override
    @NonNull
    public List<GraphItem> getDependencies() {
        return myDependencies;
    }

    @Override
    @Nullable
    public String getRequestedCoordinates() {
        return myRequestedCoordinates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GraphItem)) {
            return false;
        }
        GraphItem graphItem = (GraphItem) o;
        return Objects.equals(getArtifactAddress(), graphItem.getArtifactAddress())
                && Objects.equals(getDependencies(), graphItem.getDependencies())
                && Objects.equals(getRequestedCoordinates(), graphItem.getRequestedCoordinates());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getArtifactAddress(), getDependencies(), getRequestedCoordinates());
    }

    @Override
    public String toString() {
        return "GraphItemStub{"
                + "myArtifactAddress='"
                + myArtifactAddress
                + '\''
                + ", myDependencies="
                + myDependencies
                + ", myRequestedCoordinates='"
                + myRequestedCoordinates
                + '\''
                + "}";
    }
}
