/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.bazel;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ComparisonChain;
import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.intellij.openapi.util.text.StringUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Bazel version */
public class BazelVersion implements ProtoWrapper<ProjectData.BazelVersion> {
  static final BazelVersion DEVELOPMENT = new BazelVersion(999, 999, 999);
  private static final Pattern PATTERN = Pattern.compile("([[0-9]\\.]+)");

  private final int major;
  private final int minor;
  private final int bugfix;

  public BazelVersion(int major, int minor, int bugfix) {
    this.bugfix = bugfix;
    this.minor = minor;
    this.major = major;
  }

  public static BazelVersion fromProto(ProjectData.BazelVersion proto) {
    return new BazelVersion(proto.getMajor(), proto.getMinor(), proto.getBugfix());
  }

  @Override
  public ProjectData.BazelVersion toProto() {
    return ProjectData.BazelVersion.newBuilder()
        .setMajor(major)
        .setMinor(minor)
        .setBugfix(bugfix)
        .build();
  }

  int getMajor() {
    return major;
  }

  int getMinor() {
    return minor;
  }

  int getBugfix() {
    return bugfix;
  }

  @VisibleForTesting
  public static BazelVersion parseVersion(@Nullable String string) {
    // treat all unknown / development versions as the very latest version
    if (string == null) {
      return DEVELOPMENT;
    }
    Matcher matcher = PATTERN.matcher(string);
    if (!matcher.find()) {
      return DEVELOPMENT;
    }
    try {
      BazelVersion version = parseVersion(matcher.group(1).split("\\."));
      if (version == null) {
        return DEVELOPMENT;
      }
      return version;
    } catch (Exception e) {
      return DEVELOPMENT;
    }
  }

  @Nullable
  private static BazelVersion parseVersion(String[] numbers) {
    if (numbers.length < 1) {
      return null;
    }
    int major = StringUtil.parseInt(numbers[0], -1);
    if (major < 0) {
      return null;
    }
    int minor = numbers.length > 1 ? StringUtil.parseInt(numbers[1], 0) : 0;
    int bugfix = numbers.length > 2 ? StringUtil.parseInt(numbers[2], 0) : 0;
    return new BazelVersion(major, minor, bugfix);
  }

  static BazelVersion parseVersion(BlazeInfo blazeInfo) {
    return parseVersion(blazeInfo.get(BlazeInfo.RELEASE));
  }

  @Override
  public String toString() {
    return String.format("%s.%s.%s", major, minor, bugfix);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BazelVersion that = (BazelVersion) o;
    return major == that.major && minor == that.minor && bugfix == that.bugfix;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(major, minor, bugfix);
  }

  public boolean isAtLeast(BazelVersion version) {
    return isAtLeast(version.major, version.minor, version.bugfix);
  }

  public boolean isAtLeast(int major, int minor, int bugfix) {
    return ComparisonChain.start()
            .compare(this.major, major)
            .compare(this.minor, minor)
            .compare(this.bugfix, bugfix)
            .result()
        >= 0;
  }
}
