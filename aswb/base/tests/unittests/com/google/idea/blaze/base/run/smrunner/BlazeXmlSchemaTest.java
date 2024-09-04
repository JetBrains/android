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
package com.google.idea.blaze.base.run.smrunner;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.ErrorOrFailureOrSkipped;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestCase;
import com.google.idea.blaze.base.run.smrunner.BlazeXmlSchema.TestSuite;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link BlazeXmlSchema}. */
@RunWith(JUnit4.class)
public class BlazeXmlSchemaTest {

  @Test
  public void testNoTestSuitesOuterElement() {
    TestSuite parsed =
        parseXml(
            "  <testsuite name=\"foo/bar\" tests=\"1\" time=\"19.268\">",
            "      <testcase name=\"TestName\" result=\"completed\" status=\"run\" time=\"19.2\">",
            "          <system-out>PASS&#xA;&#xA;</system-out>",
            "      </testcase>",
            "  </testsuite>");
    assertThat(parsed).isNotNull();
  }

  @Test
  public void testOuterTestSuitesElement() {
    TestSuite parsed =
        parseXml(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<testsuites>",
            "  <testsuite name='foo' hostname='localhost' tests='331' failures='0' id='0'>",
            "    <properties />",
            "    <system-out />",
            "    <system-err />",
            "  </testsuite>",
            "  <testsuite name='bar'>",
            "    <testcase name='bar_test_1' time='12.2' />",
            "    <system-out />",
            "  </testsuite>",
            "</testsuites>");
    assertThat(parsed).isNotNull();
  }

  @Test
  public void testTestCaseWithMultipleFailures() {
    TestSuite parsed =
        parseXml(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<testsuites>",
            "  <testsuite name='com.google.ConfigTest' time='10' tests='2' failures='1'>",
            "    <testcase name='testCase1' time='7.9' status='run' result='completed'>",
            "      <failure message='failed' type='AssertionError'>Error message</failure>",
            "      <failure message='failed2' type='AssertionError'>Another Error</failure>",
            "    </testcase>",
            "  </testsuite>",
            "</testsuites>");

    assertThat(parsed.testSuites.get(0).testCases).hasSize(1);
    assertThat(parsed.testSuites.get(0).failures).isEqualTo(1);

    TestCase testCase = parsed.testSuites.get(0).testCases.get(0);
    assertThat(testCase.failures).hasSize(2);
    assertThat(testCase.failures.get(0).message).isEqualTo("failed");
    assertThat(BlazeXmlSchema.getErrorContent(testCase.failures.get(0))).isEqualTo("Error message");
    assertThat(testCase.failures.get(1).message).isEqualTo("failed2");
    assertThat(BlazeXmlSchema.getErrorContent(testCase.failures.get(1))).isEqualTo("Another Error");
  }

  @Test
  public void testTestCaseFailureWithExpectedActualData() {
    TestSuite parsed =
        parseXml(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<testsuites>",
            "  <testsuite name='com.google.ConfigTest' time='10' tests='1' failures='1'>",
            "    <testcase name='testCase1' time='7.9' status='run' result='completed'>",
            "      <failure message='failed' type='AssertionError'>Error message",
            "        <expected><value><![CDATA[abc]]></value></expected>",
            "        <actual><value><![CDATA[xyz]]></value></actual>",
            "      </failure>",
            "    </testcase>",
            "  </testsuite>",
            "</testsuites>");

    assertThat(parsed.testSuites.get(0).testCases).hasSize(1);
    assertThat(parsed.testSuites.get(0).failures).isEqualTo(1);

    ErrorOrFailureOrSkipped failure =
        Iterables.getOnlyElement(parsed.testSuites.get(0).testCases.get(0).failures);
    assertThat(failure.expected.values).containsExactly("abc");
    assertThat(failure.actual.values).containsExactly("xyz");
  }

