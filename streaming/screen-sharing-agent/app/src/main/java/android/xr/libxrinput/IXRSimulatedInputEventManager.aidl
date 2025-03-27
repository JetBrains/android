/*
 * Copyright (C) 2025 The Android Open Source Project
 */
package android.xr.libxrinput;

// TODO: Figure out how to make aidl_library accept this file when building in Bazel.
//       and move it the the aidl directory.
import android.view.MotionEvent;

interface IXRSimulatedInputEventManager {
    /**
     * This is Android XR specific non-standard extension which injects XR
     * related input events into a device which accepting simulated inputs.
     *
     * The support for this interface may not last long.
     *
     * Note: There is a similar and more general purpose interface called
     * IInputFlinger::injectCPMMotionEvent.
     */
    void injectXRSimulatedMotionEvent(in MotionEvent motionEvent);
}
