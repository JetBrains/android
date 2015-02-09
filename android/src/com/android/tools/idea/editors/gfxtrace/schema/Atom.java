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

import com.android.tools.idea.editors.gfxtrace.rpc.AtomInfo;

/**
 * A single atom unpacked using a schema description.
 */
public class Atom {
  public final int contextId;
  public final AtomInfo info;
  public final Parameter[] parameters;

  public Atom(int contextId, AtomInfo info, Parameter[] parameters) {
    this.contextId = contextId;
    this.info = info;
    this.parameters = parameters;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(info.getName());
    sb.append("(");
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      sb.append(parameters[i].toString());
    }
    sb.append(")");
    return sb.toString();
  }
}
