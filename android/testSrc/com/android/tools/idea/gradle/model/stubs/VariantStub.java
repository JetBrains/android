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
import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.JavaArtifact;
import com.android.builder.model.ProductFlavor;
import com.android.builder.model.TestedTargetVariant;
import com.android.builder.model.Variant;
import com.android.utils.StringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class VariantStub extends BaseStub implements Variant {
    @NonNull private final String myName;
    @NonNull private final String myDisplayName;
    @NonNull private final AndroidArtifact myMainArtifact;
    @NonNull private final Collection<AndroidArtifact> myExtraAndroidArtifacts;
    @NonNull private final Collection<JavaArtifact> myExtraJavaArtifacts;
    @NonNull private final String myBuildType;
    @NonNull private final List<String> myProductFlavors;
    @NonNull private final ProductFlavor myMergedFlavor;
    @NonNull private final Collection<TestedTargetVariant> myTestedTargetVariants;
    private final boolean myInstantAppCompatible;
    @NonNull private final List<String> myDesugaredMethods;

    public VariantStub() {
        this(
                "name",
                "displayName",
                new AndroidArtifactStub(AndroidProject.ARTIFACT_MAIN),
                Lists.newArrayList(new AndroidArtifactStub(AndroidProject.ARTIFACT_ANDROID_TEST)),
                Lists.newArrayList(new JavaArtifactStub()),
                "buildType",
                Lists.newArrayList("flavor"),
                new ProductFlavorStub(),
                Lists.newArrayList(new TestedTargetVariantStub()),
                false,
                Collections.emptyList());
    }

    public VariantStub(String name, String buildType, String... flavors) {
        this(
                name,
                "display" + StringHelper.usLocaleCapitalize(name),
                new AndroidArtifactStub(AndroidProject.ARTIFACT_MAIN),
                Lists.newArrayList(new AndroidArtifactStub(AndroidProject.ARTIFACT_ANDROID_TEST)),
                Lists.newArrayList(new JavaArtifactStub()),
                buildType,
                ImmutableList.copyOf(flavors),
                new ProductFlavorStub(),
                Lists.newArrayList(new TestedTargetVariantStub()),
                false,
                Collections.emptyList());
    }

    public VariantStub(
            @NonNull String name,
            @NonNull String displayName,
            @NonNull AndroidArtifact mainArtifact,
            @NonNull Collection<AndroidArtifact> extraAndroidArtifacts,
            @NonNull Collection<JavaArtifact> extraJavaArtifacts,
            @NonNull String buildType,
            @NonNull List<String> productFlavors,
            @NonNull ProductFlavor mergedFlavor,
            @NonNull Collection<TestedTargetVariant> testedTargetVariants,
            boolean instantAppCompatible,
            @NonNull List<String> desugaredMethods) {
        myName = name;
        myDisplayName = displayName;
        myMainArtifact = mainArtifact;
        myExtraAndroidArtifacts = extraAndroidArtifacts;
        myExtraJavaArtifacts = extraJavaArtifacts;
        myBuildType = buildType;
        myProductFlavors = productFlavors;
        myMergedFlavor = mergedFlavor;
        myTestedTargetVariants = testedTargetVariants;
        myInstantAppCompatible = instantAppCompatible;
        myDesugaredMethods = desugaredMethods;
    }

    @Override
    @NonNull
    public String getName() {
        return myName;
    }

    @Override
    @NonNull
    public String getDisplayName() {
        return myDisplayName;
    }

    @Override
    @NonNull
    public AndroidArtifact getMainArtifact() {
        return myMainArtifact;
    }

    @Override
    @NonNull
    public Collection<AndroidArtifact> getExtraAndroidArtifacts() {
        return myExtraAndroidArtifacts;
    }

    @Override
    @NonNull
    public Collection<JavaArtifact> getExtraJavaArtifacts() {
        return myExtraJavaArtifacts;
    }

    @Override
    @NonNull
    public String getBuildType() {
        return myBuildType;
    }

    @Override
    @NonNull
    public List<String> getProductFlavors() {
        return myProductFlavors;
    }

    @Override
    @NonNull
    public ProductFlavor getMergedFlavor() {
        return myMergedFlavor;
    }

    @Override
    @NonNull
    public Collection<TestedTargetVariant> getTestedTargetVariants() {
        return myTestedTargetVariants;
    }

    @Override
    public boolean isInstantAppCompatible() {
        return myInstantAppCompatible;
    }

    @NonNull
    @Override
    public List<String> getDesugaredMethods() {
        return myDesugaredMethods;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Variant)) {
            return false;
        }
        Variant variant = (Variant) o;
        return Objects.equals(getName(), variant.getName())
                && Objects.equals(getDisplayName(), variant.getDisplayName())
                && Objects.equals(getMainArtifact(), variant.getMainArtifact())
                && Objects.equals(getExtraAndroidArtifacts(), variant.getExtraAndroidArtifacts())
                && Objects.equals(getExtraJavaArtifacts(), variant.getExtraJavaArtifacts())
                && Objects.equals(getBuildType(), variant.getBuildType())
                && Objects.equals(getProductFlavors(), variant.getProductFlavors())
                && Objects.equals(getMergedFlavor(), variant.getMergedFlavor())
                && Objects.equals(getTestedTargetVariants(), variant.getTestedTargetVariants())
                && Objects.equals(isInstantAppCompatible(), variant.isInstantAppCompatible())
                && Objects.equals(getDesugaredMethods(), variant.getDesugaredMethods());
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                getName(),
                getDisplayName(),
                getMainArtifact(),
                getExtraAndroidArtifacts(),
                getExtraJavaArtifacts(),
                getBuildType(),
                getProductFlavors(),
                getMergedFlavor(),
                getTestedTargetVariants(),
                isInstantAppCompatible(),
                getDesugaredMethods());
    }

    @Override
    public String toString() {
        return "VariantStub{"
                + "myName='"
                + myName
                + '\''
                + ", myDisplayName='"
                + myDisplayName
                + '\''
                + ", myMainArtifact="
                + myMainArtifact
                + ", myExtraAndroidArtifacts="
                + myExtraAndroidArtifacts
                + ", myExtraJavaArtifacts="
                + myExtraJavaArtifacts
                + ", myBuildType='"
                + myBuildType
                + '\''
                + ", myProductFlavors="
                + myProductFlavors
                + ", myMergedFlavor="
                + myMergedFlavor
                + ", myTestedTargetVariants="
                + myTestedTargetVariants
                + ", myInstantAppCompatible="
                + myInstantAppCompatible
                + ", myDesugaredMethods="
                + myDesugaredMethods
                + "}";
    }
}
