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
package com.android.tools.profilers.network;

import com.android.tools.adtui.model.Range;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

// TODO: This is not used and will be deleted, but temporarily useful for local testing.
public class FakeNetworkRequestsModel implements NetworkRequestsModel {
  @NotNull
  @Override
  public List<HttpData> getData(@NotNull Range timeCurrentRangeUs) {
    int size = (int)random(1, 20);
    List<HttpData> result = new ArrayList<>();
    long startTimeUs = (long)timeCurrentRangeUs.getMin();
    long endTimeUs = (long)timeCurrentRangeUs.getMax();
    for (int i = 0; i < size; ++i) {
      long start = random(startTimeUs, endTimeUs);
      long download = random(start, endTimeUs);
      long end = random(download, endTimeUs);
      HttpData data = new HttpData();
      data.setStartTimeUs(start);
      data.setDownloadingTimeUs(download);
      data.setEndTimeUs(end);
      data.setUrl("www.fake.url/" + i);
      result.add(data);
    }
    return result;
  }

  private static long random(long min, long max) {
    return min + (int)(Math.random() * ((max - min) + 1));
  }
}
