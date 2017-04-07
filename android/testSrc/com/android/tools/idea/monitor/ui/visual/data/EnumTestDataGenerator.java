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

import com.android.tools.adtui.model.SeriesData;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EnumTestDataGenerator<E extends Enum<E>> extends TestDataGenerator<E> {
  private List<E> mData = new ArrayList<>();
  private E[] mValues;

  public EnumTestDataGenerator(Class<E> clazz) {
    mValues = clazz.getEnumConstants();
  }

  @Override
  public SeriesData<E> get(int index) {
    return new SeriesData<>(mTime.get(index), mData.get(index));
  }

  @Override
  public void generateData() {
    mTime.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime()));
    mData.add(mValues[(int)(Math.random() * mValues.length)]);
  }
}
