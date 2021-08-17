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
    val request = SearchRequest(SearchQuery("com.google.guava", "guava"), 20, 1)
    val url = MavenCentralRepository.createRequestUrl(request)
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=g%3Acom.google.guava+AND+a%3Aguava", url)
  }

  @Test
  fun testCreateUrlWithoutGroupId() {
    val request = SearchRequest(SearchQuery(null, "guava"), 20, 1)
    val url = MavenCentralRepository.createRequestUrl(request)
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=a%3Aguava", url)
  }

  @Test
  fun testCreateUrlWithoutArtifactId() {
    val request = SearchRequest(SearchQuery("com.google.guava", ""), 20, 1)
    val url = MavenCentralRepository.createRequestUrl(request)
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=g%3Acom.google.guava", url)
  }

  @Test
  fun testCreateUrlWithWildcards() {
    val request = SearchRequest(SearchQuery("com.google.*", "gu*va"), 20, 1)
    val url = MavenCentralRepository.createRequestUrl(request)
    assertEquals("https://search.maven.org/solrsearch/select?rows=20&start=1&wt=xml&q=g%3Acom.google.*+AND+a%3Agu*va", url)
  }

  @Test
  @Throws(Exception::class)
  fun testParse() {
    @Language("XML")
    val response = """
      <response>
           <lst name="responseHeader">
               <int name="status">0</int>
               <int name="QTime">0</int>
               <lst name="params">
                   <str name="spellcheck">true</str>
                   <str name="fl">
                       id,g,a,latestVersion,p,ec,repositoryId,text,timestamp,versionCount
                   </str>
                   <str name="sort">score desc,timestamp desc,g asc,a asc</str>
                   <str name="indent">off</str>
                   <str name="start">41</str>
                   <str name="q">guice</str>
                   <str name="qf">text^20 g^5 a^10</str>
                   <str name="spellcheck.count">5</str>
                   <str name="wt">xml</str>
                   <str name="rows">5</str>
                   <str name="version">2.2</str>
                   <str name="defType">dismax</str>
               </lst>
           </lst>
           <result name="response" numFound="409" start="41">
               <doc>
                   <str name="a">guice-bean</str>
                   <arr name="ec">
                       <str>.pom</str>
                   </arr>
                   <str name="g">org.sonatype.spice.inject</str>
                   <str name="id">org.sonatype.spice.inject:guice-bean</str>
                   <str name="latestVersion">1.3.4</str>
                   <str name="p">pom</str>
                   <str name="repositoryId">central</str>
                   <arr name="text">
                       <str>org.sonatype.spice.inject</str>
                       <str>guice-bean</str>
                       <str>.pom</str>
                   </arr>
                   <long name="timestamp">1283070402000</long>
                   <int name="versionCount">10</int>
               </doc>
               <doc>
                   <str name="a">guice-nexus</str>
                   <arr name="ec">
                       <str>.pom</str>
                   </arr>
                   <str name="g">org.sonatype.spice.inject</str>
                   <str name="id">org.sonatype.spice.inject:guice-nexus</str>
                   <str name="latestVersion">0.1.0</str>
                   <str name="p">pom</str>
                   <str name="repositoryId">central</str>
                   <arr name="text">
                       <str>org.sonatype.spice.inject</str>
                       <str>guice-nexus</str>
                       <str>.pom</str>
                   </arr>
                   <long name="timestamp">1267701468000</long>
                   <int name="versionCount">1</int>
               </doc>
               <doc>
                   <str name="a">jersey2-guice</str>
                   <arr name="ec">
                       <str>-sources.jar</str>
                       <str>-javadoc.jar</str>
                       <str>.jar</str>
                       <str>.pom</str>
                   </arr>
                   <str name="g">be.fluid-it.com.squarespace.jersey2-guice</str>
                   <str name="id">
                       be.fluid-it.com.squarespace.jersey2-guice:jersey2-guice
                   </str>
                   <str name="latestVersion">0.10-fix</str>
                   <str name="p">jar</str>
                   <str name="repositoryId">central</str>
                   <arr name="text">
                       <str>be.fluid-it.com.squarespace.jersey2-guice</str>
                       <str>jersey2-guice</str>
                       <str>-sources.jar</str>
                       <str>-javadoc.jar</str>
                       <str>.jar</str>
                       <str>.pom</str>
                   </arr>
                   <long name="timestamp">1446749364000</long>
                   <int name="versionCount">1</int>
               </doc>
               <doc>
                   <str name="a">stdlib-guice-hibernate</str>
                   <arr name="ec">
                       <str>-sources.jar</str>
                       <str>-javadoc.jar</str>
                       <str>.jar</str>
                       <str>.pom</str>
                   </arr>
                   <str name="g">com.peterphi.std.guice</str>
                   <str name="id">com.peterphi.std.guice:stdlib-guice-hibernate</str>
                   <str name="latestVersion">8.5.1</str>
                   <str name="p">jar</str>
                   <str name="repositoryId">central</str>
                   <arr name="text">
                       <str>com.peterphi.std.guice</str>
                       <str>stdlib-guice-hibernate</str>
                       <str>-sources.jar</str>
                       <str>-javadoc.jar</str>
                       <str>.jar</str>
                       <str>.pom</str>
                   </arr>
                   <long name="timestamp">1446645753000</long>
                   <int name="versionCount">76</int>
               </doc>
               <doc>
                   <str name="a">stdlib-guice-webapp</str>
                   <arr name="ec">
                       <str>-javadoc.jar</str>
                       <str>-sources.jar</str>
                       <str>.jar</str>
                       <str>.pom</str>
                   </arr>
                   <str name="g">com.peterphi.std.guice</str>
                   <str name="id">com.peterphi.std.guice:stdlib-guice-webapp</str>
                   <str name="latestVersion">8.5.1</str>
                   <str name="p">jar</str>
                   <str name="repositoryId">central</str>
                   <arr name="text">
                       <str>com.peterphi.std.guice</str>
                       <str>stdlib-guice-webapp</str>
                       <str>-javadoc.jar</str>
                       <str>-sources.jar</str>
                       <str>.jar</str>
                       <str>.pom</str>
                   </arr>
                   <long name="timestamp">1446645749000</long>
                   <int name="versionCount">76</int>
               </doc>
           </result>
           <lst name="spellcheck">
               <lst name="suggestions"/>
           </lst>
        </response>"""
    val responseReader = StringReader(response)
    val result = MavenCentralRepository.parse(responseReader)
    val coordinates = result.artifactCoordinates
    assertThat(coordinates).containsExactly(
      "org.sonatype.spice.inject:guice-bean:1.3.4",
      "org.sonatype.spice.inject:guice-nexus:0.1.0",
      "be.fluid-it.com.squarespace.jersey2-guice:jersey2-guice:0.10-fix",
      "com.peterphi.std.guice:stdlib-guice-hibernate:8.5.1",
      "com.peterphi.std.guice:stdlib-guice-webapp:8.5.1")
  }
}