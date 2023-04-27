package com.android.tools.idea.flags.overrides;

import java.util.Optional;
import java.io.InputStream;
import com.google.common.annotations.VisibleForTesting;

/**
 * Loads dynamic build flags.
 */
public final class BuildFlags {
  private static final String RESOURCE_NAME = "/tools/adt/idea/studio/build-flags/build.flags";

  private BuildFlags() { }

  /**
   * Returns the optional build flags file stream.
   */
  public static Optional<InputStream> getResourceInputStream() {
    return getResourceInputStream(RESOURCE_NAME);
  }

  @VisibleForTesting
  static Optional<InputStream> getResourceInputStream(String resource) {
    InputStream is = BuildFlags.class.getResourceAsStream(resource);
    if (is == null) {
      return Optional.empty();
    }
    return Optional.of(is);
  }
}