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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import java.util.Collection;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link BlazeCommandName}. */
@RunWith(JUnit4.class)
public class BlazeCommandNameTest {
  @Test
  public void emptyNameShouldThrow() {
    try {
      BlazeCommandName.fromString("");
      fail("Empty commands should not be allowed.");
    } catch (IllegalArgumentException expected) {
    }
  }

  @Test
  public void hardcodedNamesShouldBeKnown() {
    assertThat(BlazeCommandName.knownCommands()).contains(BlazeCommandName.MOBILE_INSTALL);
  }

  @Test
  public void userCommandNamesShouldBecomeKnown() {
    Collection<String> knownCommandStrings =
        Collections2.transform(
            BlazeCommandName.knownCommands(),
            new Function<BlazeCommandName, String>() {
              @Nullable
              @Override
              public String apply(BlazeCommandName input) {
                return input.toString();
              }
            });
    assertThat(knownCommandStrings).doesNotContain("user-command");
    BlazeCommandName userCommand = BlazeCommandName.fromString("user-command");
    assertThat(BlazeCommandName.knownCommands()).contains(userCommand);
  }
}
