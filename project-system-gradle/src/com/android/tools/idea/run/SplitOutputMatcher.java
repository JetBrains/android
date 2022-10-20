/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.idea.run;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.build.OutputFile;
import com.android.tools.idea.gradle.model.IdeAndroidArtifactOutput;
import com.android.tools.idea.gradle.model.IdeFilterData;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Helper class to help with installation of multi-output variants.
 */
public class SplitOutputMatcher {

    /**
     * Determines and return the list of APKs to use based on given device abis.
     *
     * <p>This uses the same logic as the store, using two passes: First, find all the compatible
     * outputs. Then take the one with the highest versionCode.
     *
     * @param outputs the outputs to choose from.
     * @param variantAbiFilters a list of abi filters applied to the variant. This is used in place
     *     of the outputs, if there is a single output with no abi filters. If the list is null,
     *     then the variant does not restrict ABI packaging.
     * @param deviceAbis a list of ABIs supported by the device.
     * @return the list of APKs to install or null if none are compatible.
     */
    @NonNull
    public static List<IdeAndroidArtifactOutput> computeBestOutput(
            @NonNull Collection<IdeAndroidArtifactOutput> outputs,
            @Nullable Collection<String> variantAbiFilters,
            @NonNull List<String> deviceAbis) {

        // gather all compatible matches.
        List<IdeAndroidArtifactOutput> matches = new ArrayList<>();

        // find a matching output.
        for (IdeAndroidArtifactOutput variantOutput : outputs) {
            String abiFilter = getFilter(variantOutput, OutputFile.ABI);

            if (abiFilter != null && !deviceAbis.contains(abiFilter)) {
                continue;
            }
            matches.add(variantOutput);
        }

        if (matches.isEmpty()) {
            return ImmutableList.of();
        }

        IdeAndroidArtifactOutput match =
                Collections.max(
                        matches,
                        (splitOutput, splitOutput2) -> {
                            int rc = splitOutput.getVersionCode() - splitOutput2.getVersionCode();
                            if (rc != 0) {
                                return rc;
                            }
                            int abiOrder1 = getAbiPreferenceOrder(splitOutput, deviceAbis);
                            int abiOrder2 = getAbiPreferenceOrder(splitOutput2, deviceAbis);
                            return abiOrder1 - abiOrder2;
                        });

        return isMainApkCompatibleWithDevice(match, variantAbiFilters, deviceAbis)
                ? ImmutableList.of(match)
                : ImmutableList.of();
    }

    /**
     * Return the preference score of a VariantOutput for the deviceAbi list.
     *
     * <p>Higher score means a better match. Scores returned by different call are only comparable
     * if the specified deviceAbi is the same.
     */
    private static int getAbiPreferenceOrder(
            IdeAndroidArtifactOutput variantOutput, List<String> deviceAbi) {
        String abiFilter = getFilter(variantOutput, IdeAndroidArtifactOutput.ABI);
        if (Strings.isNullOrEmpty(abiFilter)) {
            // Null or empty imply a universal APK, which would return the second highest score.
            return deviceAbi.size() - 1;
        }
        int match = deviceAbi.indexOf(abiFilter);
        if (match == 0) {
            // We want to select the output that matches the first deviceAbi.  The filtered output
            // is preferred over universal APK if it matches the first deviceAbi as they are likely
            // to take a shorter time to build.
            match = deviceAbi.size();  // highest possible score for the specified deviceAbi.
        } else if (match > 0) {
            // Universal APK may contain the best match even though it is not guaranteed, that's
            // why it is preferred over a filtered output that does not match the best ABI.
            match = deviceAbi.size() - match - 1;
        }
        return match;
    }

    private static boolean isMainApkCompatibleWithDevice(
            IdeAndroidArtifactOutput mainOutputFile,
            Collection<String> variantAbiFilters,
            Collection<String> deviceAbis) {
        // so far, we are not dealing with the pure split files...
        if (getFilter(mainOutputFile, IdeAndroidArtifactOutput.ABI) == null
                && variantAbiFilters != null
                && !variantAbiFilters.isEmpty()) {
            // if we have a match that has no abi filter, and we have variant-level filters, then
            // we need to make sure that the variant filters are compatible with the device abis.
            for (String abi : deviceAbis) {
                if (variantAbiFilters.contains(abi)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    @Nullable
    private static String getFilter(
            @NonNull IdeAndroidArtifactOutput variantOutput, @NonNull String filterType) {
        for (IdeFilterData filterData : variantOutput.getFilters()) {
            if (filterData.getFilterType().equals(filterType)) {
                return filterData.getIdentifier();
            }
        }
        return null;
    }
}
