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
import com.android.builder.model.AndroidBundle;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AndroidBundleStub extends LibraryStub implements AndroidBundle {
    @NonNull private final File myBundle;
    @NonNull private final File myFolder;
    @NonNull private final List<AndroidLibrary> myLibraryDependencies;
    @NonNull private final Collection<JavaLibrary> myJavaDependencies;
    @NonNull private final File myManifest;
    @NonNull private final File myJarFile;
    @NonNull private final File myCompileJarFile;
    @NonNull private final File myResFolder;
    @Nullable private final File myResStaticLibrary;
    @NonNull private final File myAssetsFolder;
    @Nullable private final String myProjectVariant;

    public AndroidBundleStub() {
        this(
                new File("bundle"),
                new File("folder"),
                new ArrayList<>(),
                Lists.newArrayList(new JavaLibraryStub()),
                new File("manifest"),
                new File("jarFile"),
                new File("apiJarFile"),
                new File("resFolder"),
                new File("resStaticLibrary"),
                new File("assetsFolder"),
                "variant");
    }

    public AndroidBundleStub(
            @NonNull File bundle,
            @NonNull File folder,
            @NonNull List<AndroidLibrary> dependencies,
            @NonNull Collection<JavaLibrary> javaDependencies,
            @NonNull File manifest,
            @NonNull File jarFile,
            @NonNull File compileJarFile,
            @NonNull File resFolder,
            @Nullable File resStaticLibrary,
            @NonNull File assetsFolder,
            @Nullable String variant) {
        myBundle = bundle;
        myFolder = folder;
        myLibraryDependencies = dependencies;
        myJavaDependencies = javaDependencies;
        myManifest = manifest;
        myJarFile = jarFile;
        myCompileJarFile = compileJarFile;
        myResFolder = resFolder;
        myResStaticLibrary = resStaticLibrary;
        myAssetsFolder = assetsFolder;
        myProjectVariant = variant;
    }

    public AndroidBundleStub(
            @NonNull MavenCoordinates coordinates,
            @Nullable String buildId,
            @Nullable String project,
            @Nullable String name,
            boolean provided,
            boolean isSkipped,
            @NonNull File bundle,
            @NonNull File folder,
            @NonNull List<AndroidLibrary> dependencies,
            @NonNull Collection<JavaLibrary> javaDependencies,
            @NonNull File manifest,
            @NonNull File jarFile,
            @NonNull File compileJarFile,
            @NonNull File resFolder,
            @Nullable File resStaticLibrary,
            @NonNull File assetsFolder,
            @Nullable String variant) {
        super(coordinates, buildId, project, name, provided, isSkipped);
        myBundle = bundle;
        myFolder = folder;
        myLibraryDependencies = dependencies;
        myJavaDependencies = javaDependencies;
        myManifest = manifest;
        myJarFile = jarFile;
        myCompileJarFile = compileJarFile;
        myResFolder = resFolder;
        myResStaticLibrary = resStaticLibrary;
        myAssetsFolder = assetsFolder;
        myProjectVariant = variant;
    }

    @Override
    @NonNull
    public File getBundle() {
        return myBundle;
    }

    @Override
    @NonNull
    public File getFolder() {
        return myFolder;
    }

    @Override
    @NonNull
    public List<? extends AndroidLibrary> getLibraryDependencies() {
        return myLibraryDependencies;
    }

    @Override
    @NonNull
    public Collection<? extends JavaLibrary> getJavaDependencies() {
        return myJavaDependencies;
    }

    @Override
    @NonNull
    public File getManifest() {
        return myManifest;
    }

    @Override
    @NonNull
    public File getJarFile() {
        return myJarFile;
    }

    @Override
    @NonNull
    public File getCompileJarFile() {
        return myCompileJarFile;
    }

    @Override
    @NonNull
    public File getResFolder() {
        return myResFolder;
    }

    @Override
    @Nullable
    public File getResStaticLibrary() {
        return myResStaticLibrary;
    }

    @Override
    @NonNull
    public File getAssetsFolder() {
        return myAssetsFolder;
    }

    @Override
    @Nullable
    public String getProjectVariant() {
        return myProjectVariant;
    }

    @Override
    public String toString() {
        return "AndroidBundleStub{"
                + "myBundle="
                + myBundle
                + ", myFolder="
                + myFolder
                + ", myNamespacedStaticLibrary="
                + myResStaticLibrary
                + ", myLibraryDependencies="
                + myLibraryDependencies
                + ", myJavaDependencies="
                + myJavaDependencies
                + ", myManifest="
                + myManifest
                + ", myJarFile="
                + myJarFile
                + ", myCompileJarFile="
                + myCompileJarFile
                + ", myResFolder="
                + myResFolder
                + ", myAssetsFolder="
                + myAssetsFolder
                + ", myProjectVariant='"
                + myProjectVariant
                + '\''
                + "} "
                + super.toString();
    }
}
