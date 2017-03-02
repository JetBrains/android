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
package com.android.tools.profilers.memory.adapters;

import com.android.tools.perflib.heap.ClassInstance;
import com.android.tools.perflib.heap.Field;
import com.android.tools.perflib.heap.Type;
import org.junit.Test;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.*;

public class HeapDumpFieldObjectTest {
  @Test
  public void testConstructorWithObjectInstance() throws Exception {
    int parentDepth = 3;
    int childDepth = 6;
    MockClassInstance parentInstance = new MockClassInstance(-1, parentDepth, "MockClass1");
    MockClassInstance childInstance = new MockClassInstance(-2, childDepth, "MockClass2");
    ClassInstance.FieldValue field = new ClassInstance.FieldValue(new Field(Type.OBJECT, "field0"), childInstance);

    HeapDumpFieldObject fieldObject = new HeapDumpFieldObject(parentInstance, field, childInstance);
    assertEquals("field0", fieldObject.getFieldName());
    assertEquals(ClassObject.ValueType.OBJECT, fieldObject.getValueType());
    assertEquals(String.format(FieldObject.FIELD_DISPLAY_FORMAT, "field0", childInstance), fieldObject.getName());
    assertEquals(childDepth, fieldObject.getDepth());
    assertEquals(0, fieldObject.getShallowSize());
    assertEquals(0, fieldObject.getRetainedSize());
    assertEquals(childInstance.getClassObj().getClassName(), fieldObject.getClassName());
    assertFalse(fieldObject.getIsPrimitive());
  }

  @Test
  public void testConstructorWithNullInstance() throws Exception {
    int parentDepth = 3;
    MockClassInstance parentInstance = new MockClassInstance(-1, parentDepth, "MockClass1");
    ClassInstance.FieldValue field = new ClassInstance.FieldValue(new Field(Type.OBJECT, "field0"), null);

    HeapDumpFieldObject fieldObject = new HeapDumpFieldObject(parentInstance, field, null);
    assertEquals("field0", fieldObject.getFieldName());
    assertEquals(ClassObject.ValueType.NULL, fieldObject.getValueType());
    assertEquals(String.format(FieldObject.FIELD_DISPLAY_FORMAT, "field0", "{null}"), fieldObject.getName());
    assertEquals(Integer.MAX_VALUE, fieldObject.getDepth());
    assertEquals(0, fieldObject.getShallowSize());
    assertEquals(0, fieldObject.getRetainedSize());
    assertNull(fieldObject.getClassName());
    assertFalse(fieldObject.getIsPrimitive());
  }

  @Test
  public void testConstructorWithPrimitives() throws Exception {
    int parentDepth = 3;
    MockClassInstance parentInstance = new MockClassInstance(-1, parentDepth, "MockClass1");
    ClassInstance.FieldValue field = new ClassInstance.FieldValue(new Field(Type.BOOLEAN, "field0"), true);

    HeapDumpFieldObject fieldObject = new HeapDumpFieldObject(parentInstance, field, null);
    assertEquals("field0", fieldObject.getFieldName());
    assertEquals(ClassObject.ValueType.BOOLEAN, fieldObject.getValueType());
    assertEquals(String.format(FieldObject.FIELD_DISPLAY_FORMAT, "field0", true), fieldObject.getName());
    assertEquals(parentDepth, fieldObject.getDepth());
    assertEquals(Type.BOOLEAN.getSize(), fieldObject.getShallowSize());
    assertEquals(Type.BOOLEAN.getSize(), fieldObject.getRetainedSize());
    assertNull(fieldObject.getClassName());
    assertTrue(fieldObject.getIsPrimitive());
  }
}