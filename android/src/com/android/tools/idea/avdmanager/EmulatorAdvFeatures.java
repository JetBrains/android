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

import static com.android.SdkConstants.FD_EMULATOR;
import static com.android.SdkConstants.FD_LIB;
import static com.android.SdkConstants.FN_ADVANCED_FEATURES;
import static com.android.SdkConstants.FN_ADVANCED_FEATURES_CANARY;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.repository.api.LocalPackage;
import com.android.repository.api.ProgressIndicator;
import com.android.sdklib.PathFileWrapper;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.utils.ILogger;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** A class to query Emulator advanced feature flags. */
public final class EmulatorAdvFeatures {

    public static final String FEATURE_FAST_BOOT = "FastSnapshotV1"; // Emulator feature support
    public static final String FEATURE_SCREEN_RECORDING =
            "ScreenRecording"; // Emulator screen recording feature
    public static final String FEATURE_VIRTUAL_SCENE =
            "VirtualScene"; // Emulator virtual scene feature

    private static Map<String, String> mEmuAdvFeatures; // Advanced Emulator Features
    private static Map<String, String> mEmuAdvFeaturesCanary; // Advanced Emulator Features (canary)

    @Nullable
    public static Map<String, String> getEmulatorFeaturesMap(
            @NonNull String featuresFile,
            @Nullable AndroidSdkHandler sdkHandler,
            @NonNull ProgressIndicator progressIndicator,
            @NonNull ILogger log) {
        if (sdkHandler == null) {
            return null;
        }

        LocalPackage emulatorPackage =
          sdkHandler.getLocalPackage(FD_EMULATOR, progressIndicator);
        if (emulatorPackage != null) {
            Path emuAdvFeaturesFile = emulatorPackage.getLocation().resolve(FD_LIB + File.separator + featuresFile);

            if (Files.exists(emuAdvFeaturesFile)) {
                return ProjectProperties.parsePropertyFile(
                  new PathFileWrapper(emuAdvFeaturesFile), log);
            }
        }

        return null;
    }
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
        Map<String, String> theMap;
        ChannelStatus channelStatus = UpdateSettings.getInstance().getSelectedChannelStatus();
        // TODO(joshuaduong): If emulator decides to use other channels too (beta, dev), need to add
        // more conditions to handle it.
        switch (channelStatus) {
            case EAP: // canary channel
                if (mEmuAdvFeaturesCanary == null) {
                    mEmuAdvFeaturesCanary = getEmulatorFeaturesMap(FN_ADVANCED_FEATURES_CANARY, sdkHandler, progressIndicator, log);
                }
                theMap = mEmuAdvFeaturesCanary;
                break;
            default:
                if (mEmuAdvFeatures == null) {
                    mEmuAdvFeatures = getEmulatorFeaturesMap(FN_ADVANCED_FEATURES, sdkHandler, progressIndicator, log);
                }
                theMap = mEmuAdvFeatures;
                break;
        }

        if (channelStatus != ChannelStatus.RELEASE && theMap == null) {
            // Fallback to stable advanced features file
            if (mEmuAdvFeatures == null) {
                mEmuAdvFeatures = getEmulatorFeaturesMap(FN_ADVANCED_FEATURES, sdkHandler, progressIndicator, log);
            }
            theMap = mEmuAdvFeatures;
        }

        return theMap != null && "on".equals(theMap.get(theFeature));
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

    /**
     * Indicates if the Emulator supports the virtual scene feature
     *
     * @return true if virtual scene is supported
     */
    public static boolean emulatorSupportsVirtualScene(
            @Nullable AndroidSdkHandler sdkHandler,
            @NonNull ProgressIndicator progressIndicator,
            @NonNull ILogger log) {
        return emulatorSupportsFeature(
                FEATURE_VIRTUAL_SCENE, sdkHandler, progressIndicator, log);
    }
}
