/*
 * Copyright (C) 2025 The Android Open Source Project
 */
package android.xr.libxrinput;

import android.view.MotionEvent;

/** XR-specific service for injecting MotionEvents. */
interface IXRSimulatedInputEventManager {
    /** Injects a MotionEvent on Android XR device. */
    void injectXRSimulatedJavaMotionEvent(in MotionEvent motionEvent);
}
