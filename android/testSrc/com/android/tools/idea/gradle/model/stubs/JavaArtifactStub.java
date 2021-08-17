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
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.level2.DependencyGraphs;
import java.io.File;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public final class JavaArtifactStub extends BaseArtifactStub implements JavaArtifact {
    @Nullable private final File myMockablePlatformJar;

    public JavaArtifactStub() {
        this(new File("jar"));
    }

    public JavaArtifactStub(@Nullable File mockablePlatformJar) {
        super(AndroidProject.ARTIFACT_UNIT_TEST);
        myMockablePlatformJar = mockablePlatformJar;
    }

    public JavaArtifactStub(
            @NonNull String name,
            @NonNull String compileTaskName,
            @NonNull String assembleTaskName,
            @NonNull File classesFolder,
            @NonNull Set<File> classesFolders,
            @NonNull File javaResourcesFolder,
            @NonNull Dependencies dependencies,
            @NonNull Dependencies compileDependencies,
            @NonNull DependencyGraphs graphs,
            @NonNull Set<String> names,
            @NonNull Collection<File> folders,
            @Nullable SourceProvider variantSourceProvider,
            @Nullable SourceProvider multiFlavorSourceProvider,
            @Nullable File mockablePlatformJar) {
        super(
                name,
                compileTaskName,
                assembleTaskName,
                new File("none"),
                classesFolder,
                classesFolders,
                javaResourcesFolder,
                dependencies,
                compileDependencies,
                graphs,
                names,
                folders,
                variantSourceProvider,
                multiFlavorSourceProvider);
        myMockablePlatformJar = mockablePlatformJar;
    }

    @Override
    @Nullable
    public File getMockablePlatformJar() {
        return myMockablePlatformJar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JavaArtifact)) {
            return false;
        }
        JavaArtifact stub = (JavaArtifact) o;
        return Objects.equals(getName(), stub.getName())
                && Objects.equals(getCompileTaskName(), stub.getCompileTaskName())
                && Objects.equals(getAssembleTaskName(), stub.getAssembleTaskName())
                && Objects.equals(getClassesFolder(), stub.getClassesFolder())
                && Objects.equals(getJavaResourcesFolder(), stub.getJavaResourcesFolder())
                && Objects.equals(getDependencies(), stub.getDependencies())
                && Objects.equals(getCompileDependencies(), stub.getCompileDependencies())
                && Objects.equals(getDependencyGraphs(), stub.getDependencyGraphs())
                && Objects.equals(getIdeSetupTaskNames(), stub.getIdeSetupTaskNames())
                && Objects.equals(getGeneratedSourceFolders(), stub.getGeneratedSourceFolders())
                && Objects.equals(getVariantSourceProvider(), stub.getVariantSourceProvider())
                && Objects.equals(
                        getMultiFlavorSourceProvider(), stub.getMultiFlavorSourceProvider())
                && Objects.equals(getMockablePlatformJar(), stub.getMockablePlatformJar());
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
                getMockablePlatformJar());
    }

    @Override
    public String toString() {
        return "JavaArtifactStub{"
                + "myMockablePlatformJar="
                + myMockablePlatformJar
                + "} "
                + super.toString();
    }
}
