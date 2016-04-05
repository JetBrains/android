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
import com.android.tools.idea.editors.gfxtrace.service.snippets.CanFollow;
import com.android.tools.idea.editors.gfxtrace.service.snippets.Labels;
import com.android.tools.idea.editors.gfxtrace.service.snippets.SnippetObject;
import com.android.tools.rpclib.schema.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class Render {
  @NotNull private static final Logger LOG = Logger.getInstance(Render.class);
  // object rendering functions

  private static void render(@NotNull Object value, @NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
    if (value instanceof SnippetObject) {
      render((SnippetObject)value, component, attributes);
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

  public static void render(@NotNull SnippetObject obj, @NotNull Dynamic dynamic,
                            @NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
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
      render(obj.field(dynamic, index), dynamic.getFieldInfo(index).getType(), component, attributes);
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
    if (mpFields.length != fields.length) {
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

  public static void render(@NotNull StateController.Node node,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    if (node.key.type != null) {
      render(node.key.value, node.key.type, component, attributes);
    }
    else {
      component.append(String.valueOf(node.key.value.getObject()), attributes);
    }
    if (node.isLeaf() && node.value != null && node.value.value != null) {
      component.append(": ", attributes);
      if (node.value.value.getObject() != null) {
        render(node.value.value, node.value.type, component, SimpleTextAttributes.SYNTHETIC_ATTRIBUTES);
      }
      else {
        component.append("null");
      }
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
      SnippetObject paramValue = SnippetObject.param(atom, i);
      SimpleTextAttributes attr = paramAttributes(highlightedParameter, i, paramValue, attributes);
      component.append(field.getDeclared() + ":", attr, i);
      render(paramValue, field.getType(), component, attr);
    }

    component.append(")", SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES, -1);
    if (resultIndex >= 0) {
      SnippetObject paramValue = SnippetObject.param(atom, resultIndex);
      SimpleTextAttributes attr = paramAttributes(highlightedParameter, resultIndex, paramValue, attributes);
      component.append("->", attr, resultIndex);
      Field field = atom.getFieldInfo(resultIndex);
      render(paramValue, field.getType(), component, attr);
    }
  }

  private static boolean isValidParam(SnippetObject paramValue) {
    if (paramValue.getObject() instanceof Dynamic) {
      // Avoid highlighting pointers for invalid memory addresses. Note only affects rendering.
      Dynamic dyn = (Dynamic)paramValue.getObject();
      MemoryPointer mp = tryMemoryPointer(dyn);
      return mp == null || mp.isAddress();
    }
    return true;
  }

  private static boolean isHighlighted(int highlightedParameter, int i, SnippetObject paramValue) {
    return (i == highlightedParameter || CanFollow.fromSnippets(paramValue.getSnippets()) != null) && isValidParam(paramValue);
  }

  private static SimpleTextAttributes paramAttributes(int highlightedParameter, int i, SnippetObject paramValue, SimpleTextAttributes attributes) {
    return isHighlighted(highlightedParameter, i, paramValue) ? SimpleTextAttributes.LINK_ATTRIBUTES : attributes;
  }

  public static void render(@NotNull MemoryPointer pointer,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    if (!PoolID.ApplicationPool.equals(pointer.getPool())) {
      component.append("0x" + Long.toHexString(pointer.getAddress()), attributes);
      component.append("@", SimpleTextAttributes.GRAY_ATTRIBUTES);
      component.append(pointer.getPool().toString(), attributes);
    } else {
      if (!pointer.isAddress()) {
        // Not really an address, display a decimal.
        component.append(String.format("%d", pointer.getAddress()), attributes);
      } else {
        component.append("0x" + Long.toHexString(pointer.getAddress()), attributes);
      }
    }
  }

  public static void render(@NotNull MemoryRange range,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    component.append(Long.toString(range.getSize()), attributes);
    component.append(" bytes at ", SimpleTextAttributes.GRAY_ATTRIBUTES);
    component.append("0x" + Long.toHexString(range.getBase()), attributes);
  }

  public static void render(@NotNull SnippetObject value,
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

  public static void render(@NotNull SnippetObject value,
                            @NotNull Struct type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    render(value, component, attributes);
  }

  public static void render(@NotNull SnippetObject value,
                            @NotNull Pointer type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    component.append("*", SimpleTextAttributes.GRAY_ATTRIBUTES);
    render(value, component, attributes);
  }

  public static void render(@NotNull SnippetObject value,
                            @NotNull Interface type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    component.append("$", SimpleTextAttributes.GRAY_ATTRIBUTES);
    render(value, component, attributes);
  }

  public static void render(@NotNull SnippetObject obj,
                            @NotNull Array type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    Object value = obj.getObject();
    assert (value instanceof Object[]);
    render(obj, (Object[])value, type.getValueType(), component, attributes);
  }

  public static void render(@NotNull SnippetObject obj,
                            @NotNull Slice type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    Object value = obj.getObject();
    if (value instanceof Object[]) {
      render(obj, (Object[])value, type.getValueType(), component, attributes);
    } else if (value instanceof byte[]) {
      render(obj, (byte[])value, type.getValueType(), component, attributes);
    } else {
      assert (false);
    }
  }

  public static void render(@NotNull SnippetObject value,
                            @NotNull Map type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    render(value, component, attributes);
  }

  public static void render(@NotNull SnippetObject value,
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

  /**
   * Try to render a primitive value using it's constant name.
   *
   * @param obj the snippet object for the primitive value to render.
   * @param type the schema type of the primitive object.
   * @param component the component to render the constant into.
   * @param attributes text attributes to use during rendering.
   * @return true if obj was rendered as a constant, false means render underlying value.
   */
  public static boolean tryConstantRender(@NotNull SnippetObject obj,
                                          @NotNull Primitive type,
                                          @NotNull SimpleColoredComponent component,
                                          @NotNull SimpleTextAttributes attributes) {
    Constant value = findConstant(obj, type);
    if (value != null) {
      component.append(value.getName(), attributes);
      return true;
    }
    return false;
  }

  @Nullable("can't find a matching constant")
  public static Constant findConstant(@NotNull SnippetObject obj, @NotNull Primitive type) {
    final ConstantSet constants = ConstantSet.lookup(type);
    if (constants != null && constants.getEntries().length != 0) {
      List<Constant> byValue = constants.getByValue(obj.getObject());
      if (byValue != null && byValue.size() != 0) {
        if (byValue.size() == 1) {
          return byValue.get(0);
        }
        Labels labels = Labels.fromSnippets(obj.getSnippets());
        List<Constant> preferred;
        if (labels != null) {
          // There are label snippets, use them to disambiguate.
          preferred = labels.preferred(byValue);
          if (preferred.size() == 1) {
            return preferred.get(0);
          } else if (preferred.size() == 0) {
            // No matches, continue with the unfiltered constants.
            preferred = byValue;
          }
        } else {
          preferred = byValue;
        }
        // labels wasn't enough, try the heuristic.
        // Using an ambiguity threshold of 8. This side steps the most egregious misinterpretations.
        if (preferred.size() < 8) {
          return pickShortestName(preferred);
        }
        // Nothing worked we will show a numeric value.
      }
    }
    return null;
  }

  public static void render(@NotNull SnippetObject obj,
                            @NotNull Primitive type,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    if (tryConstantRender(obj, type, component, attributes)) {
      // successfully rendered as a constant.
      return;
    }

    Object value = obj.getObject();
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

  public static void render(@NotNull SnippetObject obj,
                            @NotNull Object[] array,
                            @NotNull Type valueType,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    int count = Math.min(array.length, MAX_DISPLAY);
    component.append("[", SimpleTextAttributes.GRAY_ATTRIBUTES);
    for (int index = 0; index < count; ++index) {
      if (index > 0) {
        component.append(",", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      render(obj.elem(array[index]), valueType, component, attributes);
    }
    if (count < array.length) {
      component.append("...", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  public static void render(@NotNull SnippetObject obj,
                            @NotNull byte[] array,
                            @NotNull Type valueType,
                            @NotNull SimpleColoredComponent component,
                            @NotNull SimpleTextAttributes attributes) {
    int count = Math.min(array.length, MAX_DISPLAY);
    component.append("[", SimpleTextAttributes.GRAY_ATTRIBUTES);
    for (int index = 0; index < count; ++index) {
      if (index > 0) {
        component.append(",", SimpleTextAttributes.GRAY_ATTRIBUTES);
      }
      render(obj.elem(array[index]), valueType, component, attributes);
    }
    if (count < array.length) {
      component.append("...", SimpleTextAttributes.GRAY_ATTRIBUTES);
    }
  }

  public static void render(@NotNull SnippetObject obj, @NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
    if (obj.getObject() instanceof Dynamic) {
      Dynamic dynamic = (Dynamic)obj.getObject();
      render(obj, dynamic, component, attributes);
      return;
    }
    render(obj.getObject(), component, attributes);
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
