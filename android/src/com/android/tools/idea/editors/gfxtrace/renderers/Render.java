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
package com.android.tools.idea.editors.gfxtrace.renderers;

import com.android.tools.idea.editors.gfxtrace.controllers.AtomController;
import com.android.tools.idea.editors.gfxtrace.controllers.StateController;
import com.android.tools.idea.editors.gfxtrace.service.atom.DynamicAtom;
import com.android.tools.idea.editors.gfxtrace.service.memory.MemoryPointer;
import com.android.tools.idea.editors.gfxtrace.service.memory.MemoryRange;
import com.android.tools.idea.editors.gfxtrace.service.memory.PoolID;
import com.android.tools.rpclib.schema.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

public final class Render {
  @NotNull private static final Logger LOG = Logger.getInstance(Render.class);
  // object rendering functions

  public static void render(@NotNull Object value, @NotNull SimpleColoredComponent component, SimpleTextAttributes attributes) {
    if (value instanceof Dynamic) {
      render((Dynamic)value, component, attributes);
      return;
    }
    if (value instanceof Field) {
      render((Field)value, component, attributes);
      return;
    }
    if (value instanceof StateController.Node) {
      render((StateController.Node)value, component, attributes);
      return;
    }
    if (value instanceof StateController.Typed) {
      render((StateController.Typed)value, component, attributes);
      return;
    }
    if (value instanceof AtomController.Node) {
      render((AtomController.Node)value, component, attributes);
      return;
    }
    if (value instanceof AtomController.Memory) {
      render((AtomController.Memory)value, component, attributes);
      return;
    }
    if (value instanceof AtomController.Group) {
      render((AtomController.Group)value, component, attributes);
      return;
    }
    if (value instanceof DynamicAtom) {
      render((DynamicAtom)value, component, attributes);
      return;
    }
    if (value instanceof MemoryPointer) {
      render((MemoryPointer)value, component, attributes);
      return;
    }
    if (value instanceof MemoryRange) {
      render((MemoryRange)value, component, attributes);
      return;
    }
    component.append(value.toString(), attributes);
  }

