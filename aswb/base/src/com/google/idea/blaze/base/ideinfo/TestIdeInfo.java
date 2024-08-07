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
package com.google.idea.blaze.base.ideinfo;

import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo.TestInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.dependencies.TestSize;
import java.util.Objects;
import javax.annotation.Nullable;

/** Test info. */
public final class TestIdeInfo implements ProtoWrapper<TestInfo> {
  private final TestSize testSize;

  private TestIdeInfo(TestSize testSize) {
    this.testSize = testSize;
  }

  static TestIdeInfo fromProto(TestInfo proto) {

    TestSize testSize = TestSize.fromString(proto.getSize());
    if (testSize == null) {
      testSize = TestSize.DEFAULT_RULE_TEST_SIZE;
    }
    return new TestIdeInfo(testSize);
  }

  @Override
  public TestInfo toProto() {
    return IntellijIdeInfo.TestInfo.newBuilder().setSize(testSize.toProto()).build();
  }

  public TestSize getTestSize() {
    return testSize;
  }

  @Nullable
  public static TestSize getTestSize(TargetIdeInfo target) {
    TestIdeInfo testIdeInfo = target.getTestIdeInfo();
    if (testIdeInfo == null) {
      return null;
    }
    return testIdeInfo.getTestSize();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Builder for test info */
  public static class Builder {
    private TestSize testSize = TestSize.DEFAULT_RULE_TEST_SIZE;

    @CanIgnoreReturnValue
    public Builder setTestSize(TestSize testSize) {
      this.testSize = testSize;
      return this;
    }

    public TestIdeInfo build() {
      return new TestIdeInfo(testSize);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TestIdeInfo that = (TestIdeInfo) o;
    return testSize == that.testSize;
  }

  @Override
  public int hashCode() {
    return Objects.hash(testSize);
  }
}
