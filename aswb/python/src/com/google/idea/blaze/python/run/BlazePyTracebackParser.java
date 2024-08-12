/*
 * Copyright 2020 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.python.run;

import com.google.idea.common.experiments.BoolExperiment;
import com.intellij.ide.AppLifecycleListener;
import com.jetbrains.python.run.PyTracebackParser;
import com.jetbrains.python.traceBackParsers.LinkInTrace;
import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;

/** Hacky override for upstream {@link PyTracebackParser}. */
public class BlazePyTracebackParser extends PyTracebackParser {

  private static final BoolExperiment enabled =
      new BoolExperiment("blaze.py.traceback.override.enabled", true);

  @Override
  protected LinkInTrace findLinkInTrace(String line, Matcher matchedMatcher) {
    if (!enabled.getValue()) {
      return super.findLinkInTrace(line, matchedMatcher);
    }
    final String fileName = matchedMatcher.group(1).replace('\\', '/');
    final int lineNumber = Integer.parseInt(matchedMatcher.group(2));
    final int startPos = line.indexOf('\"') + 1;
    final int endPos = line.indexOf('\"', startPos);
    return new LinkInTrace(getCanonicalFilePath(fileName), lineNumber, startPos, endPos);
  }

  private static String getCanonicalFilePath(String filePath) {
    File file = new File(filePath);
    try {
      return file.getCanonicalPath();
    } catch (IOException e) {
      // fall back to original path
      return filePath;
    }
  }

  static class OverrideUpstreamParser implements AppLifecycleListener {
    @Override
    public void appStarted() {
      if (enabled.getValue()) {
        PARSERS[1] = new BlazePyTracebackParser();
      }
    }
  }
}
