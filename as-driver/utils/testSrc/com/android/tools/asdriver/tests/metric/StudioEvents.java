/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.asdriver.tests.metric;

import com.google.protobuf.TextFormat;
import com.google.wireless.android.sdk.stats.AndroidStudioEvent;
import com.intellij.openapi.util.Pair;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class StudioEvents {

  @NotNull
  private final Path studioEventsDir;

  public StudioEvents(@NotNull final Path studioEventsDir) {
    this.studioEventsDir = studioEventsDir;
  }

  /**
   * Collects dumped studio_stats events with the specified {@param studioStatsEventKind}.
   **/
  public Stream<AndroidStudioEvent> get(@NotNull final String studioStatsEventKind) {

    File[] files = studioEventsDir.toFile().listFiles();
    if (files == null) {
      return Stream.empty();
    }
    return Arrays.stream(files).filter(f -> f.getName().startsWith(studioStatsEventKind)).map(f -> {
          AndroidStudioEvent.Builder builder = AndroidStudioEvent.newBuilder();
          try {
            TextFormat.merge(Files.readString(f.toPath()), builder);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          return new Pair<>(builder.build(), f.getName());
        }).sorted(Comparator.comparing(f -> f.getSecond())).map(e -> e.getFirst());
  }
}
