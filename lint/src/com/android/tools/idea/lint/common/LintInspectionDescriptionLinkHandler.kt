/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.lint.common

import com.android.tools.lint.checks.BuiltinIssueRegistry
import com.android.tools.lint.detector.api.Option
import com.android.tools.lint.detector.api.TextFormat
import com.intellij.codeInsight.highlighting.TooltipLinkHandler
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor

class LintInspectionDescriptionLinkHandler : TooltipLinkHandler() {

  override fun handleLink(href: String, editor: Editor): Boolean {
    if (!isHrefLinkInfo(href)) {
      return false
    }

    val linkInfo = decodeLinkInfo(href)

    // Always open the URL, if we have it.
    linkInfo.url?.let { BrowserUtil.browse(it) }

    // Log any problems.
    linkInfo.problem?.let { LOG.warn(it) }

    // Log the URL, if we have all the necessary info.
    val url = linkInfo.url ?: return true
    val project = editor.project ?: return true
    val issueId = linkInfo.issueId ?: return true
    val issue =
      BuiltinIssueRegistry().getIssue(issueId)
        ?: AndroidLintInspectionBase.findIssueByShortName(project, issueId)
    if (issue == null) {
      LOG.warn("Could not find issue: issue id: $issueId")
      return true
    }
    LintIdeSupport.get().logTooltipLink(url, issue, project)
    return true
  }

  override fun getDescription(refSuffix: String, editor: Editor): String? {
    val issue =
      BuiltinIssueRegistry().getIssue(refSuffix)
        ?: AndroidLintInspectionBase.findIssueByShortName(editor.project, refSuffix)
        ?: return null
    val html = issue.getExplanation(TextFormat.HTML)

    var description = buildString {
      append(html)
      append("<br><br>Issue id: ${issue.id}")

      val options = issue.getOptions()
      if (options.isNotEmpty()) {
        append("<br><br>${Option.describe(options, TextFormat.HTML, true)}")
      }

      val urls = issue.moreInfo
      if (urls.isNotEmpty()) {
        append("<br><br>More info:<br>")
        for (url in urls) {
          append("<a href=\"${url}\">${url}</a><br>")
        }
      }

      val vendor = issue.vendor ?: issue.registry?.vendor
      if (vendor != null) {
        append("<br>")
        vendor.describeInto(this, TextFormat.HTML, "")
      }
    }

    // IntelliJ seems to treat newlines in the HTML as needing to also be converted to <br> (whereas
    // Lint includes these for HTML readability, but they shouldn't add additional lines since it
    // has already added <br> as well) so strip these out.
    description = description.replace("\n", "")

    // Allow LintInspectionDescriptionLinkHandler to handle URL links, for analytics.
    description = replaceLinksInHtml(description, issue.id)

    return description
  }

  companion object {

    const val LINK_PREFIX =
      "#lint/" // Should match the codeInsight.linkHandler prefix specified in lint-plugin.xml.

    /** To separate data items encoded into a String for [handleLink]. */
    private const val LINK_INFO_SEPARATOR = "<"

    /**
     * To indicate to [handleLink] that the link was an HTTP link that we have added some data to.
     */
    private const val LINK_INFO_MARKER = "link_info"

    private val LOG = Logger.getInstance(LintInspectionDescriptionLinkHandler::class.java)

    /**
     * Returns [html] with links to URLs replaced such that [LintInspectionDescriptionLinkHandler]
     * will handle the link. We do this by prepending [LINK_PREFIX] and some other info before the
     * "http", otherwise the link is automatically handled by IntelliJ code, and just immediately
     * opens in a browser.
     *
     * The links change from:
     * ```
     * <a href="https://somelink">some link</a>
     * ```
     *
     * to:
     * ```
     * <a href="#lint/link_info<IssueId<https://somelink">some link</a>
     * ```
     */
    fun replaceLinksInHtml(html: String, issueId: String): String {
      // Give up if the issue id contains our separator string. Very unlikely, but users can
      // provide their own issue ids.
      if (issueId.contains(LINK_INFO_SEPARATOR)) return html

      return html.replace(
        "${TextFormat.A_HREF_PREFIX}http",
        "${TextFormat.A_HREF_PREFIX}${LINK_PREFIX}" +
          "${LINK_INFO_MARKER}${LINK_INFO_SEPARATOR}" +
          "${issueId}${LINK_INFO_SEPARATOR}" +
          "http"
      )
    }

    private fun isHrefLinkInfo(href: String) =
      href.startsWith("${LINK_INFO_MARKER}${LINK_INFO_SEPARATOR}")

    class LinkInfo(
      val issueId: String? = null,
      val url: String? = null,
      val problem: String? = null
    )

    fun decodeLinkInfo(href: String): LinkInfo {
      // Example:
      //  link_info<IssueId<https://somelink
      //  0         1       2
      val numParts = 3
      val parts = href.split(LINK_INFO_SEPARATOR, limit = numParts)
      if (parts.size != numParts) {
        return LinkInfo(problem = "Unexpected number of parts in href containing link info: $href")
      }

      val url = parts[2]
      val issueId = parts[1]
      return LinkInfo(issueId, url)
    }
  }
}
