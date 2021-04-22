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
import com.android.builder.model.MavenCoordinates;
import com.android.tools.idea.gradle.model.UnusedModelMethodException;
import java.util.Objects;

public class MavenCoordinatesStub extends BaseStub implements MavenCoordinates {
    @NonNull private final String myGroupId;
    @NonNull private final String myArtifactId;
    @NonNull private final String myVersion;
    @NonNull private final String myPackaging;

    public MavenCoordinatesStub() {
        this("com.android.tools", "test", "2.1", "aar");
    }

    public MavenCoordinatesStub(
            @NonNull String groupId,
            @NonNull String artifactId,
            @NonNull String version,
            @NonNull String packaging) {
        myGroupId = groupId;
        myArtifactId = artifactId;
        myVersion = version;
        myPackaging = packaging;
    }

    @Override
    @NonNull
    public String getGroupId() {
        return myGroupId;
    }

    @Override
    @NonNull
    public String getArtifactId() {
        return myArtifactId;
    }

    @Override
    @NonNull
    public String getVersion() {
        return myVersion;
    }

    @Override
    @NonNull
    public String getPackaging() {
        return myPackaging;
    }

    @Override
    @Nullable
    public String getClassifier() {
        return null;
    }

    @Override
    @NonNull
    public String getVersionlessId() {
        throw new UnusedModelMethodException("getVersionlessId");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MavenCoordinates)) {
            return false;
        }
        MavenCoordinates coordinates = (MavenCoordinates) o;
        return Objects.equals(getGroupId(), coordinates.getGroupId())
                && Objects.equals(getArtifactId(), coordinates.getArtifactId())
                && Objects.equals(getVersion(), coordinates.getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getGroupId(), getArtifactId(), getVersion());
    }

    @Override
    public String toString() {
        return "MavenCoordinatesStub{"
                + "myGroupId='"
                + myGroupId
                + '\''
                + ", myArtifactId='"
                + myArtifactId
                + '\''
                + ", myVersion='"
                + myVersion
                + '\''
                + "}";
    }
}
