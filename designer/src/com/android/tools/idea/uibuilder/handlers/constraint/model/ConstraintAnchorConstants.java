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

public final class ConstraintAnchorConstants {
  /**
   * Type of creator
   */
  public static final int USER_CREATOR = 0;
  public static final int AUTO_CONSTRAINT_CREATOR = 2;

  /**
   * Define the type of anchor
   */
  public enum Type { NONE, LEFT, TOP, RIGHT, BOTTOM, BASELINE, CENTER, CENTER_X, CENTER_Y }

  /**
   * Define the strength of an anchor connection
   */
  public enum Strength { NONE, STRONG, WEAK }

  /**
   * Define the type of connection - either relaxed (allow +/- errors) or strict (only allow positive errors)
   */
  public enum ConnectionType { RELAXED, STRICT }
}
