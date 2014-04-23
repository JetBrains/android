/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.stats;

import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/**
 * Records a collection a key-value strings statistics associated with a single build event.
 * Used by {@link StudioBuildStatsPersistenceComponent#addBuildRecord(BuildRecord)}.
 */
public class BuildRecord {
  private final long myUtcTimestampMs;
  @NotNull private final KeyString[] myData;

  /**
   * Creates a new {@link BuildRecord} with a collection of key-value pairs.
   * Timestamp is automatically set to "now".
   *
   * @param keyValuePairs A even-sized array of strings in the form [key1, value1,... keyN, valueN].
   * @throws IllegalArgumentException when keyValuePairs has an odd size.
   */
  public BuildRecord(String...keyValuePairs) {
    assert keyValuePairs.length % 2 == 0;
    if (keyValuePairs.length % 2 != 0) {
      throw new IllegalArgumentException("BuildRecord keyValuePairs should have an even length.");
    }

    KeyString[] ks = new KeyString[keyValuePairs.length / 2];
    for (int i = 0, j = 0; i < ks.length; i++, j += 2) {
      ks[i] = new KeyString(keyValuePairs[j], keyValuePairs[j+1]);
    }

    myUtcTimestampMs = utcNow();
    myData = ks;
  }

  /**
   * Creates a new {@link BuildRecord} with an array of {@link KeyString} elements.
   *
   * @param utcTimestampMs Event timestamp in milliseconds, UTC.
   *                       See {@link #utcNow()}.
   * @param data The key-value pairs associated with the build record.
   */
  public BuildRecord(long utcTimestampMs, @NotNull KeyString[] data) {
    myUtcTimestampMs = utcTimestampMs;
    myData = data;
  }

  /**
   * Creates a new {@link BuildRecord} with a collection of {@link KeyString} elements.
   *
   * @param utcTimestampMs Event timestamp in milliseconds, UTC.
   *                       See {@link #utcNow()}.
   * @param data The key-value pairs associated with the build record.
   */
  public BuildRecord(long utcTimestampMs, @NotNull List<KeyString> data) {
    myUtcTimestampMs = utcTimestampMs;
    myData = data.toArray(new KeyString[data.size()]);
  }

  /**
   * Returns the equivalent of {@link System#currentTimeMillis()} in the UTC timezone.
   * @return Now's timestamp, in milliseconds since the epoch, in the UTC timezone.
   */
  public static long utcNow() {
    Calendar c = Calendar.getInstance();
    c.setTimeZone(TimeZone.getTimeZone("GMT"));
    return c.getTimeInMillis();
  }

  /**
   * Returns the UTC timestamp recorded in this {@link BuildRecord}.
   * @return the UTC timestamp recorded in this {@link BuildRecord}.
   */
  public long getUtcTimestampMs() {
    return myUtcTimestampMs;
  }

  /**
   * Returns the array of {@link KeyString} element recorded in this {@link BuildRecord}.
   * @return the array of {@link KeyString} element recorded in this {@link BuildRecord}.
   */
  @NotNull
  public KeyString[] getData() {
    return myData;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    BuildRecord that = (BuildRecord)o;

    if (myUtcTimestampMs != that.myUtcTimestampMs) return false;
    if (!Arrays.equals(myData, that.myData)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (int)(myUtcTimestampMs ^ (myUtcTimestampMs >>> 32));
    result = 31 * result + Arrays.hashCode(myData);
    return result;
  }

  // For debugging purposes
  @Override
  public String toString() {
    return "BuildRecord{" +
           "myUtcTimestampMs=" + myUtcTimestampMs +
           ", myData=" + Arrays.toString(myData) +
           '}';
  }
}
