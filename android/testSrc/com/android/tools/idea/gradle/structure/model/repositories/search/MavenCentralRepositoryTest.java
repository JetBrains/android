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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link MavenCentralRepository}.
 */
public class MavenCentralRepositoryTest {
  @Test
  public void testCreateUrlWithGroupId() {
    SearchRequest request = new SearchRequest("guava", "com.google.guava", 20, 1);
    String url = MavenCentralRepository.createRequestUrl(request);
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=g:\"com.google.guava\"+AND+a:\"guava\"", url);
  }

  @Test
  public void testCreateUrlWithoutGroupId() {
    SearchRequest request = new SearchRequest("guava", null, 20, 1);
    String url = MavenCentralRepository.createRequestUrl(request);
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=a:\"guava\"", url);
  }

  @Test
  public void testParse() throws Exception {
    @Language("XML")
    String response = "<response>\n" +
                      "    <lst name=\"responseHeader\">\n" +
                      "        <int name=\"status\">0</int>\n" +
                      "        <int name=\"QTime\">0</int>\n" +
                      "        <lst name=\"params\">\n" +
                      "            <str name=\"spellcheck\">true</str>\n" +
                      "            <str name=\"fl\">\n" +
                      "                id,g,a,latestVersion,p,ec,repositoryId,text,timestamp,versionCount\n" +
                      "            </str>\n" +
                      "            <str name=\"sort\">score desc,timestamp desc,g asc,a asc</str>\n" +
                      "            <str name=\"indent\">off</str>\n" +
                      "            <str name=\"start\">41</str>\n" +
                      "            <str name=\"q\">guice</str>\n" +
                      "            <str name=\"qf\">text^20 g^5 a^10</str>\n" +
                      "            <str name=\"spellcheck.count\">5</str>\n" +
                      "            <str name=\"wt\">xml</str>\n" +
                      "            <str name=\"rows\">5</str>\n" +
                      "            <str name=\"version\">2.2</str>\n" +
                      "            <str name=\"defType\">dismax</str>\n" +
                      "        </lst>\n" +
                      "    </lst>\n" +
                      "    <result name=\"response\" numFound=\"409\" start=\"41\">\n" +
                      "        <doc>\n" +
                      "            <str name=\"a\">guice-bean</str>\n" +
                      "            <arr name=\"ec\">\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <str name=\"g\">org.sonatype.spice.inject</str>\n" +
                      "            <str name=\"id\">org.sonatype.spice.inject:guice-bean</str>\n" +
                      "            <str name=\"latestVersion\">1.3.4</str>\n" +
                      "            <str name=\"p\">pom</str>\n" +
                      "            <str name=\"repositoryId\">central</str>\n" +
                      "            <arr name=\"text\">\n" +
                      "                <str>org.sonatype.spice.inject</str>\n" +
                      "                <str>guice-bean</str>\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <long name=\"timestamp\">1283070402000</long>\n" +
                      "            <int name=\"versionCount\">10</int>\n" +
                      "        </doc>\n" +
                      "        <doc>\n" +
                      "            <str name=\"a\">guice-nexus</str>\n" +
                      "            <arr name=\"ec\">\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <str name=\"g\">org.sonatype.spice.inject</str>\n" +
                      "            <str name=\"id\">org.sonatype.spice.inject:guice-nexus</str>\n" +
                      "            <str name=\"latestVersion\">0.1.0</str>\n" +
                      "            <str name=\"p\">pom</str>\n" +
                      "            <str name=\"repositoryId\">central</str>\n" +
                      "            <arr name=\"text\">\n" +
                      "                <str>org.sonatype.spice.inject</str>\n" +
                      "                <str>guice-nexus</str>\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <long name=\"timestamp\">1267701468000</long>\n" +
                      "            <int name=\"versionCount\">1</int>\n" +
                      "        </doc>\n" +
                      "        <doc>\n" +
                      "            <str name=\"a\">jersey2-guice</str>\n" +
                      "            <arr name=\"ec\">\n" +
                      "                <str>-sources.jar</str>\n" +
                      "                <str>-javadoc.jar</str>\n" +
                      "                <str>.jar</str>\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <str name=\"g\">be.fluid-it.com.squarespace.jersey2-guice</str>\n" +
                      "            <str name=\"id\">\n" +
                      "                be.fluid-it.com.squarespace.jersey2-guice:jersey2-guice\n" +
                      "            </str>\n" +
                      "            <str name=\"latestVersion\">0.10-fix</str>\n" +
                      "            <str name=\"p\">jar</str>\n" +
                      "            <str name=\"repositoryId\">central</str>\n" +
                      "            <arr name=\"text\">\n" +
                      "                <str>be.fluid-it.com.squarespace.jersey2-guice</str>\n" +
                      "                <str>jersey2-guice</str>\n" +
                      "                <str>-sources.jar</str>\n" +
                      "                <str>-javadoc.jar</str>\n" +
                      "                <str>.jar</str>\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <long name=\"timestamp\">1446749364000</long>\n" +
                      "            <int name=\"versionCount\">1</int>\n" +
                      "        </doc>\n" +
                      "        <doc>\n" +
                      "            <str name=\"a\">stdlib-guice-hibernate</str>\n" +
                      "            <arr name=\"ec\">\n" +
                      "                <str>-sources.jar</str>\n" +
                      "                <str>-javadoc.jar</str>\n" +
                      "                <str>.jar</str>\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <str name=\"g\">com.peterphi.std.guice</str>\n" +
                      "            <str name=\"id\">com.peterphi.std.guice:stdlib-guice-hibernate</str>\n" +
                      "            <str name=\"latestVersion\">8.5.1</str>\n" +
                      "            <str name=\"p\">jar</str>\n" +
                      "            <str name=\"repositoryId\">central</str>\n" +
                      "            <arr name=\"text\">\n" +
                      "                <str>com.peterphi.std.guice</str>\n" +
                      "                <str>stdlib-guice-hibernate</str>\n" +
                      "                <str>-sources.jar</str>\n" +
                      "                <str>-javadoc.jar</str>\n" +
                      "                <str>.jar</str>\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <long name=\"timestamp\">1446645753000</long>\n" +
                      "            <int name=\"versionCount\">76</int>\n" +
                      "        </doc>\n" +
                      "        <doc>\n" +
                      "            <str name=\"a\">stdlib-guice-webapp</str>\n" +
                      "            <arr name=\"ec\">\n" +
                      "                <str>-javadoc.jar</str>\n" +
                      "                <str>-sources.jar</str>\n" +
                      "                <str>.jar</str>\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <str name=\"g\">com.peterphi.std.guice</str>\n" +
                      "            <str name=\"id\">com.peterphi.std.guice:stdlib-guice-webapp</str>\n" +
                      "            <str name=\"latestVersion\">8.5.1</str>\n" +
                      "            <str name=\"p\">jar</str>\n" +
                      "            <str name=\"repositoryId\">central</str>\n" +
                      "            <arr name=\"text\">\n" +
                      "                <str>com.peterphi.std.guice</str>\n" +
                      "                <str>stdlib-guice-webapp</str>\n" +
                      "                <str>-javadoc.jar</str>\n" +
                      "                <str>-sources.jar</str>\n" +
                      "                <str>.jar</str>\n" +
                      "                <str>.pom</str>\n" +
                      "            </arr>\n" +
                      "            <long name=\"timestamp\">1446645749000</long>\n" +
                      "            <int name=\"versionCount\">76</int>\n" +
                      "        </doc>\n" +
                      "    </result>\n" +
                      "    <lst name=\"spellcheck\">\n" +
                      "        <lst name=\"suggestions\"/>\n" +
                      "    </lst>\n" +
                      "</response>";
    Reader responseReader = new StringReader(response);
    SearchResult result = new MavenCentralRepository().parse(responseReader);
    assertEquals(409, result.getTotalFound());
    List<String> data = result.getData();
    assertThat(data).containsExactly(
      "org.sonatype.spice.inject:guice-bean:1.3.4",
      "org.sonatype.spice.inject:guice-nexus:0.1.0",
      "be.fluid-it.com.squarespace.jersey2-guice:jersey2-guice:0.10-fix",
      "com.peterphi.std.guice:stdlib-guice-hibernate:8.5.1",
      "com.peterphi.std.guice:stdlib-guice-webapp:8.5.1")
      .inOrder();
  }
}