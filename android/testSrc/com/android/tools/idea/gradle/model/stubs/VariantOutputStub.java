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
import com.android.build.OutputFile;
import com.android.build.VariantOutput;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class VariantOutputStub extends BaseStub implements VariantOutput {
    @NonNull private final OutputFile myMainOutputFile;
    @NonNull private final Collection<? extends OutputFile> myOutputs;
    @NonNull private final String myOutputType;
    @NonNull private final Collection<String> myFilterTypes;
    @NonNull private final Collection<FilterData> myFilters;
    private final int myVersionCode;

    public VariantOutputStub() {
        this(
                new OutputFileStub(),
                Collections.singletonList(new OutputFileStub()),
                "type",
                Arrays.asList("type1", "type2"),
                Collections.singletonList(new FilterDataStub()),
                1);
    }

    public VariantOutputStub(
            @NonNull OutputFile mainOutputFile,
            @NonNull Collection<? extends OutputFile> outputs,
            @NonNull String outputType,
            @NonNull Collection<String> filterTypes,
            @NonNull Collection<FilterData> filters,
            int versionCode) {
        myMainOutputFile = mainOutputFile;
        myOutputs = outputs;
        myOutputType = outputType;
        myFilterTypes = filterTypes;
        myFilters = filters;
        myVersionCode = versionCode;
    }

    @Override
    @NonNull
    public OutputFile getMainOutputFile() {
        return myMainOutputFile;
    }

    @Override
    @NonNull
    public Collection<? extends OutputFile> getOutputs() {
        return myOutputs;
    }

    @Override
    @NonNull
    public String getOutputType() {
        return myOutputType;
    }

    @Override
    @NonNull
    public Collection<String> getFilterTypes() {
        return myFilterTypes;
    }

    @Override
    @NonNull
    public Collection<FilterData> getFilters() {
        return myFilters;
    }

    @Override
    public int getVersionCode() {
        return myVersionCode;
    }

    @Override
    public String toString() {
        return "VariantOutputStub{"
                + "myMainOutputFile="
                + myMainOutputFile
                + ", myOutputs="
                + myOutputs
                + ", myOutputType='"
                + myOutputType
                + '\''
                + ", myFilterTypes="
                + myFilterTypes
                + ", myFilters="
                + myFilters
                + ", myVersionCode="
                + myVersionCode
                + "}";
    }
}
