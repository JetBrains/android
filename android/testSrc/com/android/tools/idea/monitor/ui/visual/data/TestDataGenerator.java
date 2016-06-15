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
package com.android.tools.idea.monitor.ui.visual.data;

/**
 * Interface to use when generating test data.
 * @param <T> The type of data to be returned when requested by the test framework.
 */
public interface TestDataGenerator<T> {

  /**
   * Access generated data at specified index.
   */
  T get(int index);

  /**
   * Generate data, this will be called for each DataGenerator to signal new data should be created and stored internally.
   */
  void generateData();
}
