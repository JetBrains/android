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
package com.android.tools.idea.avdmanager;

import static com.android.SdkConstants.*;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.repository.io.FileOp;
import com.android.sdklib.FileOpFileWrapper;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;
import java.io.File;
import java.util.Map;

/** A class to query Emulator advanced feature flags. */
public final class EmulatorAdvFeatures {

    public static final String FEATURE_FAST_BOOT = "FastSnapshotV1"; // Emulator feature support
    public static final String FEATURE_SCREEN_RECORDING =
            "ScreenRecording"; // Emulator screen recording feature

    private static Map<String, String> mEmuAdvFeatures; // Advanced Emulator Features

    /**
     * Indicates if the Emulator supports the requested advanced feature.
     *
     * @param theFeature The name of the requested feature.
     * @return true if the feature is "on" in the Emulator.
     */
    public static boolean emulatorSupportsFeature(
            @NonNull String theFeature,
            @Nullable AndroidSdkHandler sdkHandler,
            @NonNull ProgressIndicator progressIndicator,
            @NonNull ILogger log) {
        if (mEmuAdvFeatures == null) {
            if (sdkHandler == null) {
                return false; // 'False' is the safer guess
            }
            LocalPackage emulatorPackage =
                    sdkHandler.getLocalPackage(FD_EMULATOR, progressIndicator);
            if (emulatorPackage != null) {
                File emuAdvFeaturesFile =
                        new File(
                                emulatorPackage.getLocation(),
                                FD_LIB + File.separator + FN_ADVANCED_FEATURES);
                FileOp fop = sdkHandler.getFileOp();
                if (fop.exists(emuAdvFeaturesFile)) {
                    mEmuAdvFeatures =
                            ProjectProperties.parsePropertyFile(
                                    new FileOpFileWrapper(emuAdvFeaturesFile, fop, false), log);
                }
            }
        }
        return mEmuAdvFeatures != null && "on".equals(mEmuAdvFeatures.get(theFeature));
    }

    /**
     * Indicates if the Emulator supports the Fast Boot feature
     *
     * @return true if Fast Boot is supported
     */
    public static boolean emulatorSupportsFastBoot(
            @Nullable AndroidSdkHandler sdkHandler,
            @NonNull ProgressIndicator progressIndicator,
            @NonNull ILogger log) {
        return emulatorSupportsFeature(FEATURE_FAST_BOOT, sdkHandler, progressIndicator, log);
    }

    /**
     * Indicates if the Emulator supports screen recording feature
     *
     * @return true if screen recording is supported
     */
    public static boolean emulatorSupportsScreenRecording(
            @Nullable AndroidSdkHandler sdkHandler,
            @NonNull ProgressIndicator progressIndicator,
            @NonNull ILogger log) {
        return emulatorSupportsFeature(
                FEATURE_SCREEN_RECORDING, sdkHandler, progressIndicator, log);
    }
}
