/*
 * Copyright (C) 2024 The Android Open Source Project
 */
package android.services.xr.simulatedinputmanager;

interface IXrSimulatedInputManager {
    void injectHeadRotation(in float[3] data);
    void injectHeadMovement(in float[3] data);
    void injectHeadAngularVelocity(in float[3] data);
    void injectHeadMovementVelocity(in float[3] data);
}
