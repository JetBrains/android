/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.android.filecache;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collection;
import java.util.List;

/** Data class for (de)serializing cache data */
public final class ArtifactCacheData {
  private final List<CacheEntry> cacheEntries;

  public ArtifactCacheData(Collection<CacheEntry> cacheEntries) {
    this.cacheEntries = ImmutableList.copyOf(cacheEntries);
  }

  public ImmutableList<CacheEntry> getCacheEntries() {
    return ImmutableList.copyOf(cacheEntries);
  }

  public void writeJson(OutputStream outputStream) throws IOException {
    Gson gson = new Gson();
    try (JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(outputStream, UTF_8))) {
      gson.toJson(this, ArtifactCacheData.class, jsonWriter);
    }
  }

  public static ArtifactCacheData readJson(InputStream inputStream) throws IOException {
    Gson gson = new Gson();
    try (JsonReader jsonReader = new JsonReader(new InputStreamReader(inputStream, UTF_8))) {
      return gson.fromJson(jsonReader, ArtifactCacheData.class);
    }
  }
}
