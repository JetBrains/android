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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.structure.configurables.issues.IssuesByTypeAndTextComparator
import com.android.tools.idea.gradle.structure.model.PsPath.TexType.PLAIN_TEXT
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath
import com.google.common.collect.HashMultimap
import java.util.*
import javax.annotation.concurrent.GuardedBy

class PsIssueCollection {
  private val lock = Any()

  @GuardedBy("lock")
  private val myIssues = HashMultimap.create<PsPath, PsIssue>()

  val values: List<PsIssue> get() = synchronized(lock) { myIssues.values().toList() }

  val isEmpty: Boolean get() = synchronized(lock) { myIssues.isEmpty }

  fun add(issue: PsIssue) {
    val path = issue.path
    synchronized(lock) {
      myIssues.put(path, issue)
      for (parent in path.parents) {
        myIssues.put(parent, issue)
      }
    }
  }

  fun findIssues(model: PsModel, comparator: Comparator<PsIssue>?): List<PsIssue> =
    when (model) {
      is PsModule -> PsModulePath(model)
      is PsLibraryDependency -> PsLibraryDependencyNavigationPath(model)
      else -> null
    }
      ?.let { findIssues(it, comparator) }
      .orEmpty()


  fun findIssues(path: PsPath, comparator: Comparator<PsIssue>?): List<PsIssue> =
    synchronized(lock) {
      myIssues.get(path).toList()
    }
      .let {
        if (comparator != null) it.sortedWith(comparator) else it
      }

  fun getValues(pathType: Class<out PsPath>): List<PsIssue> =
    synchronized(lock) {
      myIssues
        .entries()
        .mapNotNull { (k, v) -> if (pathType.isInstance(k)) v else null }
        .distinct()
        .toList()
    }

  fun remove(type: PsIssueType) {
    synchronized(lock) {
      myIssues
        .entries()
        .filter { (_, issue) -> issue.type == type }
        .forEach { (path, issue) -> myIssues.remove(path, issue) }
    }
  }
}

fun getTooltipText(issues: List<PsIssue>, includePath: Boolean): String? {
  if (issues.isEmpty()) {
    return null
  }

  val sorted = issues.sortedWith(IssuesByTypeAndTextComparator.INSTANCE)

  val useBullets = issues.size > 1

  val lines = sorted
    .map {
      var line = it.text
      if (includePath) {
        val path = it.path.toText(PLAIN_TEXT)
        if (!path.isEmpty()) {
          line = "$path: $line"
        }
      }
      if (useBullets) {
        line = "<li>$line</li>"
      }
      line
    }
    // Removed duplicated lines.
    .distinct()

  return buildString {
    append("<html><body>")
    if (useBullets) {
      append("<ul>")
    }
    val issueCount = lines.size
    var problems = 0

    var tooManyMessages = false

    for ((index, line) in lines.withIndex()) {
      append(line)
      if (!useBullets) {
        append("<br>")
      }
      problems++

      if (index > 9 && issueCount > 12) {
        if (useBullets) {
          append("</ul>")
        }
        append(issueCount - problems).append(" more messages...<br>")
        tooManyMessages = true
        break
      }
    }
    if (useBullets && !tooManyMessages) {
      append("</ul>")
    }
    append("</body></html>")
  }
}