  public static void render(@NotNull Dynamic dynamic, @NotNull SimpleColoredComponent component, SimpleTextAttributes attributes) {
    component.append("{", SimpleTextAttributes.GRAY_ATTRIBUTES);
    for (int index = 0; index < dynamic.getFieldCount(); ++index) {
      if (index > 0) {
        component.append(",", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      render(dynamic.getFieldValue(index), dynamic.getFieldInfo(index).getType(), component, attributes);
    }
    component.append("}", SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  public static void render(@NotNull Field field, @NotNull SimpleColoredComponent component, SimpleTextAttributes attributes) {
    component.append(field.getName(), attributes);
  }

  public static void render(@NotNull StateController.Node node,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    if (node.key != null) {
      render(node.key, component, attributes);
    }
    if (node.value != null) {
      component.append(": ", attributes);
      render(node.value, component, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
    }
  }

  public static void render(@NotNull StateController.Typed typed,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    render(typed.value, typed.type, component, attributes);
  }

  public static void render(@NotNull AtomController.Node node, @NotNull SimpleColoredComponent component, SimpleTextAttributes attributes) {
    render(node.index, component, attributes);
    if (node.atom != null) {
      component.append(": ", attributes);
      render(node.atom, component, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
    }
  }

  public static void render(@NotNull AtomController.Memory memory,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    render(memory.isRead ? "read:" : "write:", component, attributes);
    render(memory.observation.getRange(), component, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
  }

  public static void render(@NotNull AtomController.Group group,
                            @NotNull final SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    component.append(group.group.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  public static void render(@NotNull DynamicAtom atom, @NotNull SimpleColoredComponent component, SimpleTextAttributes attributes) {
    component.append(atom.getName() + "(", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    int resultIndex = atom.getResultIndex();
    int extrasIndex = atom.getExtrasIndex();
    boolean needComma = false;
    for (int i = 0; i < atom.getFieldCount(); ++i) {
      if (i == resultIndex || i == extrasIndex) continue;
      if (needComma) {
        component.append(", ", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
      needComma = true;
      Field field = atom.getFieldInfo(i);
      Object parameterValue = atom.getFieldValue(i);
      render(parameterValue, field.getType(), component, attributes);
    }

    component.append(")", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
    if (resultIndex >= 0) {
      component.append("->", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      Field field = atom.getFieldInfo(resultIndex);
      Object parameterValue = atom.getFieldValue(resultIndex);
      render(parameterValue, field.getType(), component, attributes);
    }
  }

  public static void render(@NotNull MemoryPointer pointer, @NotNull SimpleColoredComponent component, SimpleTextAttributes attributes) {
    component.append("0x" + Long.toHexString(pointer.getAddress()), attributes);
    if (pointer.getPool().value != PoolID.ApplicationPool) {
      component.append("@", SimpleTextAttributes.GRAY_ATTRIBUTES);
      component.append(pointer.getPool().toString(), attributes);
    }
  }

  public static void render(@NotNull MemoryRange range, @NotNull SimpleColoredComponent component, SimpleTextAttributes attributes) {
    component.append(Long.toString(range.getSize()), attributes);
    component.append(" bytes at ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    component.append("0x" + Long.toHexString(range.getBase()), attributes);
  }


  // Type based rendering functions

  public static void render(@NotNull Object value,
                            @NotNull Type type,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    if (type instanceof Primitive) {
      render(value, (Primitive)type, component, attributes);
      return;
    }
    if (type instanceof Struct) {
      render(value, (Struct)type, component, attributes);
      return;
    }
    if (type instanceof Pointer) {
      render(value, (Pointer)type, component, attributes);
      return;
    }
    if (type instanceof Interface) {
      render(value, (Interface)type, component, attributes);
      return;
    }
    if (type instanceof Array) {
      render(value, (Array)type, component, attributes);
      return;
    }
    if (type instanceof Slice) {
      render(value, (Slice)type, component, attributes);
      return;
    }
    if (type instanceof Map) {
      render(value, (Map)type, component, attributes);
      return;
    }
    if (type instanceof AnyType) {
      render(value, (AnyType)type, component, attributes);
      return;
    }
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Struct type,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Pointer type,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    component.append("*", SimpleTextAttributes.GRAY_ATTRIBUTES);
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Interface type,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    component.append("$", SimpleTextAttributes.GRAY_ATTRIBUTES);
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Array type,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    assert (value instanceof Object[]);
    render((Object[])value, type.getValueType(), component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Slice type,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    assert (value instanceof Object[]);
    render((Object[])value, type.getValueType(), component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Map type,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull AnyType type,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Primitive type,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    ConstantSet constants = ConstantSet.lookup(type);
    if (constants != null) {
      for (Constant constant : constants.getEntries()) {
        if (value.equals(constant.getValue())) {
          component.append(constant.getName(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
          return;
        }
      }
    }
    switch (type.getMethod().value) {
      case Method.Bool:
        component.append(String.format("%b", (Boolean)value), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Int8:
        component.append(String.format("%d", (Byte)value), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Uint8:
        component.append(String.format("%d", ((Byte)value).intValue() & 0xff), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Int16:
        component.append(String.format("%d", (Short)value), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Uint16:
        component.append(String.format("%d", ((Short)value).intValue() & 0xffff), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Int32:
        component.append(String.format("%d", (Integer)value), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Uint32:
        component.append(String.format("%d", ((Integer)value).longValue() & 0xffffffffL), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Int64:
        component.append(String.format("%d", (Long)value), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Uint64:
        component.append(String.format("0x%s", Long.toHexString((Long)value)), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Float32:
        component.append(String.format("%f", (Float)value), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.Float64:
        component.append(String.format("%f", (Double)value), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      case Method.String:
        component.append((String)value, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        return;
      default:
        component.append(value.toString(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
        break;
    }
  }


  private static final int MAX_DISPLAY = 3;

  public static void render(@NotNull Object[] array,
                            @NotNull Type valueType,
                            @NotNull SimpleColoredComponent component,
                            SimpleTextAttributes attributes) {
    int count = Math.min(array.length, MAX_DISPLAY);
    component.append("[", SimpleTextAttributes.GRAY_ATTRIBUTES);
    for (int index = 0; index < count; ++index) {
      if (index > 0) {
        component.append(",", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      render(array[index], valueType, component, attributes);
    }
    if (count < array.length) {
      component.append("...", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }
}
