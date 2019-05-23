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

import com.android.SdkConstants.EXT_GRADLE
import com.android.sdklib.SdkVersionInfo
import com.android.sdklib.SdkVersionInfo.HIGHEST_KNOWN_STABLE_API
import com.android.tools.idea.sdk.AndroidSdks
import com.android.utils.usLocaleCapitalize
import com.google.common.base.CaseFormat
import com.google.common.base.Charsets
import com.google.common.base.Strings.emptyToNull
import com.google.common.io.Files
import com.intellij.ide.impl.ProjectViewSelectInPaneTarget
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.StdFileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine
import org.jetbrains.android.uipreview.AndroidEditorSettings
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import kotlin.math.max

/**
 * Utility methods pertaining to templates for projects, modules, and activities.
 */
object TemplateUtils {
  private val LOG = Logger.getInstance("#org.jetbrains.android.templates.DomUtilities")
  private val WINDOWS_NEWLINE = Pattern.compile("\r\n")

  /**
   * Returns a list of known API names

   * @return a list of string API names, starting from 1 and up through the maximum known versions (with no gaps) */
  val knownVersions: List<String>
    @JvmStatic get() {
      val sdkData = AndroidSdks.getInstance().tryToChooseAndroidSdk()!!
      val targets = sdkData.targets.filter { it.isPlatform && !it.version.isPreview }

      val targetLevels = targets.map {it.version.apiLevel}
      val maxApi = max(HIGHEST_KNOWN_STABLE_API, targetLevels.max() ?: 0)

      return (1..maxApi).map { SdkVersionInfo.getAndroidName(it) }
    }

  /**
   * Creates a Java class name out of the given string, if possible.
   * For example, "My Project" becomes "MyProject", "hello" becomes "Hello", "Java's" becomes "Javas", and so on.
   *
   * @param string the string to be massaged into a Java class
   * @return the string as a Java class, or null if a class name could not be extracted
   */
  @JvmStatic
  fun extractClassName(string: String): String? {
    val javaIdentifier = string.dropWhile { !Character.isJavaIdentifierStart(it.toUpperCase()) }.filter(Character::isJavaIdentifierPart)
    return emptyToNull(javaIdentifier.usLocaleCapitalize())
  }

  /**
   * Strips the given suffix from the given file, provided that the file name ends with the suffix.
   *
   * @param file   the file to strip from
   * @param suffix the suffix to strip out
   * @return the file without the suffix at the end
   */
  @JvmStatic
  fun stripSuffix(file: File, suffix: String): File {
    if (file.name.endsWith(suffix)) {
      val name = file.name.removeSuffix(suffix)
      return file.parentFile?.let { File(it, name) } ?: File(name)
    }

    return file
  }

