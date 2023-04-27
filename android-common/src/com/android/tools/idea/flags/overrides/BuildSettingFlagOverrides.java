package com.android.tools.idea.flags.overrides;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.flags.Flag;
import com.android.flags.ImmutableFlagOverrides;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.diagnostic.Logger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/* Build-time flag overrides that are stored in resource files. */
public final class BuildSettingFlagOverrides implements ImmutableFlagOverrides {
  private static final Logger LOG = Logger.getInstance(BuildSettingFlagOverrides.class);

  /**
   * Creates a new BuildSettingFlagOverrides from resources.
   */
  public static BuildSettingFlagOverrides create() {
    return create(BuildFlags.getResourceInputStream());
  }

  @VisibleForTesting
  static BuildSettingFlagOverrides create(Optional<InputStream> is) {
    try {
      Map<String, String> m = readResourceLines(is);
      return new BuildSettingFlagOverrides(m);
    }
    catch (IOException e) {
      LOG.warn("failed reading flags from resources", e);
    }
    return new BuildSettingFlagOverrides(Collections.emptyMap());
  }

  private static Map<String, String> readResourceLines(Optional<InputStream> is) throws IOException {
    if (is.isEmpty()) {
      return Collections.emptyMap();
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(is.get(), StandardCharsets.UTF_8));
    return reader.lines()
      .map(line -> line.split("="))
      .filter(strArray -> strArray.length > 1 && strArray[1] != "")
      .collect(Collectors.toMap(strArray -> strArray[0], strArray -> strArray[1]));
  }

  private final Map<String, String> overrides;

  private BuildSettingFlagOverrides(Map<String, String> map) {
    this.overrides = map;
  }

  @Nullable
  public String get(@NonNull Flag<?> flag) {
    return overrides.get(flag.getId());
  }
}