/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.cpp;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Parses any compiler options that were not extracted by the build system earlier. Do minimal
 * parsing to extract what we need.
 */
final class UnfilteredCompilerOptions {

  static class Builder {
    private final BaseOptionParser baseOptionParser = new BaseOptionParser();
    private final ImmutableMap.Builder<String, OptionParser> registeredParsers =
        ImmutableMap.builder();

    /** Have the options parser handle the given one-or-two-token option (e.g., -Ifoo or -I foo). */
    Builder registerSingleOrSplitOption(String optionName) {
      registeredParsers.put(
          optionName, new SingleOrSplitOptionParser(optionName, baseOptionParser));
      return this;
    }

    /** Parse the given options and build extracted compiler options */
    UnfilteredCompilerOptions build(Iterable<String> unfilteredOptions) {
      ImmutableMap<String, OptionParser> registered = registeredParsers.build();
      baseOptionParser.setRegisteredOptionParsers(registered.values());
      UnfilteredCompilerOptions options =
          new UnfilteredCompilerOptions(baseOptionParser, registered);
      options.parse(unfilteredOptions);
      return options;
    }
  }

  /** Make a new builder to register options to extract. */
  static Builder builder() {
    return new Builder();
  }

  private final OptionParser baseOptionParser;
  private final ImmutableMap<String, OptionParser> registeredParsers;

  private UnfilteredCompilerOptions(
      OptionParser baseOptionParser, ImmutableMap<String, OptionParser> registeredParsers) {
    this.baseOptionParser = baseOptionParser;
    this.registeredParsers = registeredParsers;
  }

  private void parse(Iterable<String> unfilteredOptions) {
    OptionParser nextOptionParser = baseOptionParser;
    for (String unfilteredOption : unfilteredOptions) {
      nextOptionParser = nextOptionParser.parseValue(unfilteredOption);
    }
  }

  /**
   * Return the list of arguments that are not extracted (don't correspond to a registered option),
   * in the original order.
   */
  List<String> getUninterpretedOptions() {
    return baseOptionParser.values();
  }

  /**
   * Return the extracted option values for the given registered option name. E.g., if -I is
   * registered, and ["-foo", "-Ibar"] is parsed then getExtractedOptionValues("-I") returns
   * ["bar"]. List is in the original order.
   *
   * @param optionName the name of a flag that was registered to be extracted
   * @return option values corresponding to the flag.
   */
  List<String> getExtractedOptionValues(String optionName) {
    OptionParser parser = registeredParsers.get(optionName);
    Preconditions.checkNotNull(parser);
    return parser.values();
  }

  private interface OptionParser {
    /** Checks if the parser handles the next option value. */
    boolean handlesOptionValue(String optionValue);

    /**
     * Parses the option and returns the next handler (assumes {@link #handlesOptionValue} is true).
     */
    OptionParser parseValue(String optionValue);

    /** Return a list of option values captured by the parser. */
    List<String> values();
  }

  /**
   * A base option parser that defers to a list of more-specific registered flag parsers, before
   * handling the flag itself.
   */
  private static class BaseOptionParser implements OptionParser {
    private final List<String> values = new ArrayList<>();
    private Collection<OptionParser> registeredOptionParsers;

    void setRegisteredOptionParsers(Collection<OptionParser> registeredOptionParsers) {
      this.registeredOptionParsers = registeredOptionParsers;
    }

    @Override
    public boolean handlesOptionValue(String optionValue) {
      return true;
    }

    @Override
    public OptionParser parseValue(String optionValue) {
      for (OptionParser registeredParser : registeredOptionParsers) {
        if (registeredParser.handlesOptionValue(optionValue)) {
          return registeredParser.parseValue(optionValue);
        }
      }
      values.add(optionValue);
      return this;
    }

    @Override
    public List<String> values() {
      return values;
    }
  }

  /**
   * A parser that handles flags that can be one or two tokens (e.g., "-Ihdrs", vs "-I", "hdrs").
   */
  private static class SingleOrSplitOptionParser implements OptionParser {
    private final String optionName;
    private final BaseOptionParser baseOptionParser;
    private final List<String> values = new ArrayList<>();
    private boolean consumeNext;

    SingleOrSplitOptionParser(String optionName, BaseOptionParser baseOptionParser) {
      this.optionName = optionName;
      this.baseOptionParser = baseOptionParser;
    }

    @Override
    public boolean handlesOptionValue(String optionValue) {
      return consumeNext || optionValue.startsWith(optionName);
    }

    @Override
    public OptionParser parseValue(String optionValue) {
      if (consumeNext) {
        consumeNext = false;
        values.add(optionValue);
        return baseOptionParser;
      }
      if (optionValue.equals(optionName)) {
        consumeNext = true;
        return this;
      }
      if (optionValue.startsWith(optionName)) {
        values.add(optionValue.substring(optionName.length()));
        return baseOptionParser;
      }
      Preconditions.checkState(
          false, "Should check handlesOptionValue before attempting to parseValue");
      return null;
    }

    @Override
    public List<String> values() {
      return values;
    }
  }
}
