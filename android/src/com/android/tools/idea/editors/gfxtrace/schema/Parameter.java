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

import com.android.tools.idea.editors.gfxtrace.rpc.ParameterInfo;

/**
 * A parameter instance unpacked using a schema description.
 */
public class Parameter {
  public final ParameterInfo info;
  public final Object value;

  public Parameter(ParameterInfo info, Object value) {
    this.info = info;
    this.value = value;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(info.getName());
    sb.append(": ");
    sb.append(value.toString());
    return sb.toString();
  }
}
