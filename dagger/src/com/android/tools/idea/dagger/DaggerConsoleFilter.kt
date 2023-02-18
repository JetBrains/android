/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.dagger

import com.android.tools.idea.dagger.DaggerConsoleFilter.Companion.ERROR_PREFIX
import com.android.tools.idea.dagger.DaggerConsoleFilter.Companion.FQCN_WITH_METHOD
import com.android.tools.idea.flags.StudioFlags
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.PsiNavigateUtil

/**
 * Creates links to PsiElements from strings that matches [FQCN_WITH_METHOD] in build output for
 * Dagger error .
 *
 * Note: Implementation expects that [DaggerConsoleFilterProvider.getDefaultFilters] is called for
 * every new ConsoleView. see [com.intellij.execution.impl.ConsoleViewUtil.computeConsoleFilters].
 * Therefore Filter has internal state [isDaggerMessage].
 *
 * It also relies on the fact that every Dagger error has [ERROR_PREFIX] in message before any
 * string that needs links
 */
class DaggerConsoleFilter : Filter {
  private var isDaggerMessage = false

  companion object {
    private const val ID_PATTERN = "\\p{javaJavaIdentifierStart}\\p{javaJavaIdentifierPart}*"
    private const val FQCN_PATTERN = "$ID_PATTERN(?:\\.$ID_PATTERN)+"
    const val ERROR_PREFIX = "[Dagger/"

    // Matches in groups [full match, fully-qualified-class-name-followed-by-method, method-name,
    // fully-qualified-class-name].
    private val FQCN_WITH_METHOD =
      Regex("(?:($FQCN_PATTERN)(?:\\.($ID_PATTERN)\\())|($FQCN_PATTERN)")
  }

  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    if (StudioFlags.DAGGER_SUPPORT_ENABLED.get().not()) return null

    if (line.contains(ERROR_PREFIX)) {
      isDaggerMessage = true
    }
    if (isDaggerMessage) {
      val lineStart = entireLength - line.length
      val results = mutableListOf<Filter.Result>()
      collectLinks(line, lineStart, results)
      return Filter.Result(results)
    }
    return null
  }

  private fun collectLinks(
    line: String,
    lineStart: Int,
    results: MutableCollection<Filter.Result>
  ) {
    for (match in FQCN_WITH_METHOD.findAll(line).iterator()) {
      val link: HyperlinkInfo
      val start: Int
      val end: Int
      // The third group in FQCN_WITH_METHOD is for FQCN not followed by method.
      if (match.groups[3] != null) {
        link = createLinkToClass(match.groups[3]!!.value)
        start = match.groups[3]!!.range.first
        end = match.groups[3]!!.range.last
      } else {
        val clazz = match.groups[1]!!.value
        val method = match.groups[2]!!.value
        link = createLinkToMethod(clazz, method)
        start = match.groups[1]!!.range.first
        end = match.groups[2]!!.range.last
      }
      results.add(Filter.Result(lineStart + start, lineStart + end + 1, link))
    }
  }
}

private fun getClass(fqcn: String, project: Project): PsiClass? {
  return JavaPsiFacade.getInstance(project).findClass(fqcn, GlobalSearchScope.allScope(project))
}

private fun createLinkToClass(fqcn: String): HyperlinkInfo {
  return HyperlinkInfo { project ->
    PsiNavigateUtil.navigate(getClass(fqcn, project)?.navigationElement)
    project.service<DaggerAnalyticsTracker>().trackOpenLinkFromError()
  }
}

private fun createLinkToMethod(fqcn: String, methodName: String): HyperlinkInfo {
  return object : HyperlinkInfo {
    override fun navigate(project: Project) {
      val method: PsiMethod?
      // It could be a case when method is a constructor. It means [methodName] is a class name and
      // [fqcn] is a package/outer class.
      if (getClass("$fqcn.$methodName", project) != null) {
        method = getClass("$fqcn.$methodName", project)!!.constructors.firstOrNull()
      } else {
        method = getClass(fqcn, project)?.findMethodsByName(methodName, true)?.firstOrNull()
      }
      PsiNavigateUtil.navigate(method?.navigationElement)
      project.service<DaggerAnalyticsTracker>().trackOpenLinkFromError()
    }
  }
}

/**
 * Provides filters for console output that applicable for Dagger error messages.
 *
 * Note that it's important that we create new instance of [DaggerConsoleFilter], because it has
 * internal state.
 */
class DaggerConsoleFilterProvider : ConsoleFilterProvider {
  override fun getDefaultFilters(project: Project): Array<Filter> {
    return arrayOf(DaggerConsoleFilter())
  }
}