  /**
   * Converts a CamelCase word into an underlined_word
   *
   * @param string the CamelCase version of the word
   * @return the underlined version of the word
   */
  @JvmStatic
  fun camelCaseToUnderlines(string: String): String = CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, string)

  /**
   * Converts an underlined_word into a CamelCase word
   *
   * @param string the underlined word to convert
   * @return the CamelCase version of the word
   */
  @JvmStatic
  fun underlinesToCamelCase(string: String): String = CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, string)

  /**
   * Returns the element children of the given element
   *
   * @param element the parent element
   * @return a list of child elements, possibly empty but never null
   */
  @JvmStatic
  fun getChildren(element: Element): List<Element> {
    // Convenience to avoid lots of ugly DOM access casting
    val children = element.childNodes

    return (0 until children.length).mapNotNull {
      val node = children.item(it)
      if (node.nodeType == Node.ELEMENT_NODE) node as Element else null
    }
  }

  @JvmStatic
  fun reformatAndRearrange(project: Project, files: Iterable<File>) {
    WriteCommandAction.runWriteCommandAction(project) {
      files.filter { it.isFile }.forEach {
        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(it)!!
        reformatAndRearrange(project, virtualFile)
      }
    }
  }

  /**
   * Reformats and rearranges the part of the File concerning the PsiElement received
   *
   * @param project    The project which contains the given element
   * @param psiElement The element to be reformated and rearranged
   */
  @JvmStatic
  fun reformatAndRearrange(project: Project, psiElement: PsiElement) =
    reformatAndRearrange(project, psiElement.containingFile.virtualFile, psiElement, true)

  /**
   * Reformats and rearranges the file. By default reformats entire file, but may reformat part of it
   *
   * Note: reformatting the PSI file requires that this be wrapped in a write command.
   *
   * @param project            The project which contains the given element
   * @param virtualFile        Virtual file to be reformatted and rearranged
   * @param psiElement         The element in the file to be reformatted and rearranged
   * @param keepDocumentLocked True if the document will still be modified in the same write action
   */
  @JvmStatic
  @JvmOverloads
  fun reformatAndRearrange(project: Project,
                                   virtualFile: VirtualFile,
                                   psiElement: PsiElement? = null,
                                   keepDocumentLocked: Boolean = false) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    if (virtualFile.extension == EXT_GRADLE) {
      // Do not format Gradle files. Otherwise we get spurious "Gradle files have changed since last project sync" warnings that make UI
      // tests flaky.
      return
    }

    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                   ?: return // The file could be a binary file with no editing support...

    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    psiDocumentManager.commitDocument(document)

    val psiFile = psiDocumentManager.getPsiFile(document) ?: return

    var textRange = if (psiElement == null) psiFile.textRange else psiElement.textRange
    CodeStyleManager.getInstance(project).reformatRange(psiFile, textRange.startOffset, textRange.endOffset)

    // The textRange of psiElement in the file can change after reformatting
    textRange = if (psiElement == null) psiFile.textRange else psiElement.textRange
    psiDocumentManager.doPostponedOperationsAndUnblockDocument(document)
    ServiceManager.getService(project, ArrangementEngine::class.java).arrange(psiFile, setOf(textRange))

    if (keepDocumentLocked) {
      psiDocumentManager.commitDocument(document)
    }
  }

  /**
   * Opens the specified files in the editor
   *
   * @param project The project which contains the given file.
   * @param files   The files on disk.
   * @param select  If true, select the last (topmost) file in the project view
   * @return true if all files were opened
   */
  @JvmStatic
  fun openEditors(project: Project, files: Collection<File>, select: Boolean): Boolean {
    if (files.isEmpty()) {
      return false
    }
    var last: VirtualFile? = null
    val result = files.filter(File::exists).any {
      val vFile = VfsUtil.findFileByIoFile(it, true) ?: return@any false
      return !openEditor(project, vFile).also {
        last = vFile
      }
    }

    if (select && last != null) {
      selectEditor(project, last!!)
    }

    return result
  }

  /**
   * Opens the specified file in the editor
   *
   * @param project The project which contains the given file.
   * @param vFile   The file to open
   */
  @JvmStatic
  fun openEditor(project: Project, vFile: VirtualFile): Boolean {
    val descriptor: OpenFileDescriptor =
      if (vFile.fileType === StdFileTypes.XML && AndroidEditorSettings.getInstance().globalState.isPreferXmlEditor) {
        OpenFileDescriptor(project, vFile, 0)
      }
      else {
        OpenFileDescriptor(project, vFile)
      }
    return FileEditorManager.getInstance(project).openEditor(descriptor, true).isNotEmpty()
  }

  /**
   * Selects the specified file in the project view.
   * **Note:** Must be called with read access.
   *
   * @param project the project
   * @param file    the file to select
   */
  @JvmStatic
  fun selectEditor(project: Project, file: VirtualFile) {
    ApplicationManager.getApplication().assertReadAccessAllowed()

    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
    val currentPane = ProjectView.getInstance(project).currentProjectViewPane ?: return

    ProjectViewSelectInPaneTarget(project, currentPane, true).select(psiFile, false)
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
   * Replaces the contents of the given file with the given string. Outputs
   * text in UTF-8 character encoding. The file is created if it does not
   * already exist.
   */
  @Throws(IOException::class)
  @JvmStatic
  fun writeTextFile(requestor: Any, contents: String?, to: File) {
    if (contents == null) {
      return
    }
    var vf = LocalFileSystem.getInstance().findFileByIoFile(to)
    if (vf == null) {
      // Creating a new file
      val parentDir = checkedCreateDirectoryIfMissing(to.parentFile)
      vf = parentDir.createChildData(requestor, to.name)
    }
    val document = FileDocumentManager.getInstance().getDocument(vf)
    if (document != null) {
      document.setText(WINDOWS_NEWLINE.matcher(contents).replaceAll("\n"))
      FileDocumentManager.getInstance().saveDocument(document)
    }
    else {
      vf.setBinaryContent(contents.toByteArray(Charsets.UTF_8), -1, -1, requestor)
    }
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
   * Returns true iff the given file has the given extension (with or without .)
   */
  @JvmStatic
  fun hasExtension(file: File, extension: String): Boolean =
    Files.getFileExtension(file.name).equals(extension.trimStart { it == '.' }, ignoreCase = true)
}
