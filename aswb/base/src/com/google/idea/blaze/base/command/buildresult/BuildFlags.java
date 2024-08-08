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
package com.google.idea.blaze.base.command.buildresult;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.runtime.proto.CommandLineOuterClass;
import com.google.devtools.build.lib.runtime.proto.CommandLineOuterClass.CommandLineSection;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A data class representing blaze's build options for a build. */
public final class BuildFlags {
  private static final String STARTUP_OPTIONS_SECTION_LABEL = "startup options";
  private static final String CMDLINE_OPTIONS_SECTION_LABEL = "command options";

  static BuildFlags parseBep(InputStream bepStream) throws BuildEventStreamException {
    return parseBep(BuildEventStreamProvider.fromInputStream(bepStream));
  }

  public static BuildFlags parseBep(BuildEventStreamProvider stream)
      throws BuildEventStreamException {
    BuildEventStreamProtos.BuildEvent event;
    // order matters for build options
    Set<String> startupOptions = new LinkedHashSet<>();
    Set<String> cmdlineOptions = new LinkedHashSet<>();

    while ((event = stream.getNext()) != null) {
      switch (event.getId().getIdCase()) {
        case STRUCTURED_COMMAND_LINE:
          List<CommandLineSection> sections = event.getStructuredCommandLine().getSectionsList();
          for (CommandLineOuterClass.CommandLineSection commandLineSection : sections) {
            switch (commandLineSection.getSectionLabel()) {
              case STARTUP_OPTIONS_SECTION_LABEL:
                addOptionsToBuilder(
                    startupOptions, commandLineSection.getOptionList().getOptionList());
                continue;
              case CMDLINE_OPTIONS_SECTION_LABEL:
                addOptionsToBuilder(
                    cmdlineOptions, commandLineSection.getOptionList().getOptionList());
                continue;
              default: // continue
            }
          }
          continue;
        default: // continue
      }
    }
    return new BuildFlags(
        ImmutableList.copyOf(startupOptions), ImmutableList.copyOf(cmdlineOptions));
  }

  private static void addOptionsToBuilder(
      Set<String> builder, List<CommandLineOuterClass.Option> options) {
    for (CommandLineOuterClass.Option option : options) {
      if (!option.getOptionName().isEmpty()) {
        builder.add(option.getCombinedForm().replace("'", ""));
      }
    }
  }

  private final ImmutableList<String> startupOptions;
  private final ImmutableList<String> cmdlineOptions;

  public BuildFlags() {
    this(ImmutableList.of(), ImmutableList.of());
  }

  public BuildFlags(ImmutableList<String> startupOptions, ImmutableList<String> cmdlineOptions) {
    this.startupOptions = startupOptions;
    this.cmdlineOptions = cmdlineOptions;
  }

  public ImmutableList<String> getStartupOptions() {
    return startupOptions;
  }

  public ImmutableList<String> getCmdlineOptions() {
    return cmdlineOptions;
  }
}
