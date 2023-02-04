/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.lint

import com.android.SdkConstants.DOT_JAVA
import com.android.SdkConstants.DOT_KT
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import com.android.tools.idea.lint.common.AndroidLintInspectionBase.LINT_INSPECTION_PREFIX
import com.android.tools.idea.lint.common.LintIdeIssueRegistry
import com.android.tools.idea.lint.common.LintIdeClient.SUPPORT_CLASS_FILES
import com.android.tools.lint.checks.CheckResultDetector
import com.android.tools.lint.checks.PermissionDetector
import com.android.tools.lint.checks.ViewTypeDetector
import com.android.tools.lint.client.api.LintClient
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.TextFormat
import com.android.utils.SdkUtils
import com.android.utils.XmlUtils.toXmlAttributeValue
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import com.google.common.collect.Sets
import com.intellij.psi.PsiElement
import org.jetbrains.android.AndroidTestCase
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.junit.Assert
import java.io.File
import java.lang.String.CASE_INSENSITIVE_ORDER
import java.lang.reflect.Modifier
import java.util.ArrayList
import java.util.Calendar
import java.util.Comparator
import java.util.Locale
import kotlin.text.Charsets.UTF_8

/**
 * Ensures that all relevant lint checks are available and registered
 * <p>
 * TODO: For inspections that have safe fixes, mark the inspections with the interface
 * com.intellij.codeInspection.CleanupLocalInspectionTool . However, that also requires
 * it to provide a LocalInspectionTool via getSharedLocalInspectionToolWrapper.
 */
class LintInspectionRegistrationTest : AndroidTestCase() {
  init {
    LintClient.clientName = LintClient.CLIENT_UNIT_TESTS
  }

  fun testAllLintChecksRegistered() {
    assertTrue(
      "Not all lint checks have been registered. See the standard output for instructions on how to register the missing checks.",
      checkAllLintChecksRegistered())
  }

