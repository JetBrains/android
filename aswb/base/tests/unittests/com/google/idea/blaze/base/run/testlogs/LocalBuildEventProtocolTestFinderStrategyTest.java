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
package com.google.idea.blaze.base.run.testlogs;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos.BuildEventId.TestResultId;
import com.google.idea.blaze.base.BlazeTestCase;
import com.google.idea.blaze.base.command.buildresult.BuildEventProtocolOutputReader;
import com.google.idea.blaze.base.command.buildresult.BuildEventStreamProvider.BuildEventStreamException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelper.GetArtifactsException;
import com.google.idea.blaze.base.command.buildresult.BuildResultHelperBep;
import com.google.idea.blaze.base.io.InputStreamProvider;
import com.google.idea.blaze.base.io.MockInputStreamProvider;
import com.intellij.openapi.vfs.LocalFileSystem;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

/** Unit tests for {@link LocalBuildEventProtocolTestFinderStrategy}. */
@RunWith(JUnit4.class)
public class LocalBuildEventProtocolTestFinderStrategyTest extends BlazeTestCase {

  private MockInputStreamProvider inputStreamProvider;
  private final Set<File> deletedFiles = new HashSet<>();

  @After
  public void clearState() {
    deletedFiles.clear();
  }

  @Override
  protected void initTest(Container applicationServices, Container projectServices) {
    inputStreamProvider = new MockInputStreamProvider();
    applicationServices.register(InputStreamProvider.class, inputStreamProvider);
  }

  @Test
  public void testFinder_fileDeletedAfterCleanup() throws GetArtifactsException {
    File file = createMockFile("/tmp/bep_output.txt", new byte[0]);

    LocalBuildEventProtocolTestFinderStrategy testFinder =
        new LocalBuildEventProtocolTestFinderStrategy(new BuildResultHelperBep(file));
    try {
      BlazeTestResults unused = testFinder.findTestResults();
    } finally {
      testFinder.deleteTemporaryOutputFiles();
    }

    assertThat(deletedFiles).contains(file);
  }

  @Test
  public void findTestResults_shouldMatchBuildEventProtocolOutputReader()
      throws IOException, BuildEventStreamException, GetArtifactsException {
    BuildEventStreamProtos.BuildEvent.Builder test1 =
        testResultEvent(
            "//java/com/google:Test1",
            BuildEventStreamProtos.TestStatus.PASSED,
            ImmutableList.of("/usr/local/tmp/_cache/test_result.xml"));
    BuildEventStreamProtos.BuildEvent.Builder test2 =
        testResultEvent(
            "//java/com/google:Test2",
            BuildEventStreamProtos.TestStatus.INCOMPLETE,
            ImmutableList.of("/usr/local/tmp/_cache/second_result.xml"));
    File bepOutputFile =
        createMockFile("/tmp/bep_output.txt", asByteArray(ImmutableList.of(test1, test2)));
    LocalBuildEventProtocolTestFinderStrategy strategy =
        new LocalBuildEventProtocolTestFinderStrategy(new BuildResultHelperBep(bepOutputFile));

    InputStream inputStream = inputStreamProvider.forFile(bepOutputFile);
    BlazeTestResults results = BuildEventProtocolOutputReader.parseTestResults(inputStream);
    BlazeTestResults finderStrategyResults = strategy.findTestResults();

    assertThat(finderStrategyResults.perTargetResults.entries())
        .containsExactlyElementsIn(results.perTargetResults.entries());
  }

  private File createMockFile(String path, byte[] contents) {
    File org = new File(path);
    File spy = Mockito.spy(org);
    inputStreamProvider.addFile(path, contents);
    Mockito.when(spy.delete())
        .then(
            invocationOnMock -> {
              deletedFiles.add(spy);
              return true;
            });
    return spy;
  }

  private static byte[] asByteArray(Iterable<BuildEventStreamProtos.BuildEvent.Builder> events)
      throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    for (BuildEventStreamProtos.BuildEvent.Builder event : events) {
      event.build().writeDelimitedTo(output);
    }
    return output.toByteArray();
  }

  private static BuildEventStreamProtos.BuildEvent.Builder testResultEvent(
      String label, BuildEventStreamProtos.TestStatus status, List<String> filePaths) {
    return BuildEventStreamProtos.BuildEvent.newBuilder()
        .setId(
            BuildEventStreamProtos.BuildEventId.newBuilder()
                .setTestResult(TestResultId.newBuilder().setLabel(label)))
        .setTestResult(
            BuildEventStreamProtos.TestResult.newBuilder()
                .setStatus(status)
                .addAllTestActionOutput(
                    filePaths.stream()
                        .map(LocalBuildEventProtocolTestFinderStrategyTest::toEventFile)
                        .collect(toImmutableList())));
  }

  private static BuildEventStreamProtos.File toEventFile(String filePath) {
    return BuildEventStreamProtos.File.newBuilder().setUri(fileUrl(filePath)).build();
  }

  private static String fileUrl(String filePath) {
    return LocalFileSystem.PROTOCOL_PREFIX + filePath;
  }
}
