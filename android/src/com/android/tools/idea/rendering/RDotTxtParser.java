/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * Parse R.txt file are return a list of the id names.
 */
class RDotTxtParser {

  private static final String INT_ID = "int id ";
  private static final int INT_ID_LEN = INT_ID.length();
  private static Logger ourLog;

  @NotNull
  static Collection<String> parseFile(final File rFile) {
    try {
      return Files.readLines(rFile, Charsets.UTF_8, new LineProcessor<Collection<String>>() {
        Collection<String> idNames = new ArrayList<String>(32);

        @Override
        public boolean processLine(@NotNull String line) throws IOException {
          if (!line.startsWith(INT_ID)) {
            return true;
          }
          int i = line.indexOf(' ', INT_ID_LEN);
          assert i != -1 : "File not in expected format: " + rFile.getPath() + "\n" +
                           "Expected the ids to be in the format int id <name> <number>";
          idNames.add(line.substring(INT_ID_LEN, i));
          return true;
        }

        @Override
        public Collection<String> getResult() {
          return idNames;
        }
      });
    }
    catch (IOException e) {
      getLog().warn("Unable to read file: " + rFile.getPath(), e);
      return Collections.emptyList();
    }
  }

  private static Logger getLog() {
    if (ourLog == null) {
      ourLog = Logger.getInstance(RDotTxtParser.class);
    }
    return ourLog;
  }
}
