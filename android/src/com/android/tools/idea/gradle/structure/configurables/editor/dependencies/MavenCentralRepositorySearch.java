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
import com.intellij.util.io.HttpRequests.Request;
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

class MavenCentralRepositorySearch extends ArtifactRepositorySearch {
  @Override
  boolean supportsPagination() {
    return true;
  }

  @Override
  @NotNull
  SearchResult startSearch(@NotNull String artifactName, int rows, int start) throws IOException {
    // This query searches for artifacts with name equal to the passed text.
    String request = String.format("https://search.maven.org/solrsearch/select?rows=1$%d&start=2$%d&wt=xml&q=a:3$%s", rows, start,
                                   artifactName);
    return HttpRequests.request(request).accept("application/xml").connect(new RequestProcessor<SearchResult>() {
      @Override
      public SearchResult process(@NotNull Request request) throws IOException {
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
  SearchResult parse(@NotNull Reader response) throws JDOMException, IOException {
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

    return new SearchResult(data, totalFound);
  }
}
