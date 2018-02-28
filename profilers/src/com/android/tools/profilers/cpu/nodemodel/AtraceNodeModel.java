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
package com.android.tools.profilers.cpu.nodemodel;

import org.jetbrains.annotations.NotNull;

/**
 * This model contains the name of a trace slice as well as a flag that indicates
 * if this node represents the idle portion of a trace slice. If a slice has an idle
 * portion one slice is generated to represent the entire slice, and one slice is generated
 * to represent the idle portion of the slice.
 * Eg
 * | | = Slice Time
 * # = Idle Time
 * | Some Slice        ######|
 * We generate a node that represents the slice time.
 * | Some Slice (False )     |
 * We also generate a node that represents the idle time.
 *                    |(True)|
 * This allows us to change the rendering behavior of the nodes.
 * Note: The node doesn't care about how long the idle time is, it only cares about
 * if this node represents the idle portion of the slice.
 */
public class AtraceNodeModel extends SingleNameModel {

  private boolean myIsIdleCpu;

  public AtraceNodeModel(@NotNull String name) {
    this(name, false);
  }

  public AtraceNodeModel(@NotNull String name, boolean isIdleCpu) {
    super(name);
    myIsIdleCpu = isIdleCpu;
  }

  public boolean isIdleCpu() {
    return myIsIdleCpu;
  }
}
