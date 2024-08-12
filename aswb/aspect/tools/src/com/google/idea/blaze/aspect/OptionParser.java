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
package com.google.idea.blaze.aspect;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * A very light-weight command-line argument parser.
 *
 * <p>Expects args array of the form ["--arg_name1", "value1", "--arg_name2", "value2", ...]
 */
public final class OptionParser {
  private OptionParser() {}

  /**
   * Searches for '--[name]' in the args list, and parses the following argument using the supplied
   * parser. Returns null if not such argument found.
   *
   * @throws IllegalArgumentException if '--[name]' appears more than once, as this indicates a
   *     programming error on the client side.
   */
  @Nullable
  static <T> T parseSingleOption(String[] args, String name, Function<String, T> parser) {
    String argName = "--" + name;
    for (int i = 0; i < args.length; i++) {
      if (!args[i].equals(argName)) {
        continue;
      }
      if (i == args.length - 1) {
        throw new IllegalArgumentException("Expected value after " + args[i]);
      }
      for (int j = i + 2; j < args.length; j++) {
        if (args[j].equals(argName)) {
          throw new IllegalArgumentException("Expected " + args[i] + " to appear at most once");
        }
      }
      return parser.apply(args[i + 1]);
    }
    return null;
  }

  /**
   * Parse all '--[name]' flags in `args`, return a list of all their parsed values.
   *
   * <p>When args[i] = `--[name]`, then args[i+1] is parsed for the value, using `parser`.
   *
   * <p>Otherwise args[i] is ignored.
   *
   * <p>Returns an empty list if no occurrences of the flag are found.
   */
  static <T> List<T> parseMultiOption(String[] args, String name, Function<String, T> parser) {
    List<T> result = null;
    String argName = "--" + name;
    for (int i = 0; i < args.length; i++) {
      if (!args[i].equals(argName)) {
        continue;
      }
      if (i == args.length - 1) {
        throw new IllegalArgumentException("Expected value after " + args[i]);
      }
      T parsed = parser.apply(args[i + 1]);
      if (parsed != null) {
        if (result == null) {
          result = new ArrayList<>();
        }
        result.add(parsed);
      }
      i++;
    }
    return result == null ? ImmutableList.of() : result;
  }
}
