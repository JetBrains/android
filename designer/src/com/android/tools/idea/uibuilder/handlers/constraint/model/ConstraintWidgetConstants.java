/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.constraint.model;

public final class ConstraintWidgetConstants {
  public static final int MATCH_CONSTRAINT_SPREAD = 0;
  public static final int UNKNOWN = -1;
  public static final int HORIZONTAL = 0;
  public static final int VERTICAL = 1;
  public static final int VISIBLE = 0;
  public static final int INVISIBLE = 4;
  public static final int GONE = 8;
  // Values of the chain styles
  public static final int CHAIN_SPREAD = 0;
  // Percentages used for biasing one connection over another when dual connections
  // of the same strength exist
  public static final float DEFAULT_BIAS = 0.5f;

  private ConstraintWidgetConstants() { }
}
