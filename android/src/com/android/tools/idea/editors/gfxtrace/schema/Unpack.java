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
 */
package com.android.tools.idea.editors.gfxtrace.schema;

import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.idea.editors.gfxtrace.rpc.*;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * A helper class to unpack schema defined types into Java objects.
 */
public class Unpack {
  @NotNull private static final Logger LOG = Logger.getInstance(Unpack.class.getName());

  public static Object Type(TypeInfo type, Decoder decoder) throws IOException {
    switch (type.getKind()) {
      case Bool:
        return decoder.bool();
      case S8:
        return decoder.int8();
      case U8:
        return decoder.uint8();
      case S16:
        return decoder.int16();
      case U16:
        return decoder.uint16();
      case S32:
        return decoder.int32();
      case U32:
        return decoder.uint32();
      case F32:
        return decoder.float32();
      case S64:
        return decoder.int64();
      case U64:
        return decoder.uint64();
      case F64:
        return decoder.float64();
      case String:
        return decoder.string();
      case Enum: {
        EnumInfo info = (EnumInfo)type;
        return new EnumValue(info, decoder.uint32());
      }
      case Struct: {
        StructInfo info = (StructInfo)type;
        Field[] fields = new Field[info.getFields().length];
        for (int i = 0; i < info.getFields().length; i++) {
          FieldInfo fieldInfo = info.getFields()[i];
          Object value = Type(fieldInfo.getType(), decoder);
          fields[i] = new Field(fieldInfo, value);
        }
        return new Struct(info, fields);
      }
      case Class: {
        ClassInfo info = (ClassInfo)type;
        Field[] fields = new Field[info.getFields().length];
        // TODO: Inherited fields
        for (int i = 0; i < info.getFields().length; i++) {
          FieldInfo fieldInfo = info.getFields()[i];
          Object value = Type(fieldInfo.getType(), decoder);
          fields[i] = new Field(fieldInfo, value);
        }
        return new Class(info, fields);
      }
      case Array: {
        ArrayInfo info = (ArrayInfo)type;
        int count = decoder.int32();
        Object[] elements = new Object[count];
        for (int i = 0; i < count; i++) {
          elements[i] = Type(info.getElementType(), decoder);
        }
        return new Array(info, elements);
      }
      case Map: {
        MapInfo info = (MapInfo)type;
        int count = decoder.int32();
        MapEntry[] elements = new MapEntry[count];
        for (int i = 0; i < count; i++) {
          Object key = Type(info.getKeyType(), decoder);
          Object value = Type(info.getValueType(), decoder);
          elements[i] = new MapEntry(key, value);
        }
        return new Map(info, elements);
      }
      case Pointer:
        return decoder.uint64();
      case Memory:
        return "<Memory>";
      case Any:
        LOG.error("'Any' type not yet implemented.");
        return "";
      case ID:
        byte[] b = new byte[20];
        decoder.read(b, 20);
        return b;
      default:
        throw new RuntimeException("Unknown kind " + type.getKind());
    }
  }
}
