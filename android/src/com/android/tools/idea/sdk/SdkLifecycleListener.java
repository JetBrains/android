/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import com.android.annotations.NonNull;
import com.intellij.util.messages.Topic;
import org.jetbrains.android.sdk.AndroidSdkData;

public interface SdkLifecycleListener {
  Topic<SdkLifecycleListener> TOPIC = Topic.create("Android SDK lifecycle notifications", SdkLifecycleListener.class);

  void localSdkLoaded (@NonNull AndroidSdkData sdkData);
  void remoteSdkLoaded(@NonNull AndroidSdkData sdkData);
  void updatesComputed(@NonNull AndroidSdkData sdkData);

  abstract class Adapter implements SdkLifecycleListener {
    @Override
    public void localSdkLoaded(@NonNull AndroidSdkData sdkData) {}

    @Override
    public void remoteSdkLoaded(@NonNull AndroidSdkData sdkData) {}

    @Override
    public void updatesComputed(@NonNull AndroidSdkData sdkData) {}
  }
}
