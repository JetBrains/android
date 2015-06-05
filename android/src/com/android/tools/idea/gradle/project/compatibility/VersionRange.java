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
package com.android.tools.idea.gradle.project.compatibility;

import com.android.sdklib.repository.FullRevision;
import com.android.sdklib.repository.FullRevision.PreviewComparison;
import com.android.sdklib.repository.PreciseRevision;
import com.google.common.base.Splitter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A version range. It parses text in the following formats:
 * <ul>
 * <li>Version X to Y, with including and excluding endpoints (e.g. "[1, 10]", "(1, 10)", "[1, +)")</li>
 * <li>Version X or newer (e.g. "23+", which is equivalent to "[23, +)")</li>
 * <li>Version X (e,g, "1.0.0", "M")</li>
 * </ul>
 */
class VersionRange {
  @NonNls private static final char OR_GREATER = '+';
  @NonNls private static final char START_INCLUSIVE = '[';
  @NonNls private static final char START_EXCLUSIVE = '(';
  @NonNls private static final char END_INCLUSIVE = ']';
  @NonNls private static final char END_EXCLUSIVE = ')';

  @NotNull private final String myMinVersion;
  @Nullable private final FullRevision myMinRevision;
  private final boolean myMinVersionInclusive;

  @Nullable private final FullRevision myMaxRevision;
  @Nullable private final String myMaxVersion;
  private final boolean myMaxVersionInclusive;

  @NotNull
  static VersionRange parse(@NotNull String value) {
    if (value.isEmpty()) {
      throw new IllegalArgumentException("Empty string is not a valid value");
    }
    int size = value.length();
    char lastChar = value.charAt(size - 1);
    if (lastChar == OR_GREATER) {
      String minVersion = value.substring(0, size - 1);
      return new VersionRange(minVersion, true, null, false);
    }
    char firstChar = value.charAt(0);
    if (firstChar == START_INCLUSIVE || firstChar == START_EXCLUSIVE) {
      boolean minVersionInclusive = firstChar == START_INCLUSIVE;
      if (lastChar != END_INCLUSIVE && lastChar != END_EXCLUSIVE) {
        throw new IllegalArgumentException(String.format("Value '%1$s' should end with ']' or ')'", value));
      }
      boolean maxVersionInclusive = lastChar == END_INCLUSIVE;
      String rangeValue = value.substring(1, size - 1);
      List<String> values = Splitter.on(',').splitToList(rangeValue);
      if (values.size() != 2) {
        throw new IllegalArgumentException(String.format("Range '%1$s' should contain 2 values", value));
      }
      String minVersion = values.get(0).trim();
      String maxVersion = values.get(1).trim();
      if (maxVersion.length() == 1) {
        if (maxVersion.charAt(0) == OR_GREATER) {
          maxVersion = null;
        }
      }
      return new VersionRange(minVersion, minVersionInclusive, maxVersion, maxVersionInclusive);
    }
    return new VersionRange(value, false, null, false);
  }

  private VersionRange(@NotNull String minVersion, boolean minVersionInclusive, @Nullable String maxVersion, boolean maxVersionInclusive) {
    myMinVersion = minVersion;
    myMinRevision = asRevision(minVersion);
    myMinVersionInclusive = minVersionInclusive;
    myMaxVersion = maxVersion;
    myMaxRevision = asRevision(maxVersion);
    myMaxVersionInclusive = maxVersionInclusive;
  }

  boolean contains(@NotNull String value) {
    if (myMinRevision != null) {
      boolean contains = false;
      FullRevision revision = asRevision(value);
      if (revision != null) {
        if (myMinVersionInclusive) {
          contains = revision.compareTo(myMinRevision, PreviewComparison.IGNORE) >= 0;
        }
        else {
          contains = revision.compareTo(myMinRevision, PreviewComparison.IGNORE) > 0;
        }
        if (contains && myMaxRevision != null) {
          if (myMaxVersionInclusive) {
            contains = revision.compareTo(myMaxRevision, PreviewComparison.IGNORE) <= 0;
          }
          else {
            contains = revision.compareTo(myMaxRevision, PreviewComparison.IGNORE) < 0;
          }
        }
      }
      return contains;
    }
    return value.equals(myMinVersion) || value.equals(myMaxVersion);
  }

  @Nullable
  private static FullRevision asRevision(@Nullable String version) {
    if (version != null) {
      try {
        return PreciseRevision.parseRevision(version);
      }
      catch (NumberFormatException ignored) {
      }
    }
    return null;
  }

  @NotNull
  String getDescription() {
    if (myMinVersion.equals(myMaxVersion)) {
      return myMinVersion;
    }
    else if (myMaxVersion == null) {
      return String.format("%1$s (or newer)", myMinVersion);
    }
    return "versions " + myMinVersion + inclusiveness(myMinVersionInclusive) + " to " + myMaxVersion + inclusiveness(myMaxVersionInclusive);
  }

  private static String inclusiveness(boolean inclusive) {
    return "(" + (inclusive ? "inclusive" : "exclusive") + ")";
  }
}
