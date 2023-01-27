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
package com.android.tools.idea.testing

import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.getSdk
import com.android.tools.idea.gradle.project.sync.internal.ProjectDumper
import com.android.tools.idea.gradle.project.sync.internal.dumpProject
import com.android.tools.idea.gradle.util.EmbeddedDistributionPaths
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil.toSystemDependentName
import com.intellij.util.PathUtil.toSystemIndependentName
import com.intellij.util.io.systemIndependentPath
import org.jetbrains.annotations.SystemIndependent
import java.io.File

typealias ProjectDumpAction = (project: Project, projectDumper: ProjectDumper) -> Unit

/**
 * Returns a human readable environment independent stable representation of the current structure of the project that can be used in tests
 * to ensure that no unintended changes are accidentally introduced to projects set up by sync/import/etc.
 */
fun Project.saveAndDump(
  additionalRoots: Map<String, File> = emptyMap(),
  dumpToAction: ProjectDumpAction = { project, projectDumper -> projectDumper.dumpProject(project) }
): String {
  ApplicationManager.getApplication().saveAll()
  val dumper = ProjectDumper(
    androidSdk = getSdk().toFile(),
    offlineRepos = getOfflineM2Repositories(),
    additionalRoots = additionalRoots,
    devBuildHome = TestUtils.getWorkspaceRoot().toFile(),
    projectJdk = ProjectRootManager.getInstance(this).projectSdk,
  )

  dumpToAction(this, dumper)
  return dumper.toString()
}

private fun getOfflineM2Repositories(): List<File> =
  (EmbeddedDistributionPaths.getInstance().findAndroidStudioLocalMavenRepoPaths() + AndroidGradleTests.getLocalRepositoryDirectories())
    .map { File(FileUtil.toCanonicalPath(it.absolutePath)) }

fun normalizeHtmlForTests(project: Project, doc: String): String {
  return doc
    .replacePath(TestUtils.resolveWorkspacePath("").systemIndependentPath, "{ROOT}")
    .replacePath(project.basePath!!, "{PROJECT}")
    .replacePath(toSystemIndependentName(FileUtil.getTempDirectory()), "{TMP}")
    .replace("<BR/>", "<BR/>\n")
    .replace("</tr>", "</tr>\n")
    .replace("</td>", "</td>\n")
    .replace("</table>", "</table>\n")
    .replace("<html>", "<html>\n")
    .replace("<body>", "<body>\n")
    .replace("</html>", "</html>\n")
    .replace("</body>", "</body>\n")
    .trim()
}

private fun String.replacePath(path: @SystemIndependent String, replacement: String): String {
  return this
    .replace("/$path", replacement)
    .replace(path, replacement)
    .replace(toSystemDependentName(path), replacement)
}
