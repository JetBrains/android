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
package com.android.tools.idea.editors.gfxtrace.service.vertex;

import com.android.tools.rpclib.binary.BinaryObject;

/**
 * Format is the abstract base class extended by all vertex formats.
 */
public abstract class Format implements BinaryObject {
  /**
   * Casts the {@link BinaryObject} to {@link Format}.
   * Will throw an exception if o is not a {@link Format}.
   */
  static public Format wrap(BinaryObject o) {
    return (Format)o;
  }

  /**
   * Returns the {@link BinaryObject} interface of this {@link Format}.
   */
  public BinaryObject unwrap() {
    return this;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }
}
