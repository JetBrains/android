/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.common.artifact;

import com.google.auto.value.AutoValue;
import java.io.BufferedInputStream;
import java.io.IOException;
import javax.annotation.Nullable;

@AutoValue
public abstract class TestOutputArtifact implements OutputArtifact {

  public static final TestOutputArtifact EMPTY =
      new AutoValue_TestOutputArtifact.Builder()
          .setLength(0)
          .setDigest("digest")
          .setBazelOutRelativePath("path/file")
          .setConfigurationMnemonic("mnemonic")
          .build();

  public static TestOutputArtifact forDigest(String digest) {
    return EMPTY.toBuilder().setDigest(digest).build();
  }

  public static Builder builder() {
    return EMPTY.toBuilder();
  }

  @Override
  public abstract long getLength();

  @Override
  public BufferedInputStream getInputStream() throws IOException {
    throw new UnsupportedOperationException();
  }

  @Override
  public abstract String getDigest();

  @Override
  public abstract String getBazelOutRelativePath();

  @Override
  public abstract String getConfigurationMnemonic();

  @Nullable
  @Override
  public ArtifactState toArtifactState() {
    throw new UnsupportedOperationException();
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setLength(long value);

    public abstract Builder setDigest(String value);

    public abstract Builder setBazelOutRelativePath(String value);

    public abstract Builder setConfigurationMnemonic(String value);

    public abstract TestOutputArtifact build();
  }
}
