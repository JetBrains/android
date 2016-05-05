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

package com.android.tools.adtui.model;

/**
 * Interface used by the some animation components to retrieve the latest value and range of a series
 */
public interface ReportingSeries {

  /**
   * This function should return the newest added value in a series. Implementors of this should
   * handle if the series has no values and return 0.
   * @return The most recently added value to a series
   */
  double getLatestValue();

  /**
   * This function should return the length of the range corresponding to the latest value.
   * @return The length of the range that is used for the series values.
   */
  double getRangeLength();
}
