/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.query;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Attribute;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.QueryResult;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Rule;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.Target;
import com.google.idea.blaze.base.model.primitives.Label;
import com.google.idea.blaze.base.query.GeneratedTarget.MacroData;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * A helper class which parses {@link GeneratedTarget} from targets output by a 'blaze query
 * --output=proto' command.
 *
 * <p>Only handles rule query results containing the 'generator_location' and 'generator_function'
 * attributes.
 */
public final class BlazeQueryProtoParser {

  private BlazeQueryProtoParser() {}

  public static ImmutableList<GeneratedTarget> parseProtoOutput(InputStream stream)
      throws IOException {
    QueryResult result = QueryResult.parseFrom(stream);
    return result.getTargetList().stream()
        .map(BlazeQueryProtoParser::parseTarget)
        .filter(Objects::nonNull)
        .collect(toImmutableList());
  }

  @Nullable
  private static GeneratedTarget parseTarget(Target message) {
    return message.hasRule() ? parseRule(message.getRule()) : null;
  }

  @Nullable
  private static GeneratedTarget parseRule(Rule message) {
    MacroData macro = parseMacroData(message);
    if (macro == null) {
      return null;
    }
    String ruleType = message.getRuleClass();
    Label label = Label.createIfValid(message.getName());
    return label == null ? null : new GeneratedTarget(ruleType, label, macro);
  }

  @Nullable
  private static MacroData parseMacroData(Rule message) {
    Attribute location = findAttribute(message, "generator_location");
    Integer lineNumber = location != null ? parseLineNumber(location.getStringValue()) : null;
    if (lineNumber == null) {
      return null;
    }

    Attribute fn = findAttribute(message, "generator_function");
    if (fn == null) {
      return null;
    }
    String macroFunction = fn.getStringValue();

    Attribute generatorName = findAttribute(message, "generator_name");
    String name = generatorName == null ? null : generatorName.getStringValue();
    return new MacroData(lineNumber, macroFunction, name);
  }

  @Nullable
  private static Attribute findAttribute(Rule message, String name) {
    return message.getAttributeList().stream()
        .filter(a -> a.getName().equals(name))
        .findFirst()
        .orElse(null);
  }

  /** Location string format: absolute_path:line_number[:column_number] */
  @Nullable
  private static Integer parseLineNumber(String location) {
    if (location == null) {
      return null;
    }
    int ix1 = location.indexOf(':');
    if (ix1 == -1) {
      return null;
    }
    int ix2 = location.indexOf(':', ix1 + 1);
    try {
      String substring = ix2 == -1 ? location.substring(ix1 + 1) : location.substring(ix1 + 1, ix2);
      return Integer.parseInt(substring);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
