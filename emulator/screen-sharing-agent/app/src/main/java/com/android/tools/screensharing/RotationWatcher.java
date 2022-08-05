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
package com.android.tools.screensharing;

import android.view.IRotationWatcher;

/**
 * Simple implementation of the {@link IRotationWatcher} interface delegating to the native code
 * in window_manager.cc.
 */
public class RotationWatcher extends IRotationWatcher.Stub {
  @Override
  public native void onRotationChanged(int rotation);
}
