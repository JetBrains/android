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
import com.android.builder.model.BaseArtifact;
import com.android.builder.model.Dependencies;
import com.android.builder.model.SourceProvider;
import com.android.builder.model.level2.DependencyGraphs;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class BaseArtifactStub extends BaseStub implements BaseArtifact {
    @NonNull private final String myName;
    @NonNull private final String myCompileTaskName;
    @NonNull private final String myAssembleTaskName;
    @NonNull private final File myAssembleTaskOutputListingFile;
    @NonNull private final File myClassesFolder;
    @NonNull private final File myJavaResourcesFolder;
    @NonNull private final Dependencies myDependencies;
    @NonNull private final Dependencies myCompileDependencies;
    @NonNull private final DependencyGraphs myDependencyGraphs;
    @NonNull private final Set<String> myIdeSetupTaskNames;
    @NonNull private final Collection<File> myGeneratedSourceFolders;
    @Nullable private final SourceProvider myVariantSourceProvider;
    @Nullable private final SourceProvider myMultiFlavorSourceProvider;
    @NonNull private final Set<File> myAdditionalClassesFolders;

    public BaseArtifactStub(@NonNull String name) {
        this(
                name,
                "compile",
                "assemble",
                new File("postAssembleModel"),
                new File("classes"),
                new HashSet<>(),
                new File("javaResources"),
                new DependenciesStub(),
                new DependenciesStub(),
                new DependencyGraphsStub(),
                Sets.newHashSet("setup"),
                Lists.newArrayList(new File("generated")),
                new SourceProviderStub(),
                new SourceProviderStub());
    }

    public BaseArtifactStub(
            @NonNull String name,
            @NonNull String compileTaskName,
            @NonNull String assembleTaskName,
            @NonNull File assembleTaskOutputListingFile,
            @NonNull File classesFolder,
            @NonNull Set<File> classesFolders,
            @NonNull File javaResourcesFolder,
            @NonNull Dependencies dependencies,
            @NonNull Dependencies compileDependencies,
            @NonNull DependencyGraphs graphs,
            @NonNull Set<String> ideSetupTaskNames,
            @NonNull Collection<File> folders,
            @Nullable SourceProvider variantSourceProvider,
            @Nullable SourceProvider multiFlavorSourceProvider) {
        myName = name;
        myCompileTaskName = compileTaskName;
        myAssembleTaskName = assembleTaskName;
        myAssembleTaskOutputListingFile = assembleTaskOutputListingFile;
        myClassesFolder = classesFolder;
        myAdditionalClassesFolders = classesFolders;
        myJavaResourcesFolder = javaResourcesFolder;
        myDependencies = dependencies;
        myCompileDependencies = compileDependencies;
        myDependencyGraphs = graphs;
        myIdeSetupTaskNames = ideSetupTaskNames;
        myGeneratedSourceFolders = folders;
        myVariantSourceProvider = variantSourceProvider;
        myMultiFlavorSourceProvider = multiFlavorSourceProvider;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getCompileTaskName() {
        return myCompileTaskName;
    }

    @Override
    @NonNull
    public String getAssembleTaskName() {
        return myAssembleTaskName;
    }

    @Override
    @NonNull
    public File getClassesFolder() {
        return myClassesFolder;
    }

    @Override
    @NonNull
    public File getJavaResourcesFolder() {
        return myJavaResourcesFolder;
    }

    @Override
    @NonNull
    public Dependencies getDependencies() {
        return myDependencies;
    }

    @Override
    @NonNull
    public Dependencies getCompileDependencies() {
        return myCompileDependencies;
    }

    @Override
    @NonNull
    public DependencyGraphs getDependencyGraphs() {
        return myDependencyGraphs;
    }

    @Override
    @NonNull
    public Set<String> getIdeSetupTaskNames() {
        return myIdeSetupTaskNames;
    }

    @Override
    @NonNull
    public Collection<File> getGeneratedSourceFolders() {
        return myGeneratedSourceFolders;
    }

    @Override
    @Nullable
    public SourceProvider getVariantSourceProvider() {
        return myVariantSourceProvider;
    }

    @Override
    @Nullable
    public SourceProvider getMultiFlavorSourceProvider() {
        return myMultiFlavorSourceProvider;
    }

    @Override
    @NonNull
    public Set<File> getAdditionalClassesFolders() {
        return myAdditionalClassesFolders;
    }

    @NonNull
    @Override
    public String getAssembleTaskOutputListingFile() {
        return myAssembleTaskOutputListingFile.getAbsolutePath();
    }

    @Override
    public String toString() {
        return "BaseArtifactStub{"
                + "myName='"
                + myName
                + '\''
                + ", myCompileTaskName='"
                + myCompileTaskName
                + '\''
                + ", myAssembleTaskName='"
                + myAssembleTaskName
                + '\''
                + ", myPostAssembleTaskModelFile='"
                + myAssembleTaskOutputListingFile
                + '\''
                + ", myClassesFolder="
                + myClassesFolder
                + ", myAdditionalClassesFolders="
                + myAdditionalClassesFolders
                + ", myJavaResourcesFolder="
                + myJavaResourcesFolder
                + ", myDependencies="
                + myDependencies
                + ", myCompileDependencies="
                + myCompileDependencies
                + ", myDependencyGraphs="
                + myDependencyGraphs
                + ", myIdeSetupTaskNames="
                + myIdeSetupTaskNames
                + ", myGeneratedSourceFolders="
                + myGeneratedSourceFolders
                + ", myVariantSourceProvider="
                + myVariantSourceProvider
                + ", myMultiFlavorSourceProvider="
                + myMultiFlavorSourceProvider
                + "}";
    }
}
