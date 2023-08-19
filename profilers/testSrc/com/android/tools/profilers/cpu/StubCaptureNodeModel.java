/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.profilers.cpu;

import com.android.tools.profilers.cpu.nodemodel.CaptureNodeModel;
import org.jetbrains.annotations.NotNull;

/**
 * Useful test class to satisfy {@link CaptureNode}'s requirement to be initialized with data for
 * tests that don't need to check it.
 */
public class StubCaptureNodeModel implements CaptureNodeModel {
  @NotNull
  @Override
  public String getName() {
    throw new UnsupportedOperationException();
  }

  /**
   * Return empty string so that tests that instatnitate the {@link CpuThreadTrackModel}
   * (which invokes the getFullName) do not throw an exception.
   */
  @NotNull
  @Override
  public String getFullName() {
    return "";
  }

  @NotNull
  @Override
  public String getId() {
    throw new UnsupportedOperationException();
  }
}
