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
import com.android.build.FilterData;
import java.util.Objects;

public final class FilterDataStub extends BaseStub implements FilterData {
    @NonNull private final String myIdentifier;
    @NonNull private final String myFilterType;

    public FilterDataStub() {
        this("identifier", "filterType");
    }

    public FilterDataStub(@NonNull String identifier, @NonNull String filterType) {
        myIdentifier = identifier;
        myFilterType = filterType;
    }

    @Override
    @NonNull
    public String getIdentifier() {
        return myIdentifier;
    }

    @Override
    @NonNull
    public String getFilterType() {
        return myFilterType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FilterData)) {
            return false;
        }
        FilterData filterData = (FilterData) o;
        return Objects.equals(getIdentifier(), filterData.getIdentifier())
                && Objects.equals(getFilterType(), filterData.getFilterType());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getIdentifier(), getFilterType());
    }

    @Override
    public String toString() {
        return "FilterDataStub{"
                + "myIdentifier='"
                + myIdentifier
                + '\''
                + ", myFilterType='"
                + myFilterType
                + '\''
                + "}";
    }
}
