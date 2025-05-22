package com.google.idea.blaze.base.artifact;

import com.google.auto.value.AutoValue;
import com.google.idea.blaze.common.artifact.ArtifactState;
import com.google.idea.blaze.common.artifact.OutputArtifact;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

@AutoValue
public abstract class TestOutputArtifact implements OutputArtifact {

  public static final TestOutputArtifact EMPTY =
    new AutoValue_TestOutputArtifact.Builder()
      .setLength(0)
      .setDigest("digest")
      .setArtifactPath(Path.of("path/file"))
      .setArtifactPathPrefixLength(0)
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
  public abstract Path getArtifactPath();

  @Override
  public abstract int getArtifactPathPrefixLength();

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

    public abstract Builder setArtifactPath(Path value);

    public abstract Builder setArtifactPathPrefixLength(int value);

    public abstract TestOutputArtifact build();
  }
}
