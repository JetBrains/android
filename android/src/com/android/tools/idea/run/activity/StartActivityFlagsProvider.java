/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.run.activity;

import com.android.ddmlib.IDevice;
import org.jetbrains.annotations.NotNull;

/**
 * Wraps "am start" flags.
 * <p>
 * Start activity flags can be a combination of many different options: sources-debugger,
 * debugger state, run configuration, etc. This class encapsulates flags management so we
 * can hide details about these multiple sources.
 */
public interface StartActivityFlagsProvider {

  /**
   * Returns a concatenated list of flags and values. E.g. "-flag1=foo -flag2=bar.
   *
   * @param device The target device, should it affect the startup flags.
   */
  @NotNull
  String getFlags(@NotNull IDevice device);
}
