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

import org.jetbrains.annotations.NotNull;

/**
 * Model that represents instructions that don't have a corresponding symbol in an ELF file.
 * Such models are represented by the file name where the instruction comes from followed by their virtual address on the file.
 */
public class NoSymbolModel extends NativeNodeModel {

  private static final String KERNEL_ELF = "[kernel.kallsyms]";

  /**
   * Whether the instruction is sampled from the kernel, consequently coming from the pseudo ELF image [kernel.kallsyms].
   */
  private boolean myKernel;

  public NoSymbolModel(@NotNull String name) {
    myName = name;
    myKernel = name.startsWith(KERNEL_ELF);
  }

  public boolean isKernel() {
    return myKernel;
  }
}
