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
package com.android.tools.idea.editors.gfxtrace.service.stringtable;

import com.android.tools.rpclib.binary.BinaryObject;

import java.util.Map;

/**
 * Node is the abstract base class for all string table tree nodes.
 */
public abstract class Node implements BinaryObject {
  /**
   * Casts the {@link BinaryObject} to {@link Node}.
   * Will throw an exception if o is not a {@link Node}.
   */
  static public Node wrap(BinaryObject o) {
    return (Node)o;
  }

  /**
   * Returns the {@link BinaryObject} interface of this {@link Node}.
   */
  public BinaryObject unwrap() {
    return this;
  }

  /**
   * Returns the node as a string without any rich-formatting.
   */
  public abstract String getString(Map<String, BinaryObject> arguments);
}
