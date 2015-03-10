/*
* Copyright (C) 2015 The Android Open Source Project
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
*
* THIS WILL BE REMOVED ONCE THE CODE GENERATOR IS INTEGRATED INTO THE BUILD.
*/
package com.android.tools.idea.editors.gfxtrace.rpc;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public enum TypeKind {
  Bool(0),
  S8(1),
  U8(2),
  S16(3),
  U16(4),
  S32(5),
  U32(6),
  F32(7),
  S64(8),
  U64(9),
  F64(10),
  String(11),
  Enum(12),
  Struct(14),
  Class(15),
  Array(16),
  StaticArray(17),
  Map(18),
  Pointer(19),
  Memory(20),
  Any(21),
  ID(22);

  final int myValue;

  TypeKind(int value) {
    myValue = value;
  }

  public static TypeKind decode(@NotNull Decoder d) throws IOException {
    int id = d.int32();
    switch (id) {
      case 0:
        return Bool;
      case 1:
        return S8;
      case 2:
        return U8;
      case 3:
        return S16;
      case 4:
        return U16;
      case 5:
        return S32;
      case 6:
        return U32;
      case 7:
        return F32;
      case 8:
        return S64;
      case 9:
        return U64;
      case 10:
        return F64;
      case 11:
        return String;
      case 12:
        return Enum;
      case 14:
        return Struct;
      case 15:
        return Class;
      case 16:
        return Array;
      case 17:
        return StaticArray;
      case 18:
        return Map;
      case 19:
        return Pointer;
      case 20:
        return Memory;
      case 21:
        return Any;
      case 22:
        return ID;
      default:
        throw new RuntimeException("Unknown TypeKind " + id);
    }
  }

  public void encode(@NotNull Encoder e) throws IOException {
    e.int32(myValue);
  }
}