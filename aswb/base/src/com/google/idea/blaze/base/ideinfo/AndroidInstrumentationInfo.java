/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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

import com.google.common.base.Strings;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.model.primitives.Label;
import java.util.Objects;
import javax.annotation.Nullable;

/** Ide info specific to android instrumentation tests */
public class AndroidInstrumentationInfo
    implements ProtoWrapper<IntellijIdeInfo.AndroidInstrumentationInfo> {

  @Nullable
  public Label getTestApp() {
    return testApp;
  }

  @Nullable
  public Label getTargetDevice() {
    return targetDevice;
  }

  @Nullable private final Label testApp;
  @Nullable private final Label targetDevice;

  private AndroidInstrumentationInfo(@Nullable Label testApp, @Nullable Label targetDevice) {
    this.testApp = testApp;
    this.targetDevice = targetDevice;
  }

  static AndroidInstrumentationInfo fromProto(IntellijIdeInfo.AndroidInstrumentationInfo proto) {
    return new AndroidInstrumentationInfo(
        !Strings.isNullOrEmpty(proto.getTestApp()) ? Label.create(proto.getTestApp()) : null,
        !Strings.isNullOrEmpty(proto.getTargetDevice())
            ? Label.create(proto.getTargetDevice())
            : null);
  }

  @Override
  public IntellijIdeInfo.AndroidInstrumentationInfo toProto() {
    IntellijIdeInfo.AndroidInstrumentationInfo.Builder builder =
        IntellijIdeInfo.AndroidInstrumentationInfo.newBuilder();
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setTestApp, testApp);
    ProtoWrapper.unwrapAndSetIfNotNull(builder::setTargetDevice, targetDevice);
    return builder.build();
  }

  /** Builder for android instrumentation test rule */
  public static class Builder {
    private Label testApp;
    private Label targetDevice;

    @CanIgnoreReturnValue
    public Builder setTestApp(Label testApp) {
      this.testApp = testApp;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder setTargetDevice(Label targetDevice) {
      this.targetDevice = targetDevice;
      return this;
    }

    public AndroidInstrumentationInfo build() {
      return new AndroidInstrumentationInfo(testApp, targetDevice);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Builder)) {
        return false;
      }
      if (!(o instanceof AndroidInstrumentationInfo)) {
        return false;
      }
      AndroidInstrumentationInfo that = (AndroidInstrumentationInfo) o;
      return Objects.equals(testApp, that.getTestApp())
          && Objects.equals(targetDevice, that.getTargetDevice());
    }

    @Override
    public int hashCode() {
      return Objects.hash(testApp, targetDevice);
    }
  }
}
