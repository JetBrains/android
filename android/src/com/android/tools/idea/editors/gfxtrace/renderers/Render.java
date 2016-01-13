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

import java.util.List;

public final class Render {
  @NotNull private static final Logger LOG = Logger.getInstance(Render.class);
  // object rendering functions

  public static void render(@NotNull Object value, @NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
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
      render((DynamicAtom)value, component, attributes, -1);
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

  public static void render(@NotNull Dynamic dynamic, @NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
    MemoryPointer mp = tryMemoryPointer(dynamic);
    if (mp != null) {
      render(mp, component, attributes);
      return;
    }
    component.append("{", SimpleTextAttributes.GRAY_ATTRIBUTES);
    for (int index = 0; index < dynamic.getFieldCount(); ++index) {
      if (index > 0) {
        component.append(",", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      render(dynamic.getFieldValue(index), dynamic.getFieldInfo(index).getType(), component, attributes);
    }
    component.append("}", SimpleTextAttributes.GRAY_ATTRIBUTES);
  }

  /**
   * Tries to convert a dynamic to a memory pointer if the schema representation is compatible.
   * There are several aliases for Memory.Pointer which are unique types, but we want to render
   * them as pointers.
   *
   * @param dynamic object to attempt to convert to a memory pointer.
   * @return a memory pointer if the conversion is possible, otherwise null.
   */
  private static MemoryPointer tryMemoryPointer(Dynamic dynamic) {
    Entity entity = dynamic.klass().entity();
    Field[] fields = entity.getFields();
    MemoryPointer mp = new MemoryPointer();
    Field[] mpFields = mp.klass().entity().getFields();
    if (mpFields.length != fields.length || entity.getMetadata().length != 0) {
      return null;
    }
    for (int i = 0; i < fields.length; ++i) {
      if (!fields[i].equals(mpFields[i])) {
        return null;
      }
    }
    long address = ((Long)dynamic.getFieldValue(0)).longValue();
    PoolID poolId = PoolID.findOrCreate(((Number)dynamic.getFieldValue(1)).intValue());
    mp.setAddress(address);
    mp.setPool(poolId);
    return mp;
  }

  public static void render(@NotNull Field field, @NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
    component.append(field.getName(), attributes);
  }

  public static void render(@NotNull StateController.Node node,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    if (node.key.type != null) {
      render(node.key.value, node.key.type, component, attributes);
    }
    else {
      component.append(String.valueOf(node.key.value), attributes);
    }
    if (node.isLeaf() && node.value != null && node.value.value != null) {
      component.append(": ", attributes);
      render(node.value.value, node.value.type, component, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
    }
  }

  public static void render(@NotNull AtomController.Node node,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    render(node.index, component, attributes);
    if (node.atom != null) {
      component.append(": ", attributes);
      if (node.atom instanceof DynamicAtom) {
        render((DynamicAtom)node.atom, component, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES, node.hoveredParameter);
      }
      else {
        render(node.atom, component, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
      }
    }
  }

  public static void render(@NotNull AtomController.Memory memory,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    render(memory.isRead ? "read:" : "write:", component, attributes);
    render(memory.observation.getRange(), component, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
  }

  public static void render(@NotNull AtomController.Group group,
                            @NotNull final SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    component.append(group.group.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
  }

  public static void render(@NotNull DynamicAtom atom,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes,
                            int highlightedParameter) {
    // We append text segments with integer tags for dynamic atoms, so that we can map segments back to parameters for highlighting them
    // on hover. A tag of -1 means that the segment is not part of any parameter. A tag >= 0 means that the given segment is part of the
    // parameter which has an index equal to the tag. Since the actual rendering of the parameter is done elsewhere there are text segments
    // that are part of a parameter, but are un-tagged. To find the parameter mapping for those, the closest previous tagged segment will
    // give the mapping. Given the following text segments with tags, for example:
    //     "glFoo(":-1  "bar:":0  "barValue":null  ", ":-1  "baz:":1, "bazValue":null ")":-1
    // The "bar:" and "barValue" segments will map to parameter 0, while "baz:" and "bazValue" will map to 1.
    // See {@link #getAtomParameterIndex}.

    component.append(atom.getName() + "(", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, -1);
    int resultIndex = atom.getResultIndex();
    int extrasIndex = atom.getExtrasIndex();
    boolean needComma = false;
    for (int i = 0; i < atom.getFieldCount(); ++i) {
      if (i == resultIndex || i == extrasIndex) continue;
      Field field = atom.getFieldInfo(i);
      if (needComma) {
        component.append(", ", SimpleTextAttributes.REGULAR_ATTRIBUTES, -1);
      }
      needComma = true;
      Object parameterValue = atom.getFieldValue(i);
      component.append(field.getDeclared() + ":",
                       (i == highlightedParameter) ? SimpleTextAttributes.LINK_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES, i);
      render(parameterValue, field.getType(), component, (i == highlightedParameter) ? SimpleTextAttributes.LINK_ATTRIBUTES : attributes);
    }

    component.append(")", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, -1);
    if (resultIndex >= 0) {
      component.append("->", (resultIndex == highlightedParameter)
                             ? SimpleTextAttributes.LINK_ATTRIBUTES
                             : SimpleTextAttributes.REGULAR_ATTRIBUTES, resultIndex);
      Field field = atom.getFieldInfo(resultIndex);
      Object parameterValue = atom.getFieldValue(resultIndex);
      render(parameterValue, field.getType(), component, attributes);
    }
  }

  public static void render(@NotNull MemoryPointer pointer,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    component.append("0x" + Long.toHexString(pointer.getAddress()), attributes);
    if (!PoolID.ApplicationPool.equals(pointer.getPool())) {
      component.append("@", SimpleTextAttributes.GRAY_ATTRIBUTES);
      component.append(pointer.getPool().toString(), attributes);
    }
  }

  public static void render(@NotNull MemoryRange range,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    component.append(Long.toString(range.getSize()), attributes);
    component.append(" bytes at ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    component.append("0x" + Long.toHexString(range.getBase()), attributes);
  }


  // Type based rendering functions

  public static void render(@NotNull Object value,
                            @NotNull Type type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
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
                            @NotNull SimpleTextAttributes attributes) {
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Pointer type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    component.append("*", SimpleTextAttributes.GRAY_ATTRIBUTES);
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Interface type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    component.append("$", SimpleTextAttributes.GRAY_ATTRIBUTES);
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Array type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    assert (value instanceof Object[]);
    render((Object[])value, type.getValueType(), component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull Slice type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    if (value instanceof Object[]) {
      render((Object[])value, type.getValueType(), component, attributes);
    } else if (value instanceof byte[]) {
      render((byte[])value, type.getValueType(), component, attributes);
    } else {
      assert (false);
    }
  }

  public static void render(@NotNull Object value,
                            @NotNull Map type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    render(value, component, attributes);
  }

  public static void render(@NotNull Object value,
                            @NotNull AnyType type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    render(value, component, attributes);
  }

  private static Constant pickShortestName(List<Constant> constants) {
    int len = Integer.MAX_VALUE;
    Constant shortest = null;
    for (Constant constant : constants) {
      int l = constant.getName().length();
      if (l < len) {
        len = l;
        shortest = constant;
      }
    }
    return shortest;
  }

  public static void render(@NotNull Object value,
                            @NotNull Primitive type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    ConstantSet constants = ConstantSet.lookup(type);
    if (constants != null && constants.getEntries().length != 0) {
      List<Constant> byValue = constants.getByValue(value);
      // Use an ambiguity threshold of 8. This side steps the most egregious misinterpretations
      if (byValue != null && byValue.size() != 0 && byValue.size() < 8) {
        component.append(pickShortestName(byValue).getName(), attributes);
        return;
      }
    }

    // Note: casting to Number instead of Byte, Short, Integer, etc. in case the value was boxed into a different Number type.
    switch (type.getMethod().getValue()) {
      case Method.BoolValue:
        component.append(String.format("%b", (Boolean)value), attributes);
        return;
      case Method.Int8Value:
        component.append(String.format("%d", ((Number)value).byteValue()), attributes);
        return;
      case Method.Uint8Value:
        component.append(String.format("%d", ((Number)value).intValue() & 0xff), attributes);
        return;
      case Method.Int16Value:
        component.append(String.format("%d", ((Number)value).shortValue()), attributes);
        return;
      case Method.Uint16Value:
        component.append(String.format("%d", ((Number)value).intValue() & 0xffff), attributes);
        return;
      case Method.Int32Value:
        component.append(String.format("%d", ((Number)value).intValue()), attributes);
        return;
      case Method.Uint32Value:
        component.append(String.format("%d", ((Number)value).longValue() & 0xffffffffL), attributes);
        return;
      case Method.Int64Value:
        component.append(String.format("%d", ((Number)value).longValue()), attributes);
        return;
      case Method.Uint64Value:
        component.append(String.format("0x%s", Long.toHexString(((Number)value).longValue())), attributes);
        return;
      case Method.Float32Value:
        component.append(String.format("%f", ((Number)value).floatValue()), attributes);
        return;
      case Method.Float64Value:
        component.append(String.format("%f", ((Number)value).doubleValue()), attributes);
        return;
      case Method.StringValue:
        component.append(String.valueOf(value), attributes);
        return;
      default:
        component.append(value.toString(), attributes);
        break;
    }
  }


  private static final int MAX_DISPLAY = 3;

  public static void render(@NotNull Object[] array,
                            @NotNull Type valueType,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
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

  public static void render(@NotNull byte[] array,
                            @NotNull Type valueType,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
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

  /**
   * See {@link #render(DynamicAtom, SimpleColoredComponent, SimpleTextAttributes, int)}
   */
  public static int getAtomParameterIndex(@NotNull SimpleColoredComponent component, int x) {
    for (int index = component.findFragmentAt(x); index >= 2; index--) {
      Object tag = component.getFragmentTag(index);
      if (tag != null && tag instanceof Integer) {
        return (Integer)tag;
      }
    }
    return -1;
  }
}
