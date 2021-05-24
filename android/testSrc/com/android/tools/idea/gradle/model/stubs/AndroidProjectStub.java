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
import com.android.builder.model.AaptOptions;
import com.android.builder.model.AndroidGradlePluginProjectFlags;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.ArtifactMetaData;
import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.DependenciesInfo;
import com.android.builder.model.JavaCompileOptions;
import com.android.builder.model.LintOptions;
import com.android.builder.model.NativeToolchain;
import com.android.builder.model.ProductFlavorContainer;
import com.android.builder.model.SigningConfig;
import com.android.builder.model.SyncIssue;
import com.android.builder.model.Variant;
import com.android.builder.model.VariantBuildInformation;
import com.android.builder.model.ViewBindingOptions;
import com.android.tools.idea.gradle.model.UnusedModelMethodException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AndroidProjectStub extends BaseStub implements AndroidProject {
    @NonNull private final String myModelVersion;
    @NonNull private final String myName;
    @Nullable private final String myGroupId;
    @NonNull private final String myNamespace;
    @Nullable private final String myAndroidTestNamespace;
    @NonNull private final ProductFlavorContainer myDefaultConfig;
    @NonNull private final Collection<BuildTypeContainer> myBuildTypes;
    @NonNull private final Collection<ProductFlavorContainer> myProductFlavors;
    @NonNull private final String myBuildToolsVersion;
    @NonNull private final String myNdkVersion;
    @NonNull private final Collection<SyncIssue> mySyncIssues;
    @NonNull private final Collection<Variant> myVariants;
    @NonNull private final Collection<String> myVariantNames;
    @Nullable private final String myDefaultVariant;
    @NonNull private final Collection<String> myFlavorDimensions;
    @NonNull private final String myCompileTarget;
    @NonNull private final Collection<String> myBootClasspath;
    @NonNull private final Collection<SigningConfig> mySigningConfigs;
    @NonNull private final LintOptions myLintOptions;
    @NonNull private final List<File> myLintRuleJars;
    @NonNull private final Collection<String> myUnresolvedDependencies;
    @NonNull private final JavaCompileOptions myJavaCompileOptions;
    @NonNull private final AaptOptions myAaptOptions;
    @NonNull private final File myBuildFolder;
    @NonNull private final Collection<String> myDynamicFeatures;
    @NonNull private final ViewBindingOptions myViewBindingOptions;
    @NonNull private final DependenciesInfo myDependenciesInfo;
    @Nullable private final String myResourcePrefix;
    private final int myApiVersion;
    private final boolean myLibrary;
    private final int myProjectType;
    private final boolean myBaseSplit;
    @NonNull private final AndroidGradlePluginProjectFlags myFlags;
    @NonNull private final Collection<VariantBuildInformation> myVariantsBuildInformation;

    public AndroidProjectStub(@NonNull String modelVersion) {
        this(
                modelVersion,
                "name",
                null,
                "com.example.myapp",
                "com.example.myapp.test",
                new ProductFlavorContainerStub(),
                Lists.newArrayList(new BuildTypeContainerStub()),
                Lists.newArrayList(new ProductFlavorContainerStub()),
                "buildToolsVersion",
                "ndkVersion",
                Lists.newArrayList(new SyncIssueStub()),
                Lists.newArrayList(new VariantStub()),
                Lists.newArrayList("debug", "release"),
                "debug",
                Lists.newArrayList("flavorDimension"),
                "compileTarget",
                Lists.newArrayList("bootClasspath"),
                Lists.newArrayList(new SigningConfigStub()),
                new LintOptionsStub(),
                ImmutableList.of(),
                Sets.newHashSet("unresolvedDependency"),
                new JavaCompileOptionsStub(),
                new AaptOptionsStub(),
                ImmutableList.of(),
                new ViewBindingOptionsStub(),
                new DependenciesInfoStub(),
                new File("buildFolder"),
                "resourcePrefix",
                1,
                true,
                2,
                true,
                new AndroidGradlePluginProjectFlagsStub(Collections.emptyMap()),
                Lists.newArrayList());
    }

    public AndroidProjectStub(
            @NonNull String modelVersion,
            @NonNull String name,
            @Nullable String groupId,
            @NonNull String namespace,
            @Nullable String androidTestNamespace,
            @NonNull ProductFlavorContainer defaultConfig,
            @NonNull Collection<BuildTypeContainer> buildTypes,
            @NonNull Collection<ProductFlavorContainer> productFlavors,
            @NonNull String buildToolsVersion,
            @NonNull String ndkVersion,
            @NonNull Collection<SyncIssue> syncIssues,
            @NonNull Collection<Variant> variants,
            @NonNull Collection<String> variantNames,
            @Nullable String defaultVariant,
            @NonNull Collection<String> flavorDimensions,
            @NonNull String compileTarget,
            @NonNull Collection<String> bootClasspath,
            @NonNull Collection<SigningConfig> signingConfigs,
            @NonNull LintOptions lintOptions,
            @NonNull List<File> lintRuleJars,
            @NonNull Collection<String> unresolvedDependencies,
            @NonNull JavaCompileOptions javaCompileOptions,
            @NonNull AaptOptions aaptOptions,
            @NonNull Collection<String> dynamicFeatures,
            @NonNull ViewBindingOptions viewBindingOptions,
            @NonNull DependenciesInfo dependenciesInfo,
            @NonNull File buildFolder,
            @Nullable String resourcePrefix,
            int apiVersion,
            boolean library,
            int projectType,
            boolean baseSplit,
            @NonNull AndroidGradlePluginProjectFlags flags,
            @NonNull Collection<VariantBuildInformation> variantsBuildInformation) {
        myModelVersion = modelVersion;
        myName = name;
        myGroupId = groupId;
        myNamespace = namespace;
        myAndroidTestNamespace = androidTestNamespace;
        myDefaultConfig = defaultConfig;
        myBuildTypes = buildTypes;
        myProductFlavors = productFlavors;
        myBuildToolsVersion = buildToolsVersion;
        myNdkVersion = ndkVersion;
        mySyncIssues = syncIssues;
        myVariants = variants;
        myVariantNames = variantNames;
        myDefaultVariant = defaultVariant;
        myFlavorDimensions = flavorDimensions;
        myCompileTarget = compileTarget;
        myBootClasspath = bootClasspath;
        mySigningConfigs = signingConfigs;
        myLintOptions = lintOptions;
        myLintRuleJars = lintRuleJars;
        myUnresolvedDependencies = unresolvedDependencies;
        myJavaCompileOptions = javaCompileOptions;
        myAaptOptions = aaptOptions;
        myDynamicFeatures = dynamicFeatures;
        myViewBindingOptions = viewBindingOptions;
        myDependenciesInfo = dependenciesInfo;
        myBuildFolder = buildFolder;
        myResourcePrefix = resourcePrefix;
        myApiVersion = apiVersion;
        myLibrary = library;
        myProjectType = projectType;
        myBaseSplit = baseSplit;
        myFlags = flags;
        myVariantsBuildInformation = variantsBuildInformation;
    }

    @Override
    @NonNull
    public String getModelVersion() {
        return myModelVersion;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Nullable
    @Override
    public String getGroupId() {
        return myGroupId;
    }

  @NonNull
  @Override
  public String getNamespace() {
    return myNamespace;
  }

  @NonNull
  @Override
  public String getAndroidTestNamespace() {
    return myAndroidTestNamespace;
  }

  @Override
    @NonNull
    public ProductFlavorContainer getDefaultConfig() {
        return myDefaultConfig;
    }

    @Override
    @NonNull
    public Collection<BuildTypeContainer> getBuildTypes() {
        return myBuildTypes;
    }

    @Override
    @NonNull
    public Collection<ProductFlavorContainer> getProductFlavors() {
        return myProductFlavors;
    }

    @Override
    @NonNull
    public String getBuildToolsVersion() {
        return myBuildToolsVersion;
    }

    @Override
    @NonNull
    public String getNdkVersion() {
        return myNdkVersion;
    }

    @Override
    @NonNull
    public Collection<SyncIssue> getSyncIssues() {
        return mySyncIssues;
    }

    @Override
    @NonNull
    public Collection<Variant> getVariants() {
        return myVariants;
    }

    @Override
    @NonNull
    public Collection<String> getVariantNames() {
        return myVariantNames;
    }

    @Nullable
    @Override
    public String getDefaultVariant() {
        return myDefaultVariant;
    }

    @Override
    @NonNull
    public Collection<String> getFlavorDimensions() {
        return myFlavorDimensions;
    }

    @Override
    @NonNull
    public Collection<ArtifactMetaData> getExtraArtifacts() {
        throw new UnusedModelMethodException("getExtraArtifacts");
    }

    @Override
    @NonNull
    public String getCompileTarget() {
        return myCompileTarget;
    }

    @Override
    @NonNull
    public Collection<String> getBootClasspath() {
        return myBootClasspath;
    }

    @Override
    @NonNull
    public Collection<File> getFrameworkSources() {
        throw new UnusedModelMethodException("getFrameworkSources");
    }

    @Override
    @NonNull
    public Collection<NativeToolchain> getNativeToolchains() {
        throw new UnsupportedOperationException(
                "NativeToolchain is deprecated in favor of V2 native models.");
    }

    @Override
    @NonNull
    public AaptOptions getAaptOptions() {
        return myAaptOptions;
    }

    @Override
    @NonNull
    public Collection<SigningConfig> getSigningConfigs() {
        return mySigningConfigs;
    }

    @Override
    @NonNull
    public LintOptions getLintOptions() {
        return myLintOptions;
    }

    @Override
    @NonNull
    public Collection<String> getUnresolvedDependencies() {
        return myUnresolvedDependencies;
    }

    @Override
    @NonNull
    public JavaCompileOptions getJavaCompileOptions() {
        return myJavaCompileOptions;
    }

    @Override
    @NonNull
    public File getBuildFolder() {
        return myBuildFolder;
    }

    @Override
    @Nullable
    public String getResourcePrefix() {
        return myResourcePrefix;
    }

    @Override
    public int getApiVersion() {
        return myApiVersion;
    }

    @Deprecated
    @Override
    public boolean isLibrary() {
        return myLibrary;
    }

    @Override
    public int getProjectType() {
        return myProjectType;
    }

    @Override
    public int getPluginGeneration() {
        return GENERATION_ORIGINAL;
    }

    @Override
    public boolean isBaseSplit() {
        return myBaseSplit;
    }

    @NonNull
    @Override
    public Collection<String> getDynamicFeatures() {
        return myDynamicFeatures;
    }

    @NonNull
    @Override
    public ViewBindingOptions getViewBindingOptions() {
        return myViewBindingOptions;
    }

    @Nullable
    @Override
    public DependenciesInfo getDependenciesInfo() {
        return myDependenciesInfo;
    }

    @NonNull
    @Override
    public AndroidGradlePluginProjectFlags getFlags() {
        return myFlags;
    }

    @NonNull
    @Override
    public Collection<VariantBuildInformation> getVariantsBuildInformation() {
        return myVariantsBuildInformation;
    }

    @Override
    @NonNull
    public List<File> getLintRuleJars() {
        return myLintRuleJars;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AndroidProject)) {
            return false;
        }
        AndroidProject stub = (AndroidProject) o;
        return getApiVersion() == stub.getApiVersion()
                && isLibrary() == stub.isLibrary()
                && getProjectType() == stub.getProjectType()
                && isBaseSplit() == stub.isBaseSplit()
                && Objects.equals(getModelVersion(), stub.getModelVersion())
                && Objects.equals(getName(), stub.getName())
                && Objects.equals(getGroupId(), stub.getGroupId())
                && Objects.equals(getDefaultConfig(), stub.getDefaultConfig())
                && Objects.equals(getBuildTypes(), stub.getBuildTypes())
                && Objects.equals(getProductFlavors(), stub.getProductFlavors())
                && Objects.equals(getBuildToolsVersion(), stub.getBuildToolsVersion())
                && Objects.equals(getNdkVersion(), stub.getNdkVersion())
                && Objects.equals(getSyncIssues(), stub.getSyncIssues())
                && Objects.equals(getVariants(), stub.getVariants())
                && Objects.equals(getVariantNames(), stub.getVariantNames())
                && Objects.equals(getFlavorDimensions(), stub.getFlavorDimensions())
                && Objects.equals(getCompileTarget(), stub.getCompileTarget())
                && Objects.equals(getBootClasspath(), stub.getBootClasspath())
                && Objects.equals(getNativeToolchains(), stub.getNativeToolchains())
                && Objects.equals(getSigningConfigs(), stub.getSigningConfigs())
                && Objects.equals(getLintOptions(), stub.getLintOptions())
                && Objects.equals(getLintRuleJars(), stub.getLintRuleJars())
                && Objects.equals(getUnresolvedDependencies(), stub.getUnresolvedDependencies())
                && Objects.equals(getJavaCompileOptions(), stub.getJavaCompileOptions())
                && Objects.equals(getBuildFolder(), stub.getBuildFolder())
                && Objects.equals(getResourcePrefix(), stub.getResourcePrefix())
                && Objects.equals(getDynamicFeatures(), stub.getDynamicFeatures())
                && Objects.equals(getViewBindingOptions(), stub.getViewBindingOptions())
                && Objects.equals(getDependenciesInfo(), stub.getDependenciesInfo())
                && Objects.equals(getFlags(), stub.getFlags())
                && Objects.equals(
                        getVariantsBuildInformation(), stub.getVariantsBuildInformation());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getModelVersion(),
                getName(),
                getGroupId(),
                getDefaultConfig(),
                getBuildTypes(),
                getProductFlavors(),
                getBuildToolsVersion(),
                getNdkVersion(),
                getSyncIssues(),
                getVariants(),
                getVariantNames(),
                getFlavorDimensions(),
                getCompileTarget(),
                getBootClasspath(),
                getNativeToolchains(),
                getSigningConfigs(),
                getLintOptions(),
                getLintRuleJars(),
                getUnresolvedDependencies(),
                getJavaCompileOptions(),
                getBuildFolder(),
                getResourcePrefix(),
                getApiVersion(),
                isLibrary(),
                getProjectType(),
                isBaseSplit(),
                getDynamicFeatures(),
                getViewBindingOptions(),
                getDependenciesInfo(),
                getFlags(),
                getVariantsBuildInformation());
    }

    @Override
    public String toString() {
        return "AndroidProjectStub{"
                + "myModelVersion='"
                + myModelVersion
                + '\''
                + ", myName='"
                + myName
                + '\''
                + ", myGroupId='"
                + myGroupId
                + '\''
                + ", myDefaultConfig="
                + myDefaultConfig
                + ", myBuildTypes="
                + myBuildTypes
                + ", myProductFlavors="
                + myProductFlavors
                + ", myBuildToolsVersion='"
                + myBuildToolsVersion
                + ", myNdkVersion='"
                + myNdkVersion
                + '\''
                + ", mySyncIssues="
                + mySyncIssues
                + ", myVariants="
                + myVariants
                + ", myVariantNames="
                + myVariantNames
                + ", myDefaultVariant="
                + myDefaultVariant
                + ", myFlavorDimensions="
                + myFlavorDimensions
                + ", myCompileTarget='"
                + myCompileTarget
                + '\''
                + ", myBootClasspath="
                + myBootClasspath
                + ", mySigningConfigs="
                + mySigningConfigs
                + ", myLintOptions="
                + myLintOptions
                + ", myUnresolvedDependencies="
                + myUnresolvedDependencies
                + ", myJavaCompileOptions="
                + myJavaCompileOptions
                + ", myBuildFolder="
                + myBuildFolder
                + ", myResourcePrefix='"
                + myResourcePrefix
                + '\''
                + ", myApiVersion="
                + myApiVersion
                + ", myLibrary="
                + myLibrary
                + ", myProjectType="
                + myProjectType
                + ", myBaseSplit="
                + myBaseSplit
                + ", myDynamicFeatures="
                + myDynamicFeatures
                + ", myViewBindingOptions="
                + myViewBindingOptions
                + ", myDependenciesInfo="
                + myDependenciesInfo
                + ", myFlags="
                + myFlags
                + ", myVariantBuildInformnation"
                + myVariantsBuildInformation
                + "}";
    }
}
