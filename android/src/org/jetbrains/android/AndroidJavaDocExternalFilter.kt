/*
 * Copyright (C) 2023 The Android Open Source Project
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
package org.jetbrains.android

import com.intellij.codeInsight.javadoc.JavaDocExternalFilter
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NonNls
import java.io.BufferedReader
import java.io.Reader

internal class AndroidJavaDocExternalFilter(project: Project?) : JavaDocExternalFilter(project) {
  public override fun doBuildFromStream(url: String, input: Reader, data: StringBuilder) {
    try {
      // Looking up a method, field or constructor? If so we can use the
      // builtin support -- it works.
      if (ourAnchorSuffix.matcher(url).find()) {
        super.doBuildFromStream(url, input, data)
        return
      }

      BufferedReader(input).use { buf ->
        // Pull out the javadoc section.
        // The format has changed over time, so we need to look for different formats.
        // The document begins with a bunch of stuff we don't want to include (e.g.
        // page navigation etc); in all formats this seems to end with the following marker:
        @NonNls val startSection = "<!-- ======== START OF CLASS DATA ======== -->"
        // This doesn't appear anywhere in recent documentation,
        // but presumably was needed at one point; left for now
        // for users who have old documentation installed locally.
        @NonNls val skipHeader = "<!-- END HEADER -->"
        data.append(HTML)
        var read: String?
        do {
          read = buf.readLine().trimEnd()
        } while (read != null && !read.contains(startSection))
        if (read == null) {
          data.delete(0, data.length)
          return
        }
        if (read.isNotBlank()) data.append(read).append("\n")

        // Read until we reach the class overview (if present); copy everything until we see the
        // optional marker skipHeader.
        var skip = false
        while (
          buf.readLine().also { read = it.trimEnd() } !=
            null && // Old format: class description follows <h2>Class Overview</h2>
            !read!!.startsWith(
              "<h2>Class Overview"
            ) && // New format: class description follows just a <br><hr>. These
            // are luckily not present in the older docs.
            read != "<br><hr>"
        ) {
          if (read!!.contains("<table class=")) {
            // Skip all tables until the beginning of the class description
            skip = true
          } else if (read!!.startsWith("<h2 class=\"api-section\"")) {
            // Done; we've reached the section after the class description already.
            // Newer docs have no marker section or class attribute marking the
            // beginning of the class doc.
            read = null
            break
          }
          if (!skip && read!!.isNotBlank()) {
            data.append(read).append("\n")
          }
          if (read!!.contains(skipHeader)) {
            skip = true
          }
        }

        // Now copy lines until the next <h2> section.
        // In older versions of the docs format, this was a "<h2>", but in recent
        // revisions (N+) it's <h2 class="api-section">
        if (read != null) {
          data.append("<br><div>\n")
          while (
            buf.readLine().also { read = it.trimEnd() } != null &&
              !read!!.startsWith("<h2>") &&
              !read!!.startsWith("<h2 ")
          ) {
            if (read!!.isNotBlank()) data.append(read).append("\n")
          }
          data.append("</div>\n")
        }
        data.append(HTML_CLOSE)
      }
    } catch (e: Exception) {
      LOG.error(e.message, e, "URL: $url")
    }
  }

  companion object {
    private val LOG = Logger.getInstance("#org.jetbrains.android.AndroidDocumentationProvider")
  }
}
