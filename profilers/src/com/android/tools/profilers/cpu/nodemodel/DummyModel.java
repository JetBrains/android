/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.cpu.nodemodel;

import com.android.tools.profilers.cpu.CaptureNode;
import com.google.common.annotations.VisibleForTesting;
import org.jetbrains.annotations.NotNull;

/**
 * Model to represent nodes on a {@link CaptureNode} tree that don't need to store any relevant information.
 * For example, fake nodes created to represent the root of bottom-up trees.
 */
public class DummyModel implements MethodModel {
  @VisibleForTesting
  static final String DUMMY_NODE = "Dummy Node";

  @Override
  @NotNull
  public String getName() {
    return DUMMY_NODE;
  }

  @Override
  @NotNull
  public String getFullName() {
    return DUMMY_NODE;
  }

  @Override
  @NotNull
  public String getId() {
    return DUMMY_NODE;
  }
}
