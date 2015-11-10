/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.editor.dependencies;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.io.HttpRequests.RequestProcessor;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

import static com.android.SdkConstants.GRADLE_PATH_SEPARATOR;
import static com.intellij.openapi.util.JDOMUtil.load;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * Searches Maven Central using its <a href="http://search.maven.org/ajaxsolr/images/MavenCentralAPIGuide.pdf">REST API</a>.
 */
class MavenCentralRepositorySearch extends ArtifactRepositorySearch {
  @Override
  @NotNull
  String getName() {
    return "Maven Central";
  }

  @Override
  boolean supportsPagination() {
    return true;
  }

  @Override
  @NotNull
  SearchResult start(@NotNull Request request) throws IOException {
    // This query searches for artifacts with name equal to the passed text.
    String url = createRequestUrl(request);
    return HttpRequests.request(url).accept("application/xml").connect(new RequestProcessor<SearchResult>() {
      @Override
      public SearchResult process(@NotNull HttpRequests.Request request) throws IOException {
        try {
          return parse(request.getReader());
        }
        catch (JDOMException e) {
          String msg = String.format("Failed to parse request '%1$s'", request);
          throw new IOException(msg, e);
        }
      }
    });
  }

  @VisibleForTesting
  @NotNull
  static String createRequestUrl(@NotNull Request request) {
    StringBuilder buffer = new StringBuilder();
    buffer.append("https://search.maven.org/solrsearch/select?")
          .append("rows=").append(request.rows).append("&")
          .append("start=").append(request.start).append("&")
          .append("wt=xml&")
          .append("q=");
    String groupId = request.groupId;
    if (isNotEmpty(groupId)) {
      buffer.append("g:\"").append(groupId).append("\"+AND+");
    }
    buffer.append("a:\"").append(request.artifactName).append("\"");
    return buffer.toString();
  }

  @VisibleForTesting
  @NotNull
  SearchResult parse(@NotNull Reader response) throws JDOMException, IOException {
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

    List<String> data = Lists.newArrayList();
    int totalFound = 0;

    Element root = load(response);
    Element result = root.getChild("result");
    if (result != null) {
      String found = result.getAttributeValue("numFound");
      if (isNotEmpty(found)) {
        try {
          totalFound = Integer.parseInt(found);
        }
        catch (NumberFormatException ignored) {
        }
      }
      for (Element doc : result.getChildren("doc")) {
        String id = null;
        String latestVersion = null;
        for (Element str : doc.getChildren("str")) {
          String name = str.getAttributeValue("name");
          if ("id".equals(name)) {
            id = str.getTextTrim();
          }
          else if ("latestVersion".equals(name)) {
            latestVersion = str.getTextTrim();
          }
          if (isNotEmpty(id) && isNotEmpty(latestVersion)) {
            data.add(id + GRADLE_PATH_SEPARATOR + latestVersion);
            break;
          }
        }
      }
    }

    return new SearchResult(getName(), data, totalFound);
  }
}
