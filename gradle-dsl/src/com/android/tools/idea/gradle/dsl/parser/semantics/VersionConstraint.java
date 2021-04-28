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
package com.android.tools.idea.gradle.dsl.parser.semantics;

import com.android.annotations.Nullable;
import com.android.ide.common.repository.GradleVersion;
import com.android.ide.common.repository.GradleVersionRange;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * This class represents a constraint on the validity of something -- for example, a description of
 * a Dsl property and how it is represented in a supported language -- based on information about the
 * versions of relevant pieces of software.
 *
 * For a given piece of software, the VersionConstraint expresses the non-empty union of intervals in
 * (assumed monotonically-increasing) version space for which the software should be considered to be
 * acceptable.  Those intervals are expressed as half-open (inclusive lower bound, exclusive upper bound),
 * are non-overlapping, and are sorted in increasing order.  An absence of VersionConstraint (completely,
 * or for a particular piece of software) is equivalent to a VersionConstraint of one interval with both
 * end-points null (i.e. all versions are acceptable); a null software version is considered to be as far
 * in the future as is known (i.e. is acceptable to all VersionConstraints with a null upper end-point in
 * its last interval).
 *
 * Note that at present the constraints on the validity of the VersionConstraint are enforced implicitly by
 * having no way of constructing an invalid VersionConstraint.
 *
 * At present, the only piece of software we consider is the Android Gradle Plugin.
 */
public class VersionConstraint {
  @Nullable private List<VersionInterval> agpVersion = null;

  VersionConstraint() {
  }

  public static VersionConstraint agpBefore(@NotNull GradleVersion agpVersion) {
    VersionConstraint versionConstraint = new VersionConstraint();
    versionConstraint.agpVersion = Collections.singletonList(new VersionInterval(null, agpVersion));
    return versionConstraint;
  }

  public static VersionConstraint agpBefore(@NotNull String agpVersion) {
    return agpBefore(GradleVersion.parse(agpVersion));
  }

  public static VersionConstraint agpFrom(@NotNull GradleVersion agpVersion) {
    VersionConstraint versionConstraint = new VersionConstraint();
    versionConstraint.agpVersion = Collections.singletonList(new VersionInterval(agpVersion, null));
    return versionConstraint;
  }

  public static VersionConstraint agpFrom(@NotNull String agpVersion) {
    return agpFrom(GradleVersion.parse(agpVersion));
  }

  public boolean isOkWith(@Nullable GradleVersion agpVersion) {
    if (this.agpVersion == null) {
      return true;
    }
    else {
      for (VersionInterval interval : this.agpVersion) {
        if (interval.contains(agpVersion)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VersionConstraint that = (VersionConstraint)o;
    return Objects.equals(agpVersion, that.agpVersion);
  }

  @Override
  public int hashCode() {
    return Objects.hash(agpVersion);
  }
}

/**
 * Similar to {@link GradleVersionRange} but admits a null lower bound, and is not intended for parsing.
 */
class VersionInterval {
  @Nullable private final GradleVersion min;
  @Nullable private final GradleVersion max;

  VersionInterval(@Nullable GradleVersion min, @Nullable GradleVersion max) {
    this.min = min;
    this.max = max;
  }

  public boolean contains(@Nullable GradleVersion query) {
    if (query == null) return this.max == null;
    return (this.min == null || this.min.compareTo(query) <= 0) && (this.max == null || this.max.compareTo(query) > 0);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    VersionInterval interval = (VersionInterval)o;
    return Objects.equals(min, interval.min) &&
           Objects.equals(max, interval.max);
  }

  @Override
  public int hashCode() {
    return Objects.hash(min, max);
  }
}
