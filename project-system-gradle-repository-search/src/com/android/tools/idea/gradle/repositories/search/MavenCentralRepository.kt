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
import com.intellij.util.io.encodeUrlQueryParameter
import org.jdom.Element
import org.jdom.JDOMException
import java.io.IOException
import java.io.Reader

object MavenCentralRepository : ArtifactRepository(PROJECT_STRUCTURE_DIALOG_REPOSITORY_MAVEN_CENTRAL) {
  override val name: String = "Maven Central"
  override val isRemote: Boolean = true

  override fun doSearch(request: SearchRequest): SearchResult =
    HttpRequests
      .request(createRequestUrl(request))
      .accept("application/xml")
      .connect {
        try {
          parse(it.reader)
        }
        catch (e: JDOMException) {
          throw IOException("Failed to parse request: $it", e)
        }
      }

  @VisibleForTesting
  fun parse(response: Reader): SearchResult {
    /*
    Sample response:

    <response>
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
    </result>
    </response>
    */

    fun Element.findStringAttribute(name: String): String? = this.getChildren("str").find { it.getAttributeValue("name") == name }?.textTrim

    val result =
      load(response)
        ?.getChild("result")
        ?.getChildren("doc")
        ?.mapNotNull { docElement ->
          val id = docElement.findStringAttribute("id")
          val latestVersion = docElement.findStringAttribute("latestVersion")?.let { Version.parse(it) }
          if (id.isNullOrEmpty() || latestVersion == null) return@mapNotNull null
          id.split(':').takeIf { it.size == 2 }?.let { FoundArtifact(MavenCentralRepository.name, it[0], it[1], latestVersion) }
        }
      ?: listOf()

    return SearchResult(result)
  }

  @VisibleForTesting
  fun createRequestUrl(request: SearchRequest): String = buildString {
    fun String.escapeQueryExpression() = this

    val queryGroupId = request.query.groupId?.takeUnless { it.isBlank() }
    val queryArtifactId = request.query.artifactName?.takeUnless { it.isBlank() }
    val query =
      listOfNotNull(queryGroupId?.let { "g:${it.escapeQueryExpression()}" },
                    queryArtifactId?.let { "a:${it.escapeQueryExpression()}" })
        .joinToString(separator = " AND ")
        .encodeUrlQueryParameter()

    append("https://search.maven.org/solrsearch/select?")
    append("rows=${request.rowCount}&")
    append("start=${request.start}&")
    append("wt=xml&")
    append("q=$query")
  }
}
