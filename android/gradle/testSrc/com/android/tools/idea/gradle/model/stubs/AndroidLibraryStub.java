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

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.MavenCoordinates;
import com.android.tools.idea.gradle.model.UnusedModelMethodException;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidLibraryStub extends AndroidBundleStub implements AndroidLibrary {
    @NotNull private final Collection<File> myLocalJars;
    @NotNull private final File myProguardRules;
    @NotNull private final File myLintJar;
    @NotNull private final File myPublicResources;
    @NotNull private final File mySymbolFile;
    @NotNull private final File myExternalAnnotations;

    public AndroidLibraryStub() {
        this(
                Lists.newArrayList(new File("jar")),
                new File("proguardRules"),
                new File("lintJar"),
                new File("publicResources"),
                new File("symbolFile"),
                new File("externalAnnotations"));
    }

    public AndroidLibraryStub(
            @NotNull Collection<File> jars,
            @NotNull File proguardRules,
            @NotNull File lintJar,
            @NotNull File publicResources,
            @NotNull File symbolFile,
            @NotNull File externalAnnotations) {
        myLocalJars = jars;
        myProguardRules = proguardRules;
        myLintJar = lintJar;
        myPublicResources = publicResources;
        mySymbolFile = symbolFile;
        myExternalAnnotations = externalAnnotations;
    }

    public AndroidLibraryStub(
            @NotNull MavenCoordinates coordinates,
            @Nullable String buildId,
            @Nullable String project,
            @Nullable String name,
            boolean provided,
            boolean isSkipped,
            @NotNull File bundle,
            @NotNull File folder,
            @NotNull List<AndroidLibrary> dependencies,
            @NotNull Collection<JavaLibrary> javaDependencies,
            @NotNull File manifest,
            @NotNull File jarFile,
            @NotNull File compileJarFile,
            @NotNull File resFolder,
            @Nullable File resStaticLibrary,
            @NotNull File assetsFolder,
            @Nullable String variant,
            @NotNull Collection<File> jars,
            @NotNull File proguardRules,
            @NotNull File lintJar,
            @NotNull File publicResources,
            @NotNull File symbolFile,
            @NotNull File externalAnnotations) {
        super(
                coordinates,
                buildId,
                project,
                name,
                provided,
                isSkipped,
                bundle,
                folder,
                dependencies,
                javaDependencies,
                manifest,
                jarFile,
                compileJarFile,
                resFolder,
                resStaticLibrary,
                assetsFolder,
                variant);
        myLocalJars = jars;
        myProguardRules = proguardRules;
        myLintJar = lintJar;
        myPublicResources = publicResources;
        mySymbolFile = symbolFile;
        myExternalAnnotations = externalAnnotations;
    }

    @Override
    @NotNull
    public Collection<File> getLocalJars() {
        return myLocalJars;
    }

    @Override
    @NotNull
    public File getJniFolder() {
        throw new UnusedModelMethodException("getJniFolder");
    }

    @Override
    @NotNull
    public File getAidlFolder() {
        throw new UnusedModelMethodException("getRenderscriptFolder");
    }

    @Override
    @NotNull
    public File getRenderscriptFolder() {
        throw new UnusedModelMethodException("getRenderscriptFolder");
    }

    @Override
    @NotNull
    public File getProguardRules() {
        return myProguardRules;
    }

    @Override
    @NotNull
    public File getLintJar() {
        return myLintJar;
    }

    @Override
    @NotNull
    public File getExternalAnnotations() {
        return myExternalAnnotations;
    }

    @Override
    @NotNull
    public File getPublicResources() {
        return myPublicResources;
    }

    @Override
    @NotNull
    public File getSymbolFile() {
        return mySymbolFile;
    }

    @Override
    @Deprecated
    public boolean isOptional() {
        throw new UnusedModelMethodException("isOptional");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AndroidLibrary)) {
            return false;
        }
        AndroidLibrary library = (AndroidLibrary) o;
        return Objects.equals(getBundle(), library.getBundle())
                && Objects.equals(getFolder(), library.getFolder())
                && Objects.equals(getLibraryDependencies(), library.getLibraryDependencies())
                && Objects.equals(getJavaDependencies(), library.getJavaDependencies())
                && Objects.equals(getManifest(), library.getManifest())
                && Objects.equals(getJarFile(), library.getJarFile())
                && Objects.equals(getResFolder(), library.getResFolder())
                && Objects.equals(getAssetsFolder(), library.getAssetsFolder())
                && Objects.equals(getProjectVariant(), library.getProjectVariant())
                && Objects.equals(getLocalJars(), library.getLocalJars())
                && Objects.equals(getProguardRules(), library.getProguardRules())
                && Objects.equals(getLintJar(), library.getLintJar())
                && Objects.equals(getPublicResources(), library.getPublicResources())
                && Objects.equals(getSymbolFile(), library.getSymbolFile())
                && Objects.equals(getExternalAnnotations(), library.getExternalAnnotations());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getBundle(),
                getFolder(),
                getLibraryDependencies(),
                getJavaDependencies(),
                getManifest(),
                getJarFile(),
                getResFolder(),
                getAssetsFolder(),
                getProjectVariant(),
                getLocalJars(),
                getProguardRules(),
                getLintJar(),
                getPublicResources(),
                getSymbolFile(),
                getExternalAnnotations());
    }

    @Override
    public String toString() {
        return "AndroidLibraryStub{"
                + "myLocalJars="
                + myLocalJars
                + ", myProguardRules="
                + myProguardRules
                + ", myLintJar="
                + myLintJar
                + ", myPublicResources="
                + myPublicResources
               + ", mySymbolFile="
               + mySymbolFile
               + ", myExternalAnnotations="
               + myExternalAnnotations
                + "} "
                + super.toString();
    }
}
