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
import com.android.builder.model.JavaCompileOptions;
import java.util.Objects;

public final class JavaCompileOptionsStub extends BaseStub implements JavaCompileOptions {
    @NonNull private final String myEncoding;
    @NonNull private final String mySourceCompatibility;
    @NonNull private final String myTargetCompatibility;
    private final boolean myCoreLibraryDesugaringEnabled;

    public JavaCompileOptionsStub() {
        this("encoding", "sourceCompatibility", "targetCompatibility", false);
    }

    public JavaCompileOptionsStub(
            @NonNull String encoding,
            @NonNull String sourceCompatibility,
            @NonNull String targetCompatibility,
            boolean coreLibraryDesugaringEnabled) {
        myEncoding = encoding;
        mySourceCompatibility = sourceCompatibility;
        myTargetCompatibility = targetCompatibility;
        myCoreLibraryDesugaringEnabled = coreLibraryDesugaringEnabled;
    }

    @Override
    @NonNull
    public String getEncoding() {
        return myEncoding;
    }

    @Override
    @NonNull
    public String getSourceCompatibility() {
        return mySourceCompatibility;
    }

    @Override
    @NonNull
    public String getTargetCompatibility() {
        return myTargetCompatibility;
    }

    @Override
    public boolean isCoreLibraryDesugaringEnabled() {
        return myCoreLibraryDesugaringEnabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JavaCompileOptions)) {
            return false;
        }
        JavaCompileOptions compileOptions = (JavaCompileOptions) o;
        return Objects.equals(getEncoding(), compileOptions.getEncoding())
                && Objects.equals(getSourceCompatibility(), compileOptions.getSourceCompatibility())
                && Objects.equals(getTargetCompatibility(), compileOptions.getTargetCompatibility())
                && Objects.equals(
                        isCoreLibraryDesugaringEnabled(),
                        compileOptions.isCoreLibraryDesugaringEnabled());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getEncoding(),
                getSourceCompatibility(),
                getTargetCompatibility(),
                isCoreLibraryDesugaringEnabled());
    }

    @Override
    public String toString() {
        return "JavaCompileOptionsStub{"
                + "myEncoding='"
                + myEncoding
                + '\''
                + ", mySourceCompatibility='"
                + mySourceCompatibility
                + '\''
                + ", myTargetCompatibility='"
                + myTargetCompatibility
                + '\''
                + ", myCoreLibraryDesugaringEnabled='"
                + myCoreLibraryDesugaringEnabled
                + '\''
                + "}";
    }
}