  companion object {
    private fun findSourceTree(): File? {
      val sourceTree =
        System.getenv(ADT_SOURCE_TREE)
        ?: System.getProperty(ADT_SOURCE_TREE)
        // Tip: you can temporarily set your own path here:
        // ?: "/your/path"
        ?: return null

      return if (sourceTree.isNotBlank()) {
        File(sourceTree).apply {
          if (!File(this, ".repo").isDirectory) {
            Assert.fail("Invalid directory $this: should be pointing to the root of a tools checkout directory")
          }
        }
      } else null
    }

    private const val ADT_SOURCE_TREE = "ADT_SOURCE_TREE"
    private var ourDone = false

    /** Check that all issues have been registered */
    private fun checkAllLintChecksRegistered(): Boolean {
      if (ourDone) {
        return true
      }
      ourDone = true

      val root = findSourceTree()

      val fullRegistry = LintIdeIssueRegistry.get()
      val allIssues = fullRegistry.issues

      val missing = computeMissingIssues()
      if (missing.isEmpty()) {
        return true
      }

      // Spit out registration information for the missing elements
      val sb = StringBuilder(1000)
      sb.append("Missing registration for ").append(missing.size).append(" issues (out of a total issue count of ")
        .append(allIssues.size).append(")")
      if (root == null) {
        sb.append(
          "\n***If you set the environment variable " + ADT_SOURCE_TREE + " (or set it as a system property in the test run " +
          "config) this test can automatically create/edit the files for you!***\n")
      }
      insertMissingRegistrations(missing, root, sb, androidSpecific = false)
      insertMissingRegistrations(missing, root, sb, androidSpecific = true)
      writeMissingInspectionClasses(sb, missing, root, androidSpecific = false)
      writeMissingInspectionClasses(sb, missing, root, androidSpecific = true)
      insertMissingMessages(sb, missing, root, androidSpecific = false)
      insertMissingMessages(sb, missing, root, androidSpecific = true)
      sb.append("\nAdded registrations for ").append(missing.size).append(" issues (out of a total issue count of ")
        .append(allIssues.size).append(")\n")
      println(sb.toString())
      return false
    }

    private fun insertMissingMessages(sb: StringBuilder,
                                      missing: List<Issue>,
                                      root: File?,
                                      androidSpecific: Boolean) {
      val suffix = if (androidSpecific)
        "tools/adt/idea/android-lint/resources/messages/AndroidLintBundle.properties"
      else
        "tools/adt/idea/lint/resources/messages/LintBundle.properties"
      if (root == null) {
        sb.append("\nAdd to $suffix (and try to preserve alphabetical order):\n")
      }
      for (issue in missing) {
        if (issue.isAndroidSpecific() != androidSpecific) {
          continue
        }
        val messageKey = getMessageKey(issue)
        val desc = StringBuilder()
        desc.append("android.lint.inspections.").append(messageKey).append("=")
          .append(SdkUtils.escapePropertyValue(getBriefDescription(issue))).append("\n")
        var performed = false
        if (root != null) {
          val insert = desc.toString()
          // Try to make the edit directly
          val propertyFile = File(root, suffix)
          if (propertyFile.exists()) {
            val original = propertyFile.readText(UTF_8)
            if (original.contains("$messageKey=")) {
              continue
            }
            var begin = 0
            while (true) {
              val end = original.indexOf('\n', begin)
              if (end == -1) {
                break
              }
              val line = original.substring(begin, end)
              if (line.startsWith("android.lint.inspections.") &&
                  !line.startsWith("android.lint.inspections.group.name") &&
                  !line.startsWith("android.lint.inspections.subgroup.name") &&
                  // Passed all lint keys: insert it here (must be newly last alphabetical issue)
                  line.compareTo(insert, ignoreCase = true) > 0 ||
                  line.startsWith("android.lint.fix.") ||
                  !original.contains("android.lint.inspections")) {
                performed = true
                val contents = original.substring(0, begin) + insert + original.substring(begin)
                propertyFile.writeText(contents, UTF_8)
                desc.append(" <automatically updated ").append(propertyFile.name).append(" in ").append(root).append(">\n")
                break
              }
              begin = end + 1
            }
          }
        }
        if (!performed) {
          sb.append(desc)
        }
      }
    }

    private fun writeMissingInspectionClasses(sb: StringBuilder,
                                              missing: List<Issue>,
                                              root: File?,
                                              androidSpecific: Boolean) {
      val suffix = if (androidSpecific)
        "tools/adt/idea/android-lint/src/com/android/tools/idea/lint/inspections"
      else
        "tools/adt/idea/lint/src/com/android/tools/idea/lint/common"
      if (root == null) {
        sb.append("\nCreate the following classes in $suffix:\n")
      }
      for (issue in missing) {
        if (issue.isAndroidSpecific() != androidSpecific) {
          continue
        }
        val id = issue.id
        val detectorClass = getDetectorClass(issue).name
        val detectorName = getDetectorClass(issue).simpleName
        val issueName = getIssueFieldName(issue)
        val messageKey = getMessageKey(issue)
        val code = """
/*
 * Copyright (C) ${Calendar.getInstance()[Calendar.YEAR]} The Android Open Source Project
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
package com.android.tools.idea.lint${if (androidSpecific) ".inspections" else ".common"}
${if (androidSpecific)
          """
import com.android.tools.idea.lint.common.AndroidLintInspectionBase
import $detectorClass
import com.android.tools.idea.lint.AndroidLintBundle.Companion.message
"""
        else
          """
import com.android.tools.idea.lint.common.LintBundle.Companion.message
import $detectorClass
"""}
class $LINT_INSPECTION_PREFIX${id}Inspection : AndroidLintInspectionBase(
  message("android.lint.inspections.$messageKey"), $detectorName.$issueName
)
""".trim()
        if (root != null) {
          val packageDir = File(root, suffix)
          assertTrue(packageDir.toString(), packageDir.exists())
          val to = File(packageDir, LINT_INSPECTION_PREFIX + id + "Inspection.kt")
          if (!to.isFile && !File(to.path.removeSuffix(DOT_KT) + DOT_JAVA).exists()) {
            to.writeText(code, UTF_8)
            sb.append(" <automatically created ").append(to).append(">\n")
          }
          else {
            sb.append(" <already exists: ").append(to).append(">\n")
          }
        }
        else {
          sb.append(suffix).append("/").append(LINT_INSPECTION_PREFIX).append(id).append("Inspection.kt").append(":\n")
          sb.append(code)
        }
      }
    }

    private fun insertMissingRegistrations(missing: List<Issue>,
                                           root: File?,
                                           sb: StringBuilder,
                                           androidSpecific: Boolean) {
      val suffix = if (androidSpecific)
        "tools/adt/idea/android-lint/src/META-INF/android-lint-plugin.xml"
      else
        "tools/adt/idea/lint/src/META-INF/lint-plugin.xml"
      if (root == null) {
        sb.append("\nRegister the following inspections in $suffix (and try to preserve case insensitive alphabetical order):\n")
      }
      for (issue in missing.sortedBy { it.id.toLowerCaseAsciiOnly() }) {
        if (issue.isAndroidSpecific() != androidSpecific) {
          continue
        }
        val desc = StringBuilder()
        desc.append("<globalInspection")
        if (androidSpecific) {
          desc.append(" projectType=\"Android\"")
        }
        desc.append(" hasStaticDescription=\"true\" shortName=\"")
        desc.append(LINT_INSPECTION_PREFIX)
        val id = issue.id
        desc.append(id)
        desc.append("\" groupName=\"")
        desc.append(AndroidLintInspectionBase.getGroupDisplayName(issue.category))
        desc.append("\" displayName=\"")
        desc.append(toXmlAttributeValue(issue.getBriefDescription(TextFormat.TEXT)))
        val bundle = if (androidSpecific) "AndroidLintBundle" else "LintBundle"
        desc.append("\" bundle=\"messages.$bundle\" enabledByDefault=\"")
        desc.append(issue.isEnabledByDefault())
        desc.append("\" level=\"")
        val severityString = if (issue.defaultSeverity === Severity.ERROR || issue.defaultSeverity === Severity.FATAL)
          "ERROR"
        else if (issue.defaultSeverity === Severity.WARNING)
          "WARNING"
        else
          "INFO"
        desc.append(severityString)
        val packagePrefix = if (androidSpecific)
          "com.android.tools.idea.lint.inspections."
        else
          "com.android.tools.idea.lint.common."
        desc.append("\" implementationClass=\"")
        desc.append(packagePrefix)
        desc.append(LINT_INSPECTION_PREFIX)
        desc.append(id)
        desc.append("Inspection\"/>\n")
        var performed = false
        if (root != null) {
          val insert = desc.toString()
          // Try to make the edit directly
          val plugin = File(root, suffix)
          if (plugin.exists()) {
            val original = plugin.readText(UTF_8)
            if (original.contains(insert)) {
              continue
            }
            var begin = 0
            while (true) {
              val end = original.indexOf('\n', begin)
              if (end == -1) {
                break
              }
              val line = original.substring(begin, end)
              val trimmed = line.trim()
              if (trimmed.startsWith(
                  "<globalInspection hasStaticDescription=\"true\" shortName=\"$LINT_INSPECTION_PREFIX") &&
                  trimmed.compareTo(insert,
                                    ignoreCase = true) > 0 ||  // Passed all lint registration entries: insert it here (must be newly last alphabetical issue)
                  trimmed.startsWith("<globalInspection hasStaticDescription=\"true\" shortName=\"PermissionUsageInspection\"") ||
                  trimmed.startsWith("</extensions>") ||
                  !original.contains("<globalInspection")) {
                val contents = original.substring(0, begin) + "    " + insert + original.substring(begin)
                plugin.writeText(contents, UTF_8)
                sb.append(" <automatically updated $suffix in ").append(root).append(">\n")
                performed = true
                break
              }
              begin = end + 1
            }
          }
        }
        if (!performed) {
          sb.append("    ").append(desc)
        }
      }
    }

    fun listIssuesWithQuickFixes() {
      val quickfixes: MutableSet<Issue> = HashSet(400)
      val fullRegistry = LintIdeIssueRegistry.get()
      val allIssues = fullRegistry.issues
      for (issue in allIssues) {
        if (!AndroidLintIdeIssueRegistry().isRelevant(issue)) {
          continue
        }
        val c = findInspectionClass(issue) ?: continue
        val provider = c.newInstance() as AndroidLintInspectionBase
        var hasQuickFix = true
        try {
          provider.javaClass.getDeclaredMethod("getQuickFixes", String::class.java)
        }
        catch (e1: NoSuchMethodException) {
          try {
            provider.javaClass.getDeclaredMethod("getQuickFixes", PsiElement::class.java, PsiElement::class.java,
                                                 String::class.java)
          }
          catch (e2: NoSuchMethodException) {
            hasQuickFix = false
          }
        }
        if (hasQuickFix) {
          quickfixes.add(provider.issue)
        }
      }

      println("The following inspections have quickfixes (used for Reporter.java):\n")
      val fields: MutableList<String> = Lists.newArrayListWithExpectedSize(quickfixes.size)
      val imports: MutableSet<String> = Sets.newHashSetWithExpectedSize(quickfixes.size)
      // These two are handled by the ResourceTypeInspection's quickfixes; they're
      // not handled by lint per se, but on the command line (in HTML reports) they're
      // flagged by lint, so include them in the list
      quickfixes.add(CheckResultDetector.CHECK_PERMISSION)
      quickfixes.add(PermissionDetector.MISSING_PERMISSION)
      quickfixes.add(CheckResultDetector.CHECK_RESULT)
      for (issue in quickfixes) {
        var detectorName = getDetectorClass(issue).name
        imports.add(detectorName)
        val index = detectorName.lastIndexOf('.')
        if (index != -1) {
          detectorName = detectorName.substring(index + 1)
        }
        val issueName = getIssueFieldName(issue)
        fields.add("$detectorName.$issueName")
      }
      fields.sort()
      val sortedImports = imports.sorted()
      for (cls in sortedImports) {
        println("import $cls;")
      }
      println()
      println("ourStudioFixes = Sets.newHashSet(\n    " + Joiner.on(",\n    ").join(fields) + "\n);\n")
    }

    /** Returns the known issues that have not been registered as inspections */
    private fun computeMissingIssues(): List<Issue> {
      // For some reason*, I can't just use
      //   AndroidLintInspectionBase.getInspectionShortNameByIssue
      // to iterate the available inspections from unit tests; at runtime this will enumerate all
      // the available inspections, but from unit tests (even when extending IdeaTestCase) it's empty.
      // So instead we take advantage of the knowledge that all our inspections are named in a particular
      // way from the lint issue id's, so we can use reflection to find the classes.
      // This won't catch cases if we declare a class there and forget to register in the plugin, but
      // it's better than nothing.
      //
      // *: This is probably because the inspection registration file is only depended on by
      // the android-plugin's plugin.xml file. To fix this (TODO), I likely only have to also reference it
      // from android/testSrc/META-INF/plugin.xml!
      val registered: MutableSet<String> = HashSet(400)
      val fullRegistry = LintIdeIssueRegistry.get()
      val allIssues = fullRegistry.issues
      for (issue in allIssues) {
        if (!AndroidLintIdeIssueRegistry().isRelevant(issue)) {
          continue
        }
        val c = findInspectionClass(issue) ?: continue
        val provider = c.newInstance() as AndroidLintInspectionBase
        registered.add(provider.issue.id)
      }
      val missing = ArrayList<Issue>()
      for (issue in allIssues) {
        if (!AndroidLintIdeIssueRegistry().isRelevant(issue) || registered.contains(issue.id)) {
          continue
        }
        val implementation = issue.implementation
        if (implementation.scope.contains(Scope.CLASS_FILE) ||
            implementation.scope.contains(Scope.ALL_CLASS_FILES) ||
            implementation.scope.contains(Scope.JAVA_LIBRARIES)) {
          assertFalse(SUPPORT_CLASS_FILES) // When enabled, adjust this to register class based registrations
          var isOk = false
          for (analysisScope in implementation.analysisScopes) {
            if (!analysisScope.contains(Scope.CLASS_FILE)
                && !analysisScope.contains(Scope.ALL_CLASS_FILES)
                && !analysisScope.contains(Scope.JAVA_LIBRARIES)) {
              isOk = true
              break
            }
          }
          if (!isOk) {
            println("Skipping issue $issue because it requires classfile analysis. Consider rewriting in IDEA.")
            continue
          }
        }
        missing.add(issue)
      }

      missing.sortWith(Comparator { issue1: Issue, issue2: Issue -> CASE_INSENSITIVE_ORDER.compare(issue1.id, issue2.id) })

      return missing
    }

    private fun findInspectionClass(issue: Issue): Class<*>? {
      val base = "com.android.tools.idea.lint"
      val className = "$base.${if (issue.isAndroidSpecific()) "inspections." else "common."}$LINT_INSPECTION_PREFIX${issue.id}Inspection"
      try {
        val c = Class.forName(className)
        if (AndroidLintInspectionBase::class.java.isAssignableFrom(c) && c.modifiers and Modifier.ABSTRACT == 0) {
          return c
        }
      }
      catch (ignore: ClassNotFoundException) {
      }
      return null
    }

    private fun getIssueFieldName(issue: Issue): String {
      val detectorClass = getDetectorClass(
        issue)
      // Use reflection on the detector class, check all field instances and compare id's to locate the right one
      for (field in detectorClass.declaredFields) {
        if (field.modifiers and Modifier.STATIC != 0) {
          try {
            val o = field[null]
            if (o is Issue) {
              if (issue.id == o.id) {
                return field.name
              }
            }
          }
          catch (e: IllegalAccessException) { // pass; use default instead
          }
        }
      }
      return "/*findFieldFor: " + issue.id + "*/TODO"
    }

    private fun getDetectorClass(issue: Issue): Class<out Detector?> {
      var detectorClass = issue.implementation.detectorClass
      // Undo the effects of LintIdeIssueRegistry
      if (detectorClass == LintIdeViewTypeDetector::class.java) {
        detectorClass = ViewTypeDetector::class.java
      }
      return detectorClass
    }

    private fun getBriefDescription(issue: Issue): String {
      return issue.getBriefDescription(TextFormat.TEXT)
    }

    private fun getMessageKey(issue: Issue): String {
      return camelCaseToUnderlines(issue.id).replace('_', '.').lowercase(Locale.US)
    }

    private fun camelCaseToUnderlines(string: String): String {
      if (string.isEmpty()) {
        return string
      }
      val sb = StringBuilder(2 * string.length)
      val n = string.length
      var lastWasUpperCase = Character.isUpperCase(string[0])
      for (i in 0 until n) {
        var c = string[i]
        val isUpperCase = Character.isUpperCase(c)
        if (isUpperCase && !lastWasUpperCase) {
          sb.append('_')
        }
        lastWasUpperCase = isUpperCase
        c = Character.toLowerCase(c)
        sb.append(c)
      }
      return sb.toString()
    }
  }
}