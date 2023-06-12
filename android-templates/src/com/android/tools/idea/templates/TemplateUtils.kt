/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.templates

import com.android.annotations.concurrency.UiThread
import com.android.ide.common.repository.GradleCoordinate
import com.android.ide.common.repository.GradleCoordinate.parseCoordinateString
import com.android.tools.idea.gradle.repositories.RepositoryUrlManager
import com.android.utils.usLocaleCapitalize
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import org.jetbrains.android.uipreview.EditorUtil.openEditor
import org.jetbrains.android.uipreview.EditorUtil.selectEditor
import java.io.File
import java.io.IOException
import java.security.InvalidParameterException

/**
 * Utility methods pertaining to templates for projects, modules, and activities.
 */
object TemplateUtils {
  private val LOG = Logger.getInstance("#org.jetbrains.android.templates.DomUtilities")

  /**
   * Opens the specified files in the editor
   *
   * @param project The project which contains the given file.
   * @param files   The files on disk.
   * @param select  If true, select the last (topmost) file in the project view
   */
  @JvmStatic
  fun openEditors(project: Project, files: Collection<File>, select: Boolean) {
    var last: VirtualFile? = null

    files.filter(File::exists).mapNotNull {
      VfsUtil.findFileByIoFile(it, true)
    }.forEach {
      last = it
      openEditor(project, it)
    }

    if (select && last != null) {
      selectEditor(project, last!!)
    }
  }

  /**
   * Returns the contents of `file`, or `null` if an [IOException] occurs.
   * If an [IOException] occurs and `warnIfNotExists` is `true`, logs a warning.
   *
   * @throws AssertionError if `file` is not absolute
   */
  @JvmOverloads
  @JvmStatic
  fun readTextFromDisk(file: File, warnIfNotExists: Boolean = true): String? {
    assert(file.isAbsolute)
    return try {
      Files.asCharSource(file, Charsets.UTF_8).read()
    }
    catch (e: IOException) {
      if (warnIfNotExists) {
        LOG.warn(e)
      }
      null
    }
  }

  /**
   * Reads the given file as text (or the current contents of the edited buffer of the file, if open and not saved).
   *
   * @param file The file to read.
   * @return the contents of the file as text, or null if for some reason it couldn't be read
   */
  @JvmStatic
  fun readTextFromDocument(project: Project, file: File): String? {
    assert(project.isInitialized)
    val vFile = LocalFileSystem.getInstance().findFileByIoFile(file)
    if (vFile == null) {
      LOG.debug("Cannot find file " + file.path + " in the VFS")
      return null
    }
    return readTextFromDocument(project, vFile)
  }

  /**
   * Reads the given file as text (or the current contents of the edited buffer of the file, if open and not saved).
   *
   * @param file The file to read.
   * @return the contents of the file as text, or null if for some reason it couldn't be read
   */
  @JvmStatic
  fun readTextFromDocument(project: Project, file: VirtualFile): String? {
    assert(project.isInitialized)
    return ApplicationManager.getApplication().runReadAction(Computable<String> {
      val document = FileDocumentManager.getInstance().getDocument(file)
      document?.text
    })
  }

  /**
   * Creates a directory for the given file and returns the VirtualFile object.
   *
   * @return virtual file object for the given path. It can never be null.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun checkedCreateDirectoryIfMissing(directory: File): VirtualFile =
    WriteCommandAction.runWriteCommandAction(null, ThrowableComputable<VirtualFile, IOException> {
      VfsUtil.createDirectoryIfMissing(directory.absolutePath) ?: throw IOException("Unable to create " + directory.absolutePath)
    })

  /**
   * Find the first parent directory that exists and check if this directory is writeable.
   *
   * @throws IOException if the directory is not writable.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun checkDirectoryIsWriteable(directory: File) {
    var d = directory
    while (!d.exists() || !d.isDirectory) {
      d = d.parentFile
    }
    if (!d.canWrite()) {
      throw IOException("Cannot write to folder: " + d.absolutePath)
    }
  }

  /**
   * [VfsUtil.copyDirectory] messes up the undo stack, most likely by trying to create a directory even if it already exists.
   * This is an undo-friendly replacement.
   *
   * Note: this method should be run inside write action.
   */
  @JvmStatic
  @JvmOverloads
  @UiThread
  fun copyDirectory(src: VirtualFile, dest: File, copyFile: (file: VirtualFile, src: VirtualFile, destination: File) -> Boolean = ::copyFile) {
    VfsUtilCore.visitChildrenRecursively(src, object : VirtualFileVisitor<Any>() {
      override fun visitFile(file: VirtualFile): Boolean {
        try {
          return copyFile(file, src, dest)
        }
        catch (e: IOException) {
          throw VisitorException(e)
        }
      }
    }, IOException::class.java)!!
  }

  /**
   * Copies a file or a directory. Returns true if it was copied, otherwise false.
   */
  @JvmStatic
  @UiThread
  private fun copyFile(fileToCopy: VirtualFile, parent: VirtualFile, destination: File): Boolean {
    val relativePath = VfsUtilCore.getRelativePath(fileToCopy, parent, File.separatorChar)
    check(relativePath != null) { "${fileToCopy.path} is not a child of $parent" }
    if (fileToCopy.isDirectory) {
      checkedCreateDirectoryIfMissing(File(destination, relativePath))
      return true
    }
    val target = File(destination, relativePath)
    val toDir = checkedCreateDirectoryIfMissing(target.parentFile)
    val targetVf = LocalFileSystem.getInstance().findFileByIoFile(target)
    if (targetVf?.exists() == true) {
      return false
    }
    VfsUtilCore.copyFile(this, fileToCopy, toDir)
    return true
  }

  /**
   * Returns true iff the given file has the given extension (with or without .)
   */
  @JvmStatic
  fun hasExtension(file: File, extension: String): Boolean =
    Files.getFileExtension(file.name).equals(extension.trimStart { it == '.' }, ignoreCase = true)
}

/**
 * Attempts to resolve dynamic versions (e.g. "2.+") to specific versions from the repository.
 *
 * @param minRev the minimum revision to accept
 * @see RepositoryUrlManager.resolveDynamicCoordinate
 */
fun resolveDependency(repo: RepositoryUrlManager, dependency: String, minRev: String? = null): GradleCoordinate {
  val coordinate = parseCoordinateString(dependency) ?: throw InvalidParameterException("Invalid dependency: $dependency")

  val minCoordinate = if (minRev == null) coordinate else GradleCoordinate(coordinate.groupId, coordinate.artifactId, minRev)

  // If we cannot resolve the dependency on the repo, return the at least the min requested
  val resolved = repo.resolveDynamicCoordinate(coordinate, null, null) ?: return minCoordinate

  return maxOf(resolved, minCoordinate, GradleCoordinate.COMPARE_PLUS_LOWER)
}

fun getAppNameForTheme(appName: String): String {
  val result = appName
    .split(" ")
    .joinToString("") {
      it.usLocaleCapitalize()
    }
  // The characters need to be valid characters for Kotlin because name is used for resource names
  return StringUtil.sanitizeJavaIdentifier(result).takeIf { it.isNotEmpty() } ?: "App"
}