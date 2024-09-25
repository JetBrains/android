/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.java.run.coverage;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import gnu.trove.TIntIntHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/** Blaze coverage data class. Parsed from LCOV-formatted blaze output. */
class BlazeCoverageData {

  private static final Logger logger = Logger.getInstance(BlazeCoverageData.class);

  static BlazeCoverageData parse(InputStream inputStream) throws IOException {
    return LcovParser.parse(inputStream);
  }

  static class FileData {
    final String source;
    final TIntIntHashMap lineHits;

    private FileData(String source, TIntIntHashMap lineHits) {
      this.source = source;
      this.lineHits = lineHits;
    }
  }

  final ImmutableMap<String, FileData> perFileData;

  private BlazeCoverageData(ImmutableMap<String, FileData> perFileData) {
    this.perFileData = perFileData;
  }

  private static class LcovParser {
    // there are other valid lcov tracefile prefixes, but they're all ignored here
    static final String SF = "SF:";
    static final String DA = "DA:";
    static final String END_OF_RECORD = "end_of_record";

    private static BlazeCoverageData parse(InputStream inputStream) throws IOException {
      Map<String, FileData> map = new HashMap<>();
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(SF)) {
          String source = line.substring(SF.length());
          TIntIntHashMap hits = parseHits(reader);
          if (!hits.isEmpty()) {
            map.put(source, new FileData(source, hits));
          }
        }
      }
      return new BlazeCoverageData(ImmutableMap.copyOf(map));
    }

    private static TIntIntHashMap parseHits(BufferedReader reader) throws IOException {
      TIntIntHashMap hits = new TIntIntHashMap();
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(END_OF_RECORD)) {
          return hits;
        }
        if (line.startsWith(DA)) {
          // DA:line,hits
          int comma = line.indexOf(',');
          try {
            hits.put(
                Integer.parseInt(line.substring(DA.length(), comma)),
                Integer.parseInt(line.substring(comma + 1)));
          } catch (NumberFormatException e) {
            logger.warn("Cannot parse LCOV line: " + line, e);
          }
        }
      }
      return hits;
    }
  }
}
