/*
 * Copyright 2021 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.ideinfo;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link PyIdeInfo}. */
@RunWith(JUnit4.class)
public class PyIdeInfoTest {

  @Test
  public void testPyIdeArgEncodingRoundtrip() throws Exception {
    ImmutableList<String> args = ImmutableList.of("--ARG1", "--ARG2", "--ARG3='with spaces'");

    IntellijIdeInfo.PyIdeInfo pyIdeProto =
        IntellijIdeInfo.PyIdeInfo.newBuilder().addAllArgs(args).build();
    PyIdeInfo pyIdeInfo = PyIdeInfo.fromProto(pyIdeProto);
    List<String> parsedArgs = pyIdeInfo.getArgs();
    assertThat(parsedArgs).containsExactly("--ARG1", "--ARG2", "--ARG3=with spaces");

    // check they get re-encoded as expected
    pyIdeProto = pyIdeInfo.toProto();
    ImmutableList<String> encodedArgs =
        pyIdeProto.getArgsList().stream().collect(ImmutableList.toImmutableList());
    assertThat(encodedArgs).containsExactly("--ARG1", "--ARG2", "\"--ARG3=with spaces\"");

    // and roundtrip is consistent
    pyIdeInfo = PyIdeInfo.fromProto(pyIdeProto);
    List<String> parsedArgs2 = pyIdeInfo.getArgs();
    assertThat(parsedArgs2).isEqualTo(parsedArgs);
  }
}
