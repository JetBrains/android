/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.debug;

import com.android.support.AndroidxName;

public class SupportArrayMapRenderer extends ArrayMapRendererBase {
  static final AndroidxName ARRAY_MAP_NAME = new AndroidxName("android.support.v4.util.ArrayMap", "androidx.collection.ArrayMap");

  public SupportArrayMapRenderer() {
    super(ARRAY_MAP_NAME.oldName());
  }
}
