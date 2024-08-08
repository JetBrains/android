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
package com.google.idea.blaze.base.actions;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.google.idea.blaze.base.BlazeIntegrationTestCase;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for the GitHub issue filing action. */
@RunWith(JUnit4.class)
public class FileGitHubIssueActionTest extends BlazeIntegrationTestCase {

  @Test
  public void generatedGitHubTemplateUrl_containsCorrectQuery()
      throws UnsupportedEncodingException {
    URL url = FileGitHubIssueAction.getGitHubTemplateURL(getProject());
    String decodedUrlQuery = URLDecoder.decode(url.getQuery(), StandardCharsets.UTF_8.name());
    assertThat(decodedUrlQuery, containsString("body="));
    assertThat(decodedUrlQuery, containsString("Description of the issue. Please be specific"));
    assertThat(decodedUrlQuery, containsString("Version information"));
    assertThat(decodedUrlQuery, containsString("Platform:"));

    assertEquals("github.com", url.getHost());
    assertEquals("/bazelbuild/intellij/issues/new", url.getPath());
  }
}
