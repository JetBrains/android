// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.android.tools.profilers.cpu.nodemodel;

import com.android.tools.profilers.cpu.CaptureNode;
import org.jetbrains.annotations.NotNull;

/**
 * Provides accessors to the basic attributes of data represented by {@link CaptureNode} (e.g. Java methods, native functions, etc.).
 *
 * TODO(b/69904551): MethodModel sound too specific for Java methods. Rename this interface to something like CaptureNodeModel
 */
public interface MethodModel {

  @NotNull
  String getName();

  @NotNull
  String getFullName();

  @NotNull
  String getId();
}
