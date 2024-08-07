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
package com.google.idea.blaze.base.command;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.concurrent.Immutable;

/**
 * A class for Blaze/Bazel command names. We enumerate the commands we use (and that we expect users
 * to be interested in), but do NOT use an enum because we want to allow users to specify arbitrary
 * commands.
 */
@Immutable
public final class BlazeCommandName {
  private static final Map<String, BlazeCommandName> knownCommands =
      Collections.synchronizedMap(new LinkedHashMap<>());

  public static final BlazeCommandName TEST = fromString("test");
  public static final BlazeCommandName RUN = fromString("run");
  public static final BlazeCommandName BUILD = fromString("build");
  public static final BlazeCommandName QUERY = fromString("query");
  public static final BlazeCommandName INFO = fromString("info");
  public static final BlazeCommandName MOBILE_INSTALL = fromString("mobile-install");
  public static final BlazeCommandName COVERAGE = fromString("coverage");

  public static BlazeCommandName fromString(String name) {
    knownCommands.putIfAbsent(name, new BlazeCommandName(name));
    return knownCommands.get(name);
  }

  private final String name;

  private BlazeCommandName(String name) {
    Preconditions.checkArgument(!name.isEmpty(), "Command should be non-empty.");
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof BlazeCommandName)) {
      return false;
    }
    BlazeCommandName that = (BlazeCommandName) o;
    return name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode();
  }

  /**
   * @return An unmodifiable view of the Blaze commands we know about (including those that the user
   *     has specified, in addition to those we have hard-coded).
   */
  public static Collection<BlazeCommandName> knownCommands() {
    return ImmutableList.copyOf(knownCommands.values());
  }
}
