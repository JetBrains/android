package com.google.idea.blaze.common.artifact;

import com.google.common.io.ByteSource;
import com.google.common.io.MoreFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

public class MockArtifact extends CachedArtifact {

  private final ByteSource source;

  public MockArtifact(ByteSource source) {
    super(Path.of("fake"));
    this.source = source;
  }

  @Override
  public ByteSource byteSource() {
    return source;
  }

  @Override
  public ZipFile openAsZipFile() throws IOException {
    Path tempFile = Files.createTempFile("MockArtifact", "zip");
    source.copyTo(MoreFiles.asByteSink(tempFile));
    return new ZipFile(tempFile.toFile());
  }
}
