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

import com.android.tools.rpclib.binary.BinaryObject;
import com.android.tools.rpclib.binary.Decoder;
import com.android.tools.rpclib.binary.Encoder;
import com.android.tools.rpclib.binary.ObjectTypeID;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class Schema implements BinaryObject {
  ArrayInfo[] myArrays;
  StaticArrayInfo[] myStaticArrays;
  MapInfo[] myMaps;
  EnumInfo[] myEnums;
  StructInfo[] myStructs;
  ClassInfo[] myClasses;
  AtomInfo[] myAtoms;
  StructInfo myState;

  // Constructs a default-initialized {@link Schema}.
  public Schema() {
  }

  // Constructs and decodes a {@link Schema} from the {@link Decoder} d.
  public Schema(Decoder d) throws IOException {
    decode(d);
  }

  public ArrayInfo[] getArrays() {
    return myArrays;
  }

  public void setArrays(ArrayInfo[] v) {
    myArrays = v;
  }

  public StaticArrayInfo[] getStaticArrays() {
    return myStaticArrays;
  }

  public void setStaticArrays(StaticArrayInfo[] v) {
    myStaticArrays = v;
  }

  public MapInfo[] getMaps() {
    return myMaps;
  }

  public void setMaps(MapInfo[] v) {
    myMaps = v;
  }

  public EnumInfo[] getEnums() {
    return myEnums;
  }

  public void setEnums(EnumInfo[] v) {
    myEnums = v;
  }

  public StructInfo[] getStructs() {
    return myStructs;
  }

  public void setStructs(StructInfo[] v) {
    myStructs = v;
  }

  public ClassInfo[] getClasses() {
    return myClasses;
  }

  public void setClasses(ClassInfo[] v) {
    myClasses = v;
  }

  public AtomInfo[] getAtoms() {
    return myAtoms;
  }

  public void setAtoms(AtomInfo[] v) {
    myAtoms = v;
  }

  public StructInfo getState() {
    return myState;
  }

  public void setState(StructInfo v) {
    myState = v;
  }

  @Override
  public void encode(@NotNull Encoder e) throws IOException {
    ObjectFactory.encode(e, this);
  }

  @Override
  public void decode(@NotNull Decoder d) throws IOException {
    ObjectFactory.decode(d, this);
  }

  @Override
  public ObjectTypeID type() {
    return ObjectFactory.SchemaID;
  }
}