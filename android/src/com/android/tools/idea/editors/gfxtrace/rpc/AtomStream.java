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

public class AtomStream implements BinaryObject {
  byte[] myData;
  SchemaId mySchema;

  // Constructs a default-initialized {@link AtomStream}.
  public AtomStream() {
  }

  // Constructs and decodes a {@link AtomStream} from the {@link Decoder} d.
  public AtomStream(Decoder d) throws IOException {
    decode(d);
  }

  public byte[] getData() {
    return myData;
  }

  public void setData(byte[] v) {
    myData = v;
  }

  public SchemaId getSchema() {
    return mySchema;
  }

  public void setSchema(SchemaId v) {
    mySchema = v;
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
    return ObjectFactory.AtomStreamID;
  }
}