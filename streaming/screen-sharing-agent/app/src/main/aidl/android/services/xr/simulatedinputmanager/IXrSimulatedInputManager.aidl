/*
 * Copyright (C) 2024 The Android Open Source Project
 */
package android.services.xr.simulatedinputmanager;

interface IXrSimulatedInputManager {
    // Name of the default xr simulated input manager service
    const String DEFAULT_SERVICE_NAME = "xrsimulatedinputmanager";

    // Unused methods are replaced by placeholders to preserve generated ids of the subsequent methods.
    void placeholder1();
    void placeholder2();
    void placeholder3();
    void placeholder4();
    void placeholder5();

    void injectHeadRotation(in float[3] data);
    void injectHeadMovement(in float[3] data);
    void injectHeadAngularVelocity(in float[3] data);
    void injectHeadMovementVelocity(in float[3] data);
}
