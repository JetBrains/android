/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.perflib.heap.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class HeapDumpInstanceObjectTest {

  /**
   * Tests that FieldObjects are generated correctly based on a Hprof ClassInstance object.
   */
  @Test
  public void testExtractFieldsWithClassInstance() throws Exception {
    MockClassInstance classInstance = new MockClassInstance(-1, 0);
    classInstance.addFieldValue(Type.OBJECT, "objectTest", new MockClassInstance(-1, 0));
    classInstance.addFieldValue(Type.BOOLEAN, "boolTest", true);
    classInstance.addFieldValue(Type.CHAR, "charTest", 'a');
    classInstance.addFieldValue(Type.FLOAT, "floatTest", new Float(1f));
    classInstance.addFieldValue(Type.DOUBLE, "doubleTest", new Double(2.0));
    classInstance.addFieldValue(Type.BYTE, "byteTest", new Byte((byte)1));
    classInstance.addFieldValue(Type.SHORT, "shortTest", new Short((short)3));
    classInstance.addFieldValue(Type.INT, "intTest", new Integer(4));
    classInstance.addFieldValue(Type.LONG, "longTest", new Integer(5));

    List<FieldObject> fields = HeapDumpInstanceObject.extractFields(classInstance);
    assertEquals(9, fields.size());
    assertEquals("objectTest", fields.get(0).getFieldName());
    assertEquals(InstanceObject.ValueType.OBJECT, fields.get(0).getValueType());
    assertEquals("boolTest", fields.get(1).getFieldName());
    assertEquals(InstanceObject.ValueType.BOOLEAN, fields.get(1).getValueType());
    assertEquals("charTest", fields.get(2).getFieldName());
    assertEquals(InstanceObject.ValueType.CHAR, fields.get(2).getValueType());
    assertEquals("floatTest", fields.get(3).getFieldName());
    assertEquals(InstanceObject.ValueType.FLOAT, fields.get(3).getValueType());
    assertEquals("doubleTest", fields.get(4).getFieldName());
    assertEquals(InstanceObject.ValueType.DOUBLE, fields.get(4).getValueType());
    assertEquals("byteTest", fields.get(5).getFieldName());
    assertEquals(InstanceObject.ValueType.BYTE, fields.get(5).getValueType());
    assertEquals("shortTest", fields.get(6).getFieldName());
    assertEquals(InstanceObject.ValueType.SHORT, fields.get(6).getValueType());
    assertEquals("intTest", fields.get(7).getFieldName());
    assertEquals(InstanceObject.ValueType.INT, fields.get(7).getValueType());
    assertEquals("longTest", fields.get(8).getFieldName());
    assertEquals(InstanceObject.ValueType.LONG, fields.get(8).getValueType());
  }

  /**
   * Tests that FieldObjects are generated correctly based on a Hprof ArrayInstance object.
   */
  @Test
  public void testExtractFieldsWithArrayInstance() throws Exception {
    MockArrayInstance arrayInstance = new MockArrayInstance(-1, Type.OBJECT, 3, 0);
    arrayInstance.setValue(0, new MockClassInstance(-1, 0));
    arrayInstance.setValue(1, new MockClassInstance(-1, 0));
    arrayInstance.setValue(2, new MockClassInstance(-1, 0));

    List<FieldObject> fields = HeapDumpInstanceObject.extractFields(arrayInstance);
    assertEquals(3, fields.size());
    assertEquals("0", fields.get(0).getFieldName());
    assertEquals(InstanceObject.ValueType.OBJECT, fields.get(0).getValueType());
    assertEquals("1", fields.get(1).getFieldName());
    assertEquals(InstanceObject.ValueType.OBJECT, fields.get(1).getValueType());
    assertEquals("2", fields.get(2).getFieldName());
    assertEquals(InstanceObject.ValueType.OBJECT, fields.get(2).getValueType());
  }

  /**
   * Tests that FieldObjects are generated correctly based on a Hprof ClassObj object.
   */
  @Test
  public void testExtractFieldsWithClassObj() throws Exception {
    MockClassObj classObj = new MockClassObj(-1, "testClass", 0);
    classObj.addStaticField(Type.OBJECT, "staticObj", new MockClassInstance(-1, 0));
    classObj.addStaticField(Type.BOOLEAN, "staticBool", true);
    classObj.addStaticField(Type.CHAR, "staticChar", 'a');
    classObj.addStaticField(Type.FLOAT, "staticFloat", new Float(1f));
    classObj.addStaticField(Type.DOUBLE, "staticDouble", new Double(2.0));
    classObj.addStaticField(Type.BYTE, "staticByte", new Byte((byte)1));
    classObj.addStaticField(Type.SHORT, "staticShort", new Short((short)3));
    classObj.addStaticField(Type.INT, "staticInt", new Integer(4));
    classObj.addStaticField(Type.LONG, "staticLong", new Integer(5));

    List<FieldObject> fields = HeapDumpInstanceObject.extractFields(classObj);
    assertEquals(9, fields.size());
    assertEquals("staticObj", fields.get(0).getFieldName());
    assertEquals(InstanceObject.ValueType.OBJECT, fields.get(0).getValueType());
    assertEquals("staticBool", fields.get(1).getFieldName());
    assertEquals(InstanceObject.ValueType.BOOLEAN, fields.get(1).getValueType());
    assertEquals("staticChar", fields.get(2).getFieldName());
    assertEquals(InstanceObject.ValueType.CHAR, fields.get(2).getValueType());
    assertEquals("staticFloat", fields.get(3).getFieldName());
    assertEquals(InstanceObject.ValueType.FLOAT, fields.get(3).getValueType());
    assertEquals("staticDouble", fields.get(4).getFieldName());
    assertEquals(InstanceObject.ValueType.DOUBLE, fields.get(4).getValueType());
    assertEquals("staticByte", fields.get(5).getFieldName());
    assertEquals(InstanceObject.ValueType.BYTE, fields.get(5).getValueType());
    assertEquals("staticShort", fields.get(6).getFieldName());
    assertEquals(InstanceObject.ValueType.SHORT, fields.get(6).getValueType());
    assertEquals("staticInt", fields.get(7).getFieldName());
    assertEquals(InstanceObject.ValueType.INT, fields.get(7).getValueType());
    assertEquals("staticLong", fields.get(8).getFieldName());
    assertEquals(InstanceObject.ValueType.LONG, fields.get(8).getValueType());
  }

  /**
   * Tests that ReferenceObjects are generated correctly based on the hard+soft references of a hprof Instance object.
   * Note that as we cannot directly mock the {@link Instance} object, we use the MockClassInstance class here to allow us
   * to inject the hard + soft references.
   */
  @Test
  public void testExtractReferences() throws Exception {
    MockClassInstance mockInstance = new MockClassInstance(-1, 0);

    // Test valid/invalid reference case
    MockClassInstance hardInstanceRef = new MockClassInstance(-1, 3);
    hardInstanceRef.addFieldValue(Type.OBJECT, "hardInstanceRef", mockInstance);
    hardInstanceRef.addFieldValue(Type.OBJECT, "invalidRef", new Object());

    // Test multiple case
    MockArrayInstance hardArrayRef = new MockArrayInstance(-1, Type.OBJECT, 3, 2);
    hardArrayRef.setValue(0, new Object());
    hardArrayRef.setValue(1, mockInstance);
    hardArrayRef.setValue(2, mockInstance);

    // Test different type case
    MockClassObj hardClassRef = new MockClassObj(-1, "hardClassRef", 1);
    hardClassRef.addStaticField(Type.OBJECT, "staticClassRef", mockInstance);
    hardClassRef.addStaticField(Type.BOOLEAN, "invalidBoolRef", false);

    // Test soft references appear at end
    MockClassInstance softInstanceRef = new MockClassInstance(-1, 0);
    softInstanceRef.addFieldValue(Type.OBJECT, "softInstanceRef", mockInstance);
    softInstanceRef.addFieldValue(Type.OBJECT, "invalidRef", new Object());

    mockInstance.addHardReference(hardInstanceRef);
    mockInstance.addHardReference(hardArrayRef);
    mockInstance.addHardReference(hardClassRef);
    mockInstance.addSoftReferences(softInstanceRef);

    // extractReference is expected to return a list of sorted hard references first
    // then sorted soft references.
    List<ReferenceObject> referrers = HeapDumpInstanceObject.extractReferences(mockInstance);
    assertEquals(4, referrers.size());
    // The first object should refer to the hardClassRef which has the shortest distance to root.
    List<String> refs = referrers.get(0).getReferenceFieldNames();
    assertEquals(1, refs.size());
    assertEquals("staticClassRef", refs.get(0));
    // The second object should refer to hardArrayRef with two indices references
    refs = referrers.get(1).getReferenceFieldNames();
    assertEquals(2, refs.size());
    assertEquals("1", refs.get(0));
    assertEquals("2", refs.get(1));
    // The third object should refer to hardInstanceRef
    refs = referrers.get(2).getReferenceFieldNames();
    assertEquals(1, refs.size());
    assertEquals("hardInstanceRef", refs.get(0));
    // The fourth object should refer to softInstanceRef
    refs = referrers.get(3).getReferenceFieldNames();
    assertEquals(1, refs.size());
    assertEquals("softInstanceRef", refs.get(0));
  }

  private static class MockClassInstance extends ClassInstance {
    @Nullable private final ArrayList<Instance> mySoftReferences;
    @NotNull private final ArrayList<Instance> myHardReferences;
    @NotNull private final List<FieldValue> myFieldValues = new ArrayList<>();
    private final int myRootDistance;

    public MockClassInstance(int id, int rootDistance) {
      super(id, null /* StackTrace - don't care */, 0 /* valuesOffset - don't care */);
      myRootDistance = rootDistance;
      mySoftReferences = new ArrayList<>();
      myHardReferences = new ArrayList<>();
    }

    @Override
    public List<FieldValue> getValues() {
      return myFieldValues;
    }

    public void addFieldValue(@NotNull Type fieldType, @NotNull String fieldName, @Nullable Object value) {
      Field field = new Field(fieldType, fieldName);
      myFieldValues.add(new FieldValue(field, value));
    }

    @Override
    public int getDistanceToGcRoot() {
      return myRootDistance;
    }

    @Override
    public ArrayList<Instance> getSoftReverseReferences() {
      return mySoftReferences;
    }

    @Override
    public ArrayList<Instance> getHardReverseReferences() {
      return myHardReferences;
    }

    public void addSoftReferences(@NotNull Instance instance) {
      mySoftReferences.add(instance);
    }

    public void addHardReference(@NotNull Instance instance) {
      myHardReferences.add(instance);
    }

    @Override
    public ClassObj getClassObj() {
      return new MockClassObj(-1, "MockClass", 0);
    }
  }

  private static class MockArrayInstance extends ArrayInstance {
    @NotNull private final Object[] myArrayValues;
    private final int myRootDistance;

    public MockArrayInstance(int id, @NotNull Type arrayType, int length, int rootDistance) {
      super(id, null /* StackTrace - don't care */, arrayType, length, 0 /* valuesOffset - don't care */);
      myArrayValues = new Object[length];
      myRootDistance = rootDistance;
    }

    @Override
    public Object[] getValues() {
      return myArrayValues;
    }

    public void setValue(int index, @Nullable Object object) {
      assert index >= 0 && index < myArrayValues.length;
      myArrayValues[index] = object;
    }

    @Override
    public int getDistanceToGcRoot() {
      return myRootDistance;
    }
  }

  private static class MockClassObj extends ClassObj {
    // keep insertion order with LinkedHashMap so we have deterministic positions when validating the fields
    @NotNull private final Map<Field, Object> myStaticFields = new LinkedHashMap<>();
    private final int myRootDistance;

    public MockClassObj(int id, @NotNull String className, int rootDistance) {
      super(id, null /* StackTrace - don't care */, className, 0 /* offset - don't care */);
      myRootDistance = rootDistance;
    }

    @Override
    public Map<Field, Object> getStaticFieldValues() {
      return myStaticFields;
    }

    public void addStaticField(@NotNull Type fieldType, @NotNull String fieldName, @Nullable Object value) {
      myStaticFields.put(new Field(fieldType, fieldName), value);
    }

    @Override
    public int getDistanceToGcRoot() {
      return myRootDistance;
    }
  }
}