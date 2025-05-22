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
package com.android.tools.idea.gradle.repositories.search

import com.android.ide.common.gradle.Version
import com.google.common.truth.Truth.assertThat
import org.intellij.lang.annotations.Language
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

/**
 * Tests for [MavenCentralRepository].
 */
class MavenCentralRepositoryTest {
  @Test
  fun testCreateUrlWithGroupId() {
    val request = SearchRequest(ArbitraryModulesSearchQuery("com.google.guava", "guava"), 20, 1)
    val url = MavenCentralRepository.createArbitraryModulesRequestUrl(request)
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=g%3Acom.google.guava+AND+a%3Aguava&core=gav", url)
  }

  @Test
  fun testCreateUrlWithoutGroupId() {
    val request = SearchRequest(ArbitraryModulesSearchQuery(null, "guava"), 20, 1)
    val url = MavenCentralRepository.createArbitraryModulesRequestUrl(request)
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=a%3Aguava&core=gav", url)
  }

  @Test
  fun testCreateUrlWithoutArtifactId() {
    val request = SearchRequest(ArbitraryModulesSearchQuery("com.google.guava", ""), 20, 1)
    val url = MavenCentralRepository.createArbitraryModulesRequestUrl(request)
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=g%3Acom.google.guava&core=gav", url)
  }

  @Test
  fun testCreateUrlWithWildcards() {
    val request = SearchRequest(ArbitraryModulesSearchQuery("com.google.*", "gu*va"), 20, 1)
    val url = MavenCentralRepository.createArbitraryModulesRequestUrl(request)
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=g%3Acom.google.*+AND+a%3Agu*va&core=gav", url)
  }

  @Test
  fun testCreateUrlWithId() {
    val request = SearchRequest(ArbitraryModulesSearchByModuleQuery("guava"), 20, 1)
    val url = MavenCentralRepository.createArbitraryModulesRequestUrl(request)
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=id%3Aguava&core=gav", url)
  }

  @Test
  @Throws(Exception::class)
  fun testParseArbitraryModulesResponse() {
    @Language("XML")
    val response = """
      <response>
        <lst name="responseHeader">
          <int name="status">0</int>
          <int name="QTime">2</int>
          <lst name="params">
            <str name="q">g:com.google.demo</str>
            <str name="core">gav</str>
            <str name="indent">off</str>
            <str name="fl">id,g,a,v,p,ec,timestamp,tags</str>
            <str name="start">0</str>
            <str name="sort">score desc,timestamp desc,g asc,a asc,v desc</str>
            <str name="rows">20</str>
            <str name="wt">xml</str>
            <str name="version">2.2</str>
          </lst>
        </lst>
        <result name="response" numFound="6" start="0">
          <doc>
            <str name="a">abc</str>
            <arr name="ec">
              <str>-sources.jar</str>
              <str>.pom</str>
              <str>-javadoc.jar</str>
              <str>-tests.jar</str>
              <str>.jar</str>
            </arr>
            <str name="g">com.google.demo</str>
            <str name="id">com.google.demo:abc:1.0.0</str>
            <str name="p">jar</str>
            <long name="timestamp">1740498658000</long>
            <str name="v">1.0.0</str>
          </doc>
          <doc>
            <str name="a">abc</str>
            <arr name="ec">
              <str>-sources.jar</str>
              <str>.pom</str>
              <str>-javadoc.jar</str>
              <str>-tests.jar</str>
              <str>.jar</str>
            </arr>
            <str name="g">com.google.demo</str>
            <str name="id">com.google.demo:abc:1.0.1</str>
            <str name="p">jar</str>
            <long name="timestamp">1740498658000</long>
            <str name="v">1.0.1</str>
          </doc>
          <doc>
            <str name="a">abc</str>
            <arr name="ec">
              <str>-sources.jar</str>
              <str>.pom</str>
              <str>-javadoc.jar</str>
              <str>-tests.jar</str>
              <str>.jar</str>
            </arr>
            <str name="g">com.google.demo</str>
            <str name="id">com.google.demo:abc:1.0.2</str>
            <str name="p">jar</str>
            <long name="timestamp">1740498658000</long>
            <str name="v">1.0.2</str>
          </doc>
          <doc>
            <str name="a">def</str>
            <arr name="ec">
              <str>-sources.jar</str>
              <str>.pom</str>
              <str>-javadoc.jar</str>
              <str>-tests.jar</str>
              <str>.jar</str>
            </arr>
            <str name="g">com.google.demo</str>
            <str name="id">com.google.demo:def:1.0.0</str>
            <str name="p">jar</str>
            <long name="timestamp">1740498658000</long>
            <str name="v">1.0.0</str>
          </doc>
          <doc>
            <str name="a">def</str>
            <arr name="ec">
              <str>-sources.jar</str>
              <str>.pom</str>
              <str>-javadoc.jar</str>
              <str>-tests.jar</str>
              <str>.jar</str>
            </arr>
            <str name="g">com.google.demo</str>
            <str name="id">com.google.demo:def:1.0.1</str>
            <str name="p">jar</str>
            <long name="timestamp">1740498658000</long>
            <str name="v">1.0.1</str>
          </doc>
          <doc>
            <str name="a">def</str>
            <arr name="ec">
              <str>-sources.jar</str>
              <str>.pom</str>
              <str>-javadoc.jar</str>
              <str>-tests.jar</str>
              <str>.jar</str>
            </arr>
            <str name="g">com.google.demo</str>
            <str name="id">com.google.demo:def:1.0.2</str>
            <str name="p">jar</str>
            <long name="timestamp">1740498658000</long>
            <str name="v">1.0.2</str>
          </doc>
        </result>
      </response>
    """.trimIndent()
    val responseReader = StringReader(response)
    val result = MavenCentralRepository.parseArbitraryModulesResponse(responseReader)
    val coordinates = result.artifactCoordinates
    assertThat(coordinates).containsExactly(
      "com.google.demo:abc:1.0.0",
      "com.google.demo:abc:1.0.1",
      "com.google.demo:abc:1.0.2",
      "com.google.demo:def:1.0.0",
      "com.google.demo:def:1.0.1",
      "com.google.demo:def:1.0.2"
    )
    assertThat(result.artifacts).isEqualTo(
      listOf(
        FoundArtifact(MavenCentralRepository.name, "com.google.demo", "abc",
                      setOf(Version.parse("1.0.0"), Version.parse("1.0.1"), Version.parse("1.0.2"))),
        FoundArtifact(MavenCentralRepository.name, "com.google.demo", "def",
                      setOf(Version.parse("1.0.0"), Version.parse("1.0.1"), Version.parse("1.0.2")))
      )
    )
  }

  @Test
  fun testCreateSingleModuleUrl() {
    val query = SingleModuleSearchQuery("com.google.guava", "guava")
    val url = MavenCentralRepository.createSingleModuleRequestUrl(query)
    assertEquals("https://repo.maven.apache.org/maven2/com/google/guava/guava/maven-metadata.xml", url)
  }

  @Test
  fun testCreateSingleModuleUrlWithDotsInArtifactId() {
    val query = SingleModuleSearchQuery("ai.agnos", "reactive-sparql_2.12")
    val url = MavenCentralRepository.createSingleModuleRequestUrl(query)
    assertEquals("https://repo.maven.apache.org/maven2/ai/agnos/reactive-sparql_2.12/maven-metadata.xml", url)
  }

  @Test
  fun testParseSingleModuleResponse() {
    @Language("XML")
    val response = """<metadata modelVersion="1.1.0">
      <groupId>commons-io</groupId>
      <artifactId>commons-io</artifactId>
      <versioning>
        <latest>2.12.0</latest>
        <release>2.12.0</release>
        <versions>
          <version>0.1</version>
          <version>1.0</version>
          <version>1.1</version>
          <version>1.2</version>
          <version>1.3</version>
          <version>1.3.1</version>
          <version>1.3.2</version>
          <version>1.4</version>
          <version>2.0</version>
          <version>2.0.1</version>
          <version>2.1</version>
          <version>2.2</version>
          <version>2.3</version>
          <version>2.4</version>
          <version>2.5</version>
          <version>2.6</version>
          <version>2.7</version>
          <version>2.8.0</version>
          <version>2.9.0</version>
          <version>2.10.0</version>
          <version>2.11.0</version>
          <version>2.12.0</version>
        </versions>
        <lastUpdated>20230516171153</lastUpdated>
      </versioning></metadata>
      """.trimIndent()

    val query = SingleModuleSearchQuery("commons-io", "commons-io")
    val responseReader = StringReader(response)
    val result = MavenCentralRepository.parseSingleModuleResponse(responseReader, query)
    val coordinates = result.artifactCoordinates
    assertThat(coordinates).containsExactly(
      "commons-io:commons-io:0.1",
      "commons-io:commons-io:1.0",
      "commons-io:commons-io:1.1",
      "commons-io:commons-io:1.2",
      "commons-io:commons-io:1.3",
      "commons-io:commons-io:1.3.1",
      "commons-io:commons-io:1.3.2",
      "commons-io:commons-io:1.4",
      "commons-io:commons-io:2.0",
      "commons-io:commons-io:2.0.1",
      "commons-io:commons-io:2.1",
      "commons-io:commons-io:2.2",
      "commons-io:commons-io:2.3",
      "commons-io:commons-io:2.4",
      "commons-io:commons-io:2.5",
      "commons-io:commons-io:2.6",
      "commons-io:commons-io:2.7",
      "commons-io:commons-io:2.8.0",
      "commons-io:commons-io:2.9.0",
      "commons-io:commons-io:2.10.0",
      "commons-io:commons-io:2.11.0",
      "commons-io:commons-io:2.12.0",
    )
  }
}