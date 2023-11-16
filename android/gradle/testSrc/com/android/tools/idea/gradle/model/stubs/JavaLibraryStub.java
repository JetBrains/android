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
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JavaLibraryStub extends LibraryStub implements JavaLibrary {
    @NonNull private final File myJarFile;
    @NonNull private final List<JavaLibrary> myDependencies;

    public JavaLibraryStub() {
        this(new File("jarFile"), new ArrayList<>());
    }

    public JavaLibraryStub(@NonNull File jarFile, @NonNull List<JavaLibrary> dependencies) {
        myJarFile = jarFile;
        myDependencies = dependencies;
    }

    public JavaLibraryStub(
            @NonNull MavenCoordinates coordinates,
            @Nullable String buildId,
            @Nullable String project,
            @Nullable String name,
            boolean provided,
            boolean isSkipped,
            @NonNull File jarFile,
            @NonNull List<JavaLibrary> dependencies) {
        super(coordinates, buildId, project, name, provided, isSkipped);
        myJarFile = jarFile;
        myDependencies = dependencies;
    }

    @Override
    @NonNull
    public File getJarFile() {
        return myJarFile;
    }

    @Override
    @NonNull
    public List<? extends JavaLibrary> getDependencies() {
        return myDependencies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JavaLibrary)) {
            return false;
        }
        JavaLibrary library = (JavaLibrary) o;
        return isProvided() == library.isProvided()
                && Objects.equals(getResolvedCoordinates(), library.getResolvedCoordinates())
                && Objects.equals(getProject(), library.getProject())
                && Objects.equals(getName(), library.getName())
                && Objects.equals(getJarFile(), library.getJarFile())
                && Objects.equals(getDependencies(), library.getDependencies());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getResolvedCoordinates(),
                getProject(),
                getName(),
                isProvided(),
                getJarFile(),
                getDependencies());
    }

    @Override
    public String toString() {
        return "JavaLibraryStub{"
                + "myJarFile="
                + myJarFile
                + ", myDependencies="
                + myDependencies
                + "} "
                + super.toString();
    }
}
