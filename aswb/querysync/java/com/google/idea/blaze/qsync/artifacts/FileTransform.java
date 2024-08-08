package com.google.idea.blaze.qsync.artifacts;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.MoreFiles;
import com.google.idea.blaze.common.artifact.CachedArtifact;
import com.google.idea.blaze.exception.BuildException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public interface FileTransform {
  ImmutableSet<Path> copyWithTransform(CachedArtifact src, Path dest)
      throws BuildException, IOException;

  FileTransform COPY =
      (src, dest) -> {
        src.byteSource().copyTo(MoreFiles.asByteSink(dest));
        return ImmutableSet.of(dest);
      };

  FileTransform UNZIP = FileTransform::unzip;

  private static ImmutableSet<Path> unzip(CachedArtifact src, Path destination) throws IOException {
    Files.createDirectory(destination);
    ImmutableSet.Builder dests = ImmutableSet.builder();
    try (ZipFile zip = src.openAsZipFile()) {
      Enumeration<? extends ZipEntry> entries = zip.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        if (entry.isDirectory()) {
          Files.createDirectories(destination.resolve(entry.getName()));
        } else {
          // Srcjars do not contain separate directory entries
          Files.createDirectories(destination.resolve(entry.getName()).getParent());
          try (InputStream in = zip.getInputStream(entry)) {
            Files.copy(
                in, destination.resolve(entry.getName()), StandardCopyOption.REPLACE_EXISTING);
          }
          dests.add(destination.resolve(entry.getName()));
        }
      }
    }
    return dests.build();
  }
}
