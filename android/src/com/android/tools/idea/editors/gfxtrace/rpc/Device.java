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

public class Device implements BinaryObject {
  String Name;
  String Model;
  String OS;
  short PointerSize;
  short PointerAlignment;
  long MaxMemorySize;
  boolean RequiresShaderPatching;

  // Constructs a default-initialized {@link Device}.
  public Device() {
  }

  // Constructs and decodes a {@link Device} from the {@link Decoder} d.
  public Device(Decoder d) throws IOException {
    decode(d);
  }

  // Getters
  public String getName() {
    return Name;
  }

  // Setters
  public void setName(String v) {
    Name = v;
  }

  public String getModel() {
    return Model;
  }

  public void setModel(String v) {
    Model = v;
  }

  public String getOS() {
    return OS;
  }

  public void setOS(String v) {
    OS = v;
  }

  public short getPointerSize() {
    return PointerSize;
  }

  public void setPointerSize(short v) {
    PointerSize = v;
  }

  public short getPointerAlignment() {
    return PointerAlignment;
  }

  public void setPointerAlignment(short v) {
    PointerAlignment = v;
  }

  public long getMaxMemorySize() {
    return MaxMemorySize;
  }

  public void setMaxMemorySize(long v) {
    MaxMemorySize = v;
  }

  public boolean getRequiresShaderPatching() {
    return RequiresShaderPatching;
  }

  public void setRequiresShaderPatching(boolean v) {
    RequiresShaderPatching = v;
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
    return ObjectFactory.DeviceID;
  }
}