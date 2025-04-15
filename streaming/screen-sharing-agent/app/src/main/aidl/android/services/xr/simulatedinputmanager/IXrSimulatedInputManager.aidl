/*
 * Copyright (C) 2024 The Android Open Source Project
 */
package android.services.xr.simulatedinputmanager;

import android.services.xr.simulatedinputmanager.Environment;
import android.services.xr.simulatedinputmanager.IXrSimulatedInputStateCallback;

interface IXrSimulatedInputManager {
    // Unused methods are replaced by placeholders to preserve generated ids of the subsequent methods.
    void placeholder1();
    void placeholder2();
    void placeholder3();
    void placeholder4();
    void placeholder5();

    // Rotates head by [x, y, z] units
    void injectHeadRotation(in float[3] data);

    // Moves head by [x, y, z] units
    void injectHeadMovement(in float[3] data);

    // Sets head's angular velocity with [x, y, z] units
    void injectHeadAngularVelocity(in float[3] data);

    // Sets head's velocity with [x, y, z] units
    void injectHeadMovementVelocity(in float[3] data);

    void placeholder6();

    // Recenters head's position and rotation
    void recenter();

    // Set's passthrough state. 1.0 enables passthrough and 0.0 disables it.
    void setPassthroughCoefficient(in float coefficient);

    // Returns current passthrough coefficient.
    float getPassthroughCoefficient();

    // Set simulated enviroment in which device is placed
    void setEnvironment(in Environment environment);

    // Returns current simulated enviroment in which device is placed
    Environment getEnvironment();

    // Registers a callback that gets called when simulated environment changes
    void registerListener(IXrSimulatedInputStateCallback cb);

    // Unregisters callback
    void unRegisterListener(IXrSimulatedInputStateCallback cb);
}
