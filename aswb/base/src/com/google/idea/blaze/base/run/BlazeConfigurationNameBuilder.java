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
package com.google.idea.blaze.base.run;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import java.util.Arrays;

/**
 * Utility class for creating Blaze configuration names of the form "{build system name} {command
 * name} {target string}", where each component is optional.
 */
public class BlazeConfigurationNameBuilder {
  private String buildSystemName;
  private String commandName;
  private String targetString;

  public BlazeConfigurationNameBuilder() {}

  /**
   * Use the passed {@code configuration} to initialize the build system name, command name, and
   * target string. If the configuration's command name is null, this will default to "command". If
   * the configuration's target is null, target string will also be null.
   */
  public BlazeConfigurationNameBuilder(BlazeCommandRunConfiguration configuration) {
    setBuildSystemName(configuration.getProject());

    BlazeCommandName commandName = configuration.getHandler().getCommandName();
    setCommandName(commandName == null ? "command" : commandName.toString());

    ImmutableList<? extends TargetExpression> targets = configuration.getTargets();
    if (!targets.isEmpty()) {
      TargetExpression first = targets.get(0);
      String text = first instanceof Label ? getTextForLabel((Label) first) : first.toString();
      setTargetString(text);
    }
  }

  /**
   * Sets the build system name to the name of the build system used by {@code project}, e.g.
   * "Blaze" or "Bazel".
   */
  @CanIgnoreReturnValue
  public BlazeConfigurationNameBuilder setBuildSystemName(Project project) {
    buildSystemName = Blaze.buildSystemName(project);
    return this;
  }

  /** Sets the command name to {@code commandName}. */
  @CanIgnoreReturnValue
  public BlazeConfigurationNameBuilder setCommandName(String commandName) {
    this.commandName = commandName;
    return this;
  }

  /** Sets the target string to {@code targetString}. */
  @CanIgnoreReturnValue
  public BlazeConfigurationNameBuilder setTargetString(String targetString) {
    this.targetString = targetString;
    return this;
  }

  /**
   * Sets the target string to a string of the form "{package}:{target}", where 'target' is {@code
   * label}'s target, and the 'package' is the containing package. For example, the {@link Label}
   * "//javatests/com/google/foo/bar/baz:FooTest" will set the target string to "baz:FooTest".
   */
  @CanIgnoreReturnValue
  public BlazeConfigurationNameBuilder setTargetString(Label label) {
    this.targetString =
        String.format("%s:%s", getImmediatePackage(label), label.targetName().toString());
    return this;
  }

  /**
   * Returns a ui-friendly label description, or the form "{package}:{target}", where 'target' is
   * {@code label}'s target, and the 'package' is the containing package. For example, the {@link
   * Label} "//javatests/com/google/foo/bar/baz:FooTest" will return "baz:FooTest".
   */
  public static String getTextForLabel(Label label) {
    return String.format("%s:%s", getImmediatePackage(label), label.targetName().toString());
  }

  /**
   * Get the portion of a label between the colon and the preceding slash. Example:
   * "//javatests/com/google/foo/bar/baz:FooTest" -> "baz".
   */
  private static String getImmediatePackage(Label label) {
    String labelString = label.toString();
    int colonIndex = labelString.lastIndexOf(':');
    assert colonIndex >= 0;
    int slashIndex = labelString.lastIndexOf('/', colonIndex);
    assert slashIndex >= 0;
    return labelString.substring(slashIndex + 1, colonIndex);
  }

  /**
   * Builds a name of the form "{build system name} {command name} {target string}". Any null
   * components are omitted, and there is always one space inserted between each included component.
   */
  public String build() {
    // Use this instead of String.join to omit null terms.
    return StringUtil.join(Arrays.asList(buildSystemName, commandName, targetString), " ");
  }
}
