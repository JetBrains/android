/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.structure.model.repositories.search;

import org.intellij.lang.annotations.Language;
import org.junit.Test;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assert.*;

/**
 * Tests for {@link JCenterRepository}.
 */
public class JCenterRepositoryTest {
  @Test
  public void testCreateUrlWithGroupId() {
    SearchRequest request = new SearchRequest("guava", "com.google.guava", 20, 1);
    String url = JCenterRepository.createRequestUrl(request);
    assertEquals("https://api.bintray.com/search/packages/maven?g=com.google.guava&a=guava&subject=bintray&repo=jcenter", url);
  }

  @Test
  public void testCreateUrlWithoutGroupId() {
    SearchRequest request = new SearchRequest("guava", null, 20, 1);
    String url = JCenterRepository.createRequestUrl(request);
    assertEquals("https://api.bintray.com/search/packages/maven?a=guava&subject=bintray&repo=jcenter", url);
  }

  @Test
  public void testParse() {
    @Language("JSON")
    String response = "[\n" +
                      "  {\n" +
                      "    \"name\": \"com.atlassian.guava:guava\",\n" +
                      "    \"repo\": \"jcenter\",\n" +
                      "    \"owner\": \"bintray\",\n" +
                      "    \"desc\": null,\n" +
                      "    \"system_ids\": [\n" +
                      "      \"com.atlassian.guava:guava\"\n" +
                      "    ],\n" +
                      "    \"versions\": [\n" +
                      "      \"15.0\"\n" +
                      "    ],\n" +
                      "    \"latest_version\": \"15.0\"\n" +
                      "  },\n" +
                      "  {\n" +
                      "    \"name\": \"com.atlassian.bundles:guava\",\n" +
                      "    \"repo\": \"jcenter\",\n" +
                      "    \"owner\": \"bintray\",\n" +
                      "    \"desc\": null,\n" +
                      "    \"system_ids\": [\n" +
                      "      \"com.atlassian.bundles:guava\"\n" +
                      "    ],\n" +
                      "    \"versions\": [\n" +
                      "      \"8.1\",\n" +
                      "      \"8.0\",\n" +
                      "      \"1.0-actually-8.1\"\n" +
                      "    ],\n" +
                      "    \"latest_version\": \"8.1\"\n" +
                      "  },\n" +
                      "  {\n" +
                      "    \"name\": \"io.janusproject.guava:guava\",\n" +
                      "    \"repo\": \"jcenter\",\n" +
                      "    \"owner\": \"bintray\",\n" +
                      "    \"desc\": null,\n" +
                      "    \"system_ids\": [\n" +
                      "      \"io.janusproject.guava:guava\"\n" +
                      "    ],\n" +
                      "    \"versions\": [\n" +
                      "      \"19.0.0\",\n" +
                      "      \"17.0.2\",\n" +
                      "      \"17.0\"\n" +
                      "    ],\n" +
                      "    \"latest_version\": \"19.0.0\"\n" +
                      "  },\n" +
                      "  {\n" +
                      "    \"name\": \"com.google.guava:guava\",\n" +
                      "    \"repo\": \"jcenter\",\n" +
                      "    \"owner\": \"bintray\",\n" +
                      "    \"desc\": \"Guava is a suite of core and expanded libraries that include\\n    utility classes, google's collections, io classes, and much\\n    much more.\\n\\n    Guava has two code dependencies - javax.annotation\\n    per the JSR-305 spec and javax.inject per the JSR-330 spec.\",\n" +
                      "    \"system_ids\": [\n" +
                      "      \"com.google.guava:guava\"\n" +
                      "    ],\n" +
                      "    \"versions\": [\n" +
                      "      \"19.0\",\n" +
                      "      \"19.0-rc3\",\n" +
                      "      \"19.0-rc2\",\n" +
                      "      \"19.0-rc1\",\n" +
                      "      \"18.0\",\n" +
                      "      \"18.0-rc2\",\n" +
                      "      \"18.0-rc1\",\n" +
                      "      \"11.0.2-atlassian-02\",\n" +
                      "      \"17.0\",\n" +
                      "      \"17.0-rc2\",\n" +
                      "      \"17.0-rc1\",\n" +
                      "      \"16.0.1\",\n" +
                      "      \"16.0\",\n" +
                      "      \"16.0-rc1\",\n" +
                      "      \"15.0\",\n" +
                      "      \"15.0-rc1\",\n" +
                      "      \"14.0.1\",\n" +
                      "      \"14.0\",\n" +
                      "      \"14.0-rc3\",\n" +
                      "      \"14.0-rc2\",\n" +
                      "      \"14.0-rc1\",\n" +
                      "      \"13.0.1\",\n" +
                      "      \"13.0\",\n" +
                      "      \"13.0-final\",\n" +
                      "      \"13.0-rc2\",\n" +
                      "      \"13.0-rc1\",\n" +
                      "      \"12.0.1\",\n" +
                      "      \"12.0\",\n" +
                      "      \"12.0-rc2\",\n" +
                      "      \"12.0-rc1\",\n" +
                      "      \"11.0.2-atlassian-01\",\n" +
                      "      \"11.0.2\",\n" +
                      "      \"11.0.1\",\n" +
                      "      \"11.0\",\n" +
                      "      \"11.0-rc1\",\n" +
                      "      \"10.0.1\",\n" +
                      "      \"10.0\",\n" +
                      "      \"10.0-rc3\",\n" +
                      "      \"10.0-rc2\",\n" +
                      "      \"10.0-rc1\",\n" +
                      "      \"r09\",\n" +
                      "      \"r08\",\n" +
                      "      \"r07\",\n" +
                      "      \"r06\",\n" +
                      "      \"r05\",\n" +
                      "      \"r03\"\n" +
                      "    ],\n" +
                      "    \"latest_version\": \"19.0\"\n" +
                      "  },\n" +
                      "  {\n" +
                      "    \"name\": \"de.weltraumschaf.commons:guava\",\n" +
                      "    \"repo\": \"jcenter\",\n" +
                      "    \"owner\": \"bintray\",\n" +
                      "    \"desc\": null,\n" +
                      "    \"system_ids\": [\n" +
                      "      \"de.weltraumschaf.commons:guava\"\n" +
                      "    ],\n" +
                      "    \"versions\": [\n" +
                      "      \"2.1.0\",\n" +
                      "      \"2.0.0\",\n" +
                      "      \"1.1.3\",\n" +
                      "      \"1.1.2\",\n" +
                      "      \"1.0.0-beta\",\n" +
                      "      \"1.0.0-alpha\",\n" +
                      "      \"1.1.1\",\n" +
                      "      \"1.1.0\",\n" +
                      "      \"1.0.1\",\n" +
                      "      \"1.0.0\"\n" +
                      "    ],\n" +
                      "    \"latest_version\": \"2.1.0\"\n" +
                      "  }\n" +
                      "]";
    Reader responseReader = new StringReader(response);
    SearchResult result = new JCenterRepository().parse(responseReader);
    assertEquals(5, result.getTotalFound());
    List<String> data = result.getData();
    assertThat(data).hasSize(5)
                    .containsSequence("com.atlassian.guava:guava:15.0",
                                      "com.atlassian.bundles:guava:8.1",
                                      "io.janusproject.guava:guava:19.0.0",
                                      "com.google.guava:guava:19.0",
                                      "de.weltraumschaf.commons:guava:2.1.0");
  }
}