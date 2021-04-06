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
import com.android.builder.model.ApiVersion;
import java.util.Objects;

public final class ApiVersionStub extends BaseStub implements ApiVersion {
    @NonNull private final String myApiString;
    @Nullable private final String myCodename;
    private final int myApiLevel;

    public ApiVersionStub() {
        this("apiString", "codeName", 1);
    }

    public ApiVersionStub(@NonNull String apiString, @Nullable String codename, int level) {
        myApiString = apiString;
        myCodename = codename;
        myApiLevel = level;
    }

    @Override
    @NonNull
    public String getApiString() {
        return myApiString;
    }

    @Override
    @Nullable
    public String getCodename() {
        return myCodename;
    }

    @Override
    public int getApiLevel() {
        return myApiLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ApiVersion)) {
            return false;
        }
        ApiVersion apiVersion = (ApiVersion) o;
        return getApiLevel() == apiVersion.getApiLevel()
                && Objects.equals(getApiString(), apiVersion.getApiString())
                && Objects.equals(getCodename(), apiVersion.getCodename());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getApiString(), getCodename(), getApiLevel());
    }

    @Override
    public String toString() {
        return "ApiVersionStub{"
                + "myApiString='"
                + myApiString
                + '\''
                + ", myCodename='"
                + myCodename
                + '\''
                + ", myApiLevel="
                + myApiLevel
                + "}";
    }
}
