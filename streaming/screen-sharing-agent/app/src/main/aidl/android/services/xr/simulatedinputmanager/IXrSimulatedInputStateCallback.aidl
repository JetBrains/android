/*
 * Copyright (C) 2025 The Android Open Source Project
 */
package android.services.xr.simulatedinputmanager;

import android.services.xr.simulatedinputmanager.Environment;

interface IXrSimulatedInputStateCallback {
    // Called on changes to passthrough coefficient.
    oneway void onPassthroughCoefficientChange(in float passthrough_coefficient);
    // Called on changes to simulated environment light settings
    oneway void onEnvironmentChange(in Environment environment);
}
