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
import com.android.tools.idea.editors.gfxtrace.rpc.AtomInfo;
import com.android.tools.idea.editors.gfxtrace.rpc.AtomStream;
import com.android.tools.idea.editors.gfxtrace.rpc.ParameterInfo;
import com.android.tools.idea.editors.gfxtrace.rpc.Schema;
import gnu.trove.TIntIntHashMap;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A random-access reader of {@link Atom}s.
 * </p>
 * The {@link AtomReader} holds the entire collection of atoms in binary
 * packed form, and to reduce memory overhead, only unpacks these to Java
 * structures on {@link #read}.
 */
public class AtomReader {
  private final Schema mSchema;
  private final byte[] mData;
  private final List<Segment> mAtomSegments;
  private final TIntIntHashMap atomTypeToIndex;

  public AtomReader(AtomStream stream, Schema schema) throws IOException {
    mSchema = schema;
    mData = stream.getData();
    mAtomSegments = new ArrayList<Segment>();

    atomTypeToIndex = new TIntIntHashMap(schema.getAtoms().length);
    AtomInfo[] atomInfos = schema.getAtoms();
    for (int i = 0; i < atomInfos.length; ++i) {
      assert !atomTypeToIndex.containsValue(i); // Make sure there are no duplicates.
      atomTypeToIndex.put(atomInfos[i].getType(), i);
    }

    calculateAtomInfos();
  }

  /**
   * @return the number of atoms in the collection.
   */
  public int count() {
    return mAtomSegments.size();
  }

  private Atom unpack(Decoder decoder) throws IOException {
    short type = decoder.uint16();
    int contextId = decoder.int32();
    assert (contextId >= 0); // Sanity check.
    if (!atomTypeToIndex.containsKey(type)) {
      throw new RuntimeException("Atom type " + type + " not found in schema.");
    }
    AtomInfo atomInfo = mSchema.getAtoms()[atomTypeToIndex.get(type)];

    Parameter[] parameters = new Parameter[atomInfo.getParameters().length];
    for (int i = 0; i < parameters.length; i++) {
      ParameterInfo parameterInfo = atomInfo.getParameters()[i];
      Object value = Unpack.Type(atomInfo.getParameters()[i].getType(), decoder);
      parameters[i] = new Parameter(parameterInfo, value);
    }

    return new Atom(contextId, atomInfo, parameters);
  }

  /**
   * Unpack and return a single atom with the specified index.
   *
   * @param index the index of the atom.
   * @return the unpacked atom structure.
   */
  public Atom read(long index) throws IOException {
    assert (index <= Integer.MAX_VALUE);
    Segment segment = mAtomSegments.get((int)index);
    ByteArrayInputStream stream = new ByteArrayInputStream(mData, segment.mOffset, segment.mSize);
    Decoder decoder = new Decoder(stream);
    return unpack(decoder);
  }

  private void calculateAtomInfos() throws IOException {
    ByteArrayInputStream stream = new ByteArrayInputStream(mData);
    Decoder decoder = new Decoder(stream);
    int total = stream.available();
    long count = decoder.uint32();
    for (int i = 0; i < count; i++) {
      int remains = stream.available();
      int offset = total - remains;
      unpack(decoder);
      int size = remains - stream.available();
      mAtomSegments.add(new Segment(offset, size));
    }
  }

  private static class Segment {
    private final int mOffset;
    private final int mSize;

    private Segment(int offset, int size) {
      mOffset = offset;
      mSize = size;
    }
  }
}
