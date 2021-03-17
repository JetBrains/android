/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.properties

enum class PropertyType {
  STRING,
  BOOLEAN,
  BYTE,
  CHAR,
  DOUBLE,
  FLOAT,
  INT16,
  INT32,
  INT64,
  OBJECT,
  COLOR,
  GRAVITY,
  INT_ENUM,
  INT_FLAG,
  RESOURCE,
  DRAWABLE,
  ANIM,
  ANIMATOR,
  INTERPOLATOR,
  DIMENSION,
  DIMENSION_FLOAT,
  DIMENSION_DP,
  DIMENSION_SP,
  DIMENSION_EM,
  LAMBDA,
  FUNCTION_REFERENCE,
  ITERABLE,
  SHOW_MORE_LINK,
}