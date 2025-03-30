/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit.analysis.leir

import org.jetbrains.org.objectweb.asm.Opcodes

enum class IrAccessFlag(val bitmask: Int) {
  PUBLIC(Opcodes.ACC_PUBLIC),
  PRIVATE(Opcodes.ACC_PRIVATE),
  PROTECTED(Opcodes.ACC_PROTECTED),
  STATIC(Opcodes.ACC_STATIC),
  FINAL(Opcodes.ACC_FINAL),
  SUPER(Opcodes.ACC_SUPER),
  SYNCHRONIZED(Opcodes.ACC_SYNCHRONIZED),
  VOLATILE(Opcodes.ACC_VOLATILE),
  BRIDGE(Opcodes.ACC_BRIDGE),
  VARARGS(Opcodes.ACC_VARARGS),
  TRANSIENT(Opcodes.ACC_TRANSIENT),
  NATIVE(Opcodes.ACC_NATIVE),
  INTERFACE(Opcodes.ACC_INTERFACE),
  ABSTRACT(Opcodes.ACC_ABSTRACT),
  STRICT(Opcodes.ACC_STRICT),
  SYNTHETIC(Opcodes.ACC_SYNTHETIC),
  ANNOTATION(Opcodes.ACC_ANNOTATION),
  ENUM(Opcodes.ACC_ENUM),
  DEPRECATED(Opcodes.ACC_DEPRECATED),
}

/**
 * Translates an ASM access bitmask to a set of [IrAccessFlag].
 */
fun parseAccess(access: Int): Set<IrAccessFlag> {
  val flags = mutableSetOf<IrAccessFlag>()
  for (flag in IrAccessFlag.values()) {
    if (access and flag.bitmask != 0) {
      flags.add(flag)
    }
  }
  return flags
}