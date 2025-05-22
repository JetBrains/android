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
import com.google.common.annotations.VisibleForTesting
import com.google.wireless.android.sdk.stats.PSDEvent.PSDRepositoryUsage.PSDRepository.PROJECT_STRUCTURE_DIALOG_REPOSITORY_MAVEN_CENTRAL
import com.intellij.openapi.util.JDOMUtil.load
import com.intellij.util.io.HttpRequests
import org.jdom.Element
import org.jdom.JDOMException
import java.io.IOException
import java.io.Reader
import java.net.URLEncoder

object MavenCentralRepository : ArtifactRepository(PROJECT_STRUCTURE_DIALOG_REPOSITORY_MAVEN_CENTRAL) {
  override val name: String = "Maven Central"
  override val isRemote: Boolean = true

  override fun doSearch(request: SearchRequest): SearchResult =
    when (val query = request.query) {
      is ArbitraryModulesSearchByModuleQuery, is ArbitraryModulesSearchQuery  ->
        HttpRequests
          .request(createArbitraryModulesRequestUrl(request))
          .accept("application/xml")
          .connect {
            try {
              parseArbitraryModulesResponse(it.reader)
            }
            catch (e: JDOMException) {
              throw IOException("Failed to parse request: $it", e)
            }
          }
      is SingleModuleSearchQuery ->
        HttpRequests
          .request(createSingleModuleRequestUrl(query))
          .accept("application/xml")
          .connect {
            try {
              parseSingleModuleResponse(it.reader, query)
            }
            catch (e: HttpRequests.HttpStatusException) {
              // fall back to ArbitraryModulesSearch
              val fallbackQuery = ArbitraryModulesSearchQuery(query.groupId, query.artifactName)
              val fallbackRequest = SearchRequest(fallbackQuery, request.rowCount, request.start)
              doSearch(fallbackRequest)
            }
            catch (e: JDOMException) {
              throw IOException("Failed to parse request: $it", e)
            }
          }
    }

  @VisibleForTesting
  fun parseArbitraryModulesResponse(response: Reader): SearchResult {
    /*
    Sample response:

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
      <result name="response" numFound="5" start="0">
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
        …
      </result>
    </response>
    */

    fun Element.findStringAttribute(name: String): String? = this.getChildren("str").find { it.getAttributeValue("name") == name }?.textTrim

    val result =
      load(response)
        ?.getChild("result")
        ?.getChildren("doc")
        ?.mapNotNull { docElement ->
          val group = docElement.findStringAttribute("g")
          val artifact = docElement.findStringAttribute("a")
          val version = docElement.findStringAttribute("v")?.let { Version.parse(it) }
          if (group.isNullOrBlank() || artifact.isNullOrBlank() || version == null) {
            return@mapNotNull null
          }
          FoundArtifact(name, group, artifact, version)
        }
        ?.groupBy { "${it.groupId}:${it.name}" }
        ?.map { (_, allVersions) ->
          allVersions.first().copy(unsortedVersions = allVersions.flatMap { it.unsortedVersions }.toSet())
        }
      ?: emptyList()

    return SearchResult(result)
  }

  @VisibleForTesting
  fun createArbitraryModulesRequestUrl(request: SearchRequest): String = buildString {
    fun String.escapeQueryExpression() = this

    val query = when (request.query) {
      is GroupArtifactQuery -> {
        val queryGroupId = request.query.groupId?.takeUnless { it.isBlank() }
        val queryArtifactId = request.query.artifactName?.takeUnless { it.isBlank() }
        URLEncoder.encode(
          listOfNotNull(queryGroupId?.let { "g:${it.escapeQueryExpression()}" }, queryArtifactId?.let { "a:${it.escapeQueryExpression()}" })
            .joinToString(separator = " AND "),
          Charsets.UTF_8)!!
      }

      is ModuleQuery ->
        URLEncoder.encode(request.query.module.let { "id:${it.escapeQueryExpression()}" }, Charsets.UTF_8)!!

    }
    append("https://search.maven.org/solrsearch/select?")
    append("rows=${request.rowCount}&")
    append("start=${request.start}&")
    append("wt=xml&")
    append("q=$query&")
    append("core=gav")
  }

  @VisibleForTesting
  fun parseSingleModuleResponse(response: Reader, query: SingleModuleSearchQuery): SearchResult {
    val root = load(response) ?: return SearchResult(listOf())
    if (query.groupId != root.getChild("groupId").textTrim) return SearchResult(listOf())
    if (query.artifactName != root.getChild("artifactId").textTrim) return SearchResult(listOf())

    val result = root
      .getChild("versioning")
      ?.getChild("versions")
      ?.getChildren("version")
      ?.map { Version.parse(it.textTrim) }
      ?.let { FoundArtifact(name, query.groupId, query.artifactName, it) }

    return result?.let { SearchResult(listOf(result)) } ?: SearchResult(listOf())
  }

  @VisibleForTesting
  fun createSingleModuleRequestUrl(query: SingleModuleSearchQuery): String = buildString {
    append("https://repo.maven.apache.org/maven2/")
    append(query.groupId.replace('.', '/'))
    append("/")
    append(query.artifactName)
    append("/maven-metadata.xml")
  }
}