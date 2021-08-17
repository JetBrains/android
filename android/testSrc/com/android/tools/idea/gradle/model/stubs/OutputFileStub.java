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
import com.android.build.FilterData;
import com.android.build.OutputFile;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

public final class OutputFileStub extends BaseStub implements OutputFile {
    @NonNull private final String myOutputType;
    @NonNull private final Collection<String> myFilterTypes;
    @NonNull private final Collection<FilterData> myFilters;
    @NonNull private final File myOutputFile;
    @NonNull private final Collection<OutputFile> myOutputs;
    private final int myVersionCode;

    public OutputFileStub() {
        this(Collections.emptyList());
    }

    public OutputFileStub(@NonNull Collection<OutputFile> outputs) {
        this(
                "type",
                Lists.newArrayList("filterType1"),
                Lists.newArrayList(new FilterDataStub()),
                new File("output"),
                outputs,
                1);
    }

    public OutputFileStub(
            @NonNull String type,
            @NonNull Collection<String> filterTypes,
            @NonNull Collection<FilterData> filters,
            @NonNull File outputFile,
            @NonNull Collection<OutputFile> outputs,
            int versionCode) {
        myOutputType = type;
        myFilterTypes = filterTypes;
        myFilters = filters;
        myOutputFile = outputFile;
        myOutputs = new ArrayList<>(outputs);
        myVersionCode = versionCode;
    }

    public void addOutputFile(@NonNull OutputFile file) {
        myOutputs.add(file);
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
    @NonNull
    public File getOutputFile() {
        return myOutputFile;
    }

    @Override
    @NonNull
    public OutputFile getMainOutputFile() {
        return this;
    }

    @Override
    @NonNull
    public Collection<? extends OutputFile> getOutputs() {
        return myOutputs;
    }

    @Override
    public int getVersionCode() {
        return myVersionCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutputFile)) {
            return false;
        }
        OutputFile outputFile = (OutputFile) o;
        return getVersionCode() == outputFile.getVersionCode()
                && Objects.equals(getOutputType(), outputFile.getOutputType())
                && Objects.equals(getFilterTypes(), outputFile.getFilterTypes())
                && Objects.equals(getFilters(), outputFile.getFilters())
                && Objects.equals(getOutputFile(), outputFile.getOutputFile())
                && Objects.equals(getOutputs(), outputFile.getOutputs());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getOutputType(),
                getFilterTypes(),
                getFilters(),
                getOutputFile(),
                hashCode(getOutputs()),
                getVersionCode());
    }

    private int hashCode(@NonNull Collection<? extends OutputFile> outputFiles) {
        int hashCode = 1;
        for (OutputFile outputFile : outputFiles) {
            hashCode = 31 * hashCode + hashCode(outputFile);
        }
        return hashCode;
    }

    private int hashCode(@Nullable OutputFile outputFile) {
        return outputFile != this ? Objects.hashCode(outputFile) : 1;
    }

    @Override
    public String toString() {
        return "OutputFileStub{"
                + "myOutputType='"
                + myOutputType
                + '\''
                + ", myFilterTypes="
                + myFilterTypes
                + ", myFilters="
                + myFilters
                + ", myOutputFile="
                + myOutputFile
                + ", myOutputs="
                + toString(myOutputs)
                + ", myVersionCode="
                + myVersionCode
                + "}";
    }

    @NonNull
    private String toString(@NonNull Collection<? extends OutputFile> outputFiles) {
        int max = outputFiles.size() - 1;
        if (max == -1) {
            return "[]";
        }

        StringBuilder b = new StringBuilder();
        b.append('[');
        int i = 0;
        for (OutputFile file : outputFiles) {
            b.append(toString(file));
            if (i++ == max) {
                b.append(']');
                break;
            }
            b.append(", ");
        }
        return b.toString();
    }

    @NonNull
    private String toString(@Nullable OutputFile outputFile) {
        if (outputFile == this) {
            return "this";
        }
        return Objects.toString(outputFile);
    }
}
