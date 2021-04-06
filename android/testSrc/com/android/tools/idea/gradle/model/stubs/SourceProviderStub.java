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
import com.android.builder.model.SourceProvider;
import com.google.common.collect.Lists;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

public class SourceProviderStub extends BaseStub implements SourceProvider {
    @NonNull private final String myName;
    @NonNull private final File myManifestFile;
    @NonNull private final Collection<File> myJavaDirectories;
    @NonNull private final Collection<File> myKotlinDirectories;
    @NonNull private final Collection<File> myResourcesDirectories;
    @NonNull private final Collection<File> myAidlDirectories;
    @NonNull private final Collection<File> myRenderscriptDirectories;
    @NonNull private final Collection<File> myResDirectories;
    @NonNull private final Collection<File> myAssetsDirectories;
    @NonNull private final Collection<File> myJniLibsDirectories;
    @NonNull private final Collection<File> myShadersDirectories;
    @NonNull private final Collection<File> myMlModelsDirectories;

    public SourceProviderStub() {
        this("name", new File("/"), "manifest");
    }

    public SourceProviderStub(
            @NonNull String name, @NonNull File rootDirectory, @NonNull String manifestFileName) {
        this(
                name,
                new File(rootDirectory, manifestFileName),
                new File(rootDirectory, "java"),
                new File(rootDirectory, "kotlin"),
                new File(rootDirectory, "resources"),
                new File(rootDirectory, "aidl"),
                new File(rootDirectory, "renderscript"),
                new File(rootDirectory, "res"),
                new File(rootDirectory, "assets"),
                new File(rootDirectory, "jniLibs"),
                new File(rootDirectory, "shaders"),
                new File(rootDirectory, "ml"));
    }

    public SourceProviderStub(
            @NonNull String name,
            @NonNull File manifestFile,
            @NonNull File javaDirectory,
            @NonNull File kotlinDirectory,
            @NonNull File resourcesDirectory,
            @NonNull File aidlDirectory,
            @NonNull File renderscriptDirectory,
            @NonNull File resDirectory,
            @NonNull File assetsDirectory,
            @NonNull File jniLibsDirectory,
            @NonNull File shadersDirectory,
            @NonNull File mlMlModelsDirectory) {
        myName = name;
        myManifestFile = manifestFile;
        myJavaDirectories = Lists.newArrayList(javaDirectory);
        myKotlinDirectories = Lists.newArrayList(kotlinDirectory);
        myResourcesDirectories = Lists.newArrayList(resourcesDirectory);
        myAidlDirectories = Lists.newArrayList(aidlDirectory);
        myRenderscriptDirectories = Lists.newArrayList(renderscriptDirectory);
        myResDirectories = Lists.newArrayList(resDirectory);
        myAssetsDirectories = Lists.newArrayList(assetsDirectory);
        myJniLibsDirectories = Lists.newArrayList(jniLibsDirectory);
        myShadersDirectories = Lists.newArrayList(shadersDirectory);
        myMlModelsDirectories = Lists.newArrayList(mlMlModelsDirectory);
    }

    public SourceProviderStub(
            @NonNull String name,
            @NonNull File manifestFile,
            @NonNull Collection<File> javaDirectories,
            @NonNull Collection<File> kotlinDirectories,
            @NonNull Collection<File> resourcesDirectories,
            @NonNull Collection<File> aidlDirectories,
            @NonNull Collection<File> renderscriptDirectories,
            @NonNull Collection<File> resDirectories,
            @NonNull Collection<File> assetsDirectories,
            @NonNull Collection<File> jniLibsDirectories,
            @NonNull Collection<File> shadersDirectories,
            @NonNull Collection<File> mlModelsDirectories) {
        myName = name;
        myManifestFile = manifestFile;
        myJavaDirectories = Lists.newArrayList(javaDirectories);
        myKotlinDirectories = Lists.newArrayList(kotlinDirectories);
        myResourcesDirectories = Lists.newArrayList(resourcesDirectories);
        myAidlDirectories = Lists.newArrayList(aidlDirectories);
        myRenderscriptDirectories = Lists.newArrayList(renderscriptDirectories);
        myResDirectories = Lists.newArrayList(resDirectories);
        myAssetsDirectories = Lists.newArrayList(assetsDirectories);
        myJniLibsDirectories = Lists.newArrayList(jniLibsDirectories);
        myShadersDirectories = Lists.newArrayList(shadersDirectories);
        myMlModelsDirectories = Lists.newArrayList(mlModelsDirectories);
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public File getManifestFile() {
        return myManifestFile;
    }

    @Override
    @NonNull
    public Collection<File> getJavaDirectories() {
        return myJavaDirectories;
    }

    @NotNull
    @Override
    public Collection<File> getKotlinDirectories() {
        return myKotlinDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getResourcesDirectories() {
        return myResourcesDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getAidlDirectories() {
        return myAidlDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getRenderscriptDirectories() {
        return myRenderscriptDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getCDirectories() {
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public Collection<File> getCppDirectories() {
        return Collections.emptyList();
    }

    @Override
    @NonNull
    public Collection<File> getResDirectories() {
        return myResDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getAssetsDirectories() {
        return myAssetsDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getJniLibsDirectories() {
        return myJniLibsDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getShadersDirectories() {
        return myShadersDirectories;
    }

    @Override
    @NonNull
    public Collection<File> getMlModelsDirectories() {
        return myMlModelsDirectories;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SourceProvider)) {
            return false;
        }
        SourceProvider stub = (SourceProvider) o;
        return Objects.equals(getName(), stub.getName())
                && Objects.equals(getManifestFile(), stub.getManifestFile())
                && Objects.equals(getJavaDirectories(), stub.getJavaDirectories())
                && Objects.equals(getKotlinDirectories(), stub.getKotlinDirectories())
                && Objects.equals(getResourcesDirectories(), stub.getResourcesDirectories())
                && Objects.equals(getAidlDirectories(), stub.getAidlDirectories())
                && Objects.equals(getRenderscriptDirectories(), stub.getRenderscriptDirectories())
                && Objects.equals(getResDirectories(), stub.getResDirectories())
                && Objects.equals(getAssetsDirectories(), stub.getAssetsDirectories())
                && Objects.equals(getJniLibsDirectories(), stub.getJniLibsDirectories())
                && Objects.equals(getShadersDirectories(), stub.getShadersDirectories())
                && Objects.equals(getMlModelsDirectories(), stub.getMlModelsDirectories());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getManifestFile(),
                getJavaDirectories(),
                getKotlinDirectories(),
                getResourcesDirectories(),
                getAidlDirectories(),
                getRenderscriptDirectories(),
                getResDirectories(),
                getAssetsDirectories(),
                getJniLibsDirectories(),
                getShadersDirectories(),
                getMlModelsDirectories());
    }

    @Override
    public String toString() {
        return "SourceProviderStub{"
                + "myName='"
                + myName
                + '\''
                + ", myManifestFile="
                + myManifestFile
                + ", myJavaDirectories="
                + myJavaDirectories
                + ", myKotlinDirectories="
                + myKotlinDirectories
                + ", myResourcesDirectories="
                + myResourcesDirectories
                + ", myAidlDirectories="
                + myAidlDirectories
                + ", myRenderscriptDirectories="
                + myRenderscriptDirectories
                + ", myResDirectories="
                + myResDirectories
                + ", myAssetsDirectories="
                + myAssetsDirectories
                + ", myJniLibsDirectories="
                + myJniLibsDirectories
                + ", myShadersDirectories="
                + myShadersDirectories
                + ", myMlModelsDirectories="
                + myMlModelsDirectories
                + "}";
    }
}