  @Test
  public void systemOutCanBeParsed() {
    TestSuite parsed =
        parseXml(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<testsuites>",
            "  <testsuite>",
            "    <system-out>System output on testsuite</system-out>",
            "    <testcase>",
            "      <system-out>System output on testcase</system-out>",
            "    </testcase>",
            "  </testsuite>",
            "</testsuites>");

    TestSuite testSuite = Iterables.getOnlyElement(parsed.testSuites);
    assertThat(testSuite.sysOut).isEqualTo("System output on testsuite");
    TestCase testCase = Iterables.getOnlyElement(testSuite.testCases);
    assertThat(testCase.sysOut).isEqualTo("System output on testcase");
  }

  @Test
  public void systemErrCanBeParsed() {
    TestSuite parsed =
        parseXml(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<testsuites>",
            "  <testsuite>",
            "    <system-err>Error output on testsuite</system-err>",
            "    <testcase>",
            "      <system-err>Error output on testcase</system-err>",
            "    </testcase>",
            "  </testsuite>",
            "</testsuites>");

    TestSuite testSuite = Iterables.getOnlyElement(parsed.testSuites);
    assertThat(testSuite.sysErr).isEqualTo("Error output on testsuite");
    TestCase testCase = Iterables.getOnlyElement(testSuite.testCases);
    assertThat(testCase.sysErr).isEqualTo("Error output on testcase");
  }

  @Test
  public void testMergeShardedTests() {
    TestSuite shard1 =
        parseXml(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<testsuites>",
            "  <testsuite name='com.google.ConfigTest' time='10' tests='2' failures='1'>",
            "    <testcase name='testCase1' time='2.1' status='run' result='completed'/>",
            "    <testcase name='testCase2' time='7.9' status='run' result='completed'>",
            "      <failure message='failed'/>",
            "    </testcase>",
            "  </testsuite>",
            "</testsuites>");
    TestSuite shard2 =
        parseXml(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<testsuites>",
            "  <testsuite name='com.google.ConfigTest' time='5' tests='2' failures='1'>",
            "    <testcase name='testCase3' time='1' status='run' result='completed'/>",
            "    <testcase name='testCase4' time='4' status='run' result='completed'>",
            "      <failure message='failed'/>",
            "    </testcase>",
            "  </testsuite>",
            "</testsuites>");
    TestSuite mergedOuter = BlazeXmlSchema.mergeSuites(ImmutableList.of(shard1, shard2));
    assertThat(mergedOuter.testSuites).hasSize(1);
    TestSuite mergedInner = mergedOuter.testSuites.get(0).testSuites.get(0);
    assertThat(mergedInner.name).isEqualTo("com.google.ConfigTest");
    assertThat(mergedInner.time).isEqualTo(15d);
    assertThat(mergedInner.tests).isEqualTo(4);
    assertThat(mergedInner.failures).isEqualTo(2);
    assertThat(mergedInner.testCases).hasSize(4);
    assertThat(
            mergedInner.testCases.stream()
                .map(testCase -> testCase.name)
                .collect(Collectors.toList()))
        .containsExactly("testCase1", "testCase2", "testCase3", "testCase4");
  }

  @Test
  public void testErrorWithoutErrorContent() {
    TestSuite parsed =
        parseXml(
            "<?xml version='1.0' encoding='UTF-8'?>",
            "<testsuites>",
            "  <testsuite name='com.google.ConfigTest' tests='1' failures='0' errors='1'>",
            "    <testcase name='testCase1' status='run' duration='55' time='55'>",
            "<error message='exited with error code 1'></error>",
            "    </testcase>",
            "  </testsuite>",
            "</testsuites>");

    TestCase testCase = parsed.testSuites.get(0).testCases.get(0);
    assertThat(BlazeXmlSchema.getErrorContent(testCase.errors.get(0))).isNull();
  }

  private static TestSuite parseXml(String... lines) {
    InputStream stream =
        new ByteArrayInputStream(Joiner.on('\n').join(lines).getBytes(StandardCharsets.UTF_8));
    return BlazeXmlSchema.parse(stream);
  }
}
