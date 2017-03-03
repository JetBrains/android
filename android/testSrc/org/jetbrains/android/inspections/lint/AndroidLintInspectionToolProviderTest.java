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
package org.jetbrains.android.inspections.lint;

import com.android.tools.idea.lint.LintIdeGradleDetector;
import com.android.tools.idea.lint.LintIdeIssueRegistry;
import com.android.tools.idea.lint.LintIdeProject;
import com.android.tools.idea.lint.LintIdeViewTypeDetector;
import com.android.tools.lint.checks.GradleDetector;
import com.android.tools.lint.checks.SupportAnnotationDetector;
import com.android.tools.lint.checks.ViewTypeDetector;
import com.android.tools.lint.detector.api.*;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.AndroidTestCase;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import static com.android.utils.SdkUtils.escapePropertyValue;
import static org.jetbrains.android.inspections.lint.AndroidLintInspectionBase.LINT_INSPECTION_PREFIX;

/** Ensures that all relevant lint checks are available and registered */
public class AndroidLintInspectionToolProviderTest extends AndroidTestCase {
  private static final boolean LIST_ISSUES_WITH_QUICK_FIXES = false;
  public static final String ADT_SOURCE_TREE = "ADT_SOURCE_TREE";

  public void testAllLintChecksRegistered() throws Exception {
    assertTrue(
      "Not all lint checks have been registered. See the standard output for instructions on how to register the missing checks.",
      checkAllLintChecksRegistered(getProject()));
  }

  private static boolean ourDone;
  @SuppressWarnings("deprecation")
  public static boolean checkAllLintChecksRegistered(Project project) throws Exception {
    if (ourDone) {
      return true;
    }
    ourDone = true;

    // For some reason, I can't just use
    //   AndroidLintInspectionBase.getInspectionShortNameByIssue
    // to iterate the available inspections from unit tests; at runtime this will enumerate all
    // the available inspections, but from unit tests (even when extending IdeaTestCase) it's empty.
    // So instead we take advantage of the knowledge that all our inspections are named in a particular
    // way from the lint issue id's, so we can use reflection to find the classes.
    // This won't catch cases if we declare a class there and forget to register in the plugin, but
    // it's better than nothing.
    Set<String> registered = Sets.newHashSetWithExpectedSize(200);
    Set<Issue> quickfixes = Sets.newLinkedHashSetWithExpectedSize(200);

    final LintIdeIssueRegistry fullRegistry = new LintIdeIssueRegistry();
    List<Issue> allIssues = fullRegistry.getIssues();

    for (Issue issue : allIssues) {
      if (!LintIdeIssueRegistry.isRelevant(issue)) {
        continue;
      }
      String className = "com.android.tools.idea.lint.AndroidLint" + issue.getId() + "Inspection";
      try {
        Class<?> c = Class.forName(className);
        if (AndroidLintInspectionBase.class.isAssignableFrom(c) && ((c.getModifiers() & Modifier.ABSTRACT) == 0)) {
          AndroidLintInspectionBase provider = (AndroidLintInspectionBase)c.newInstance();
          registered.add(provider.getIssue().getId());

          boolean hasQuickFix = true;
          try {
            provider.getClass().getDeclaredMethod("getQuickFixes", String.class);
          }
          catch (NoSuchMethodException e1) {
            try {
              provider.getClass().getDeclaredMethod("getQuickFixes", PsiElement.class, PsiElement.class, String.class);
            }
            catch (NoSuchMethodException e2) {
              hasQuickFix = false;
            }
          }
          if (hasQuickFix) {
            quickfixes.add(provider.getIssue());
          }
        }
      } catch (ClassNotFoundException ignore) {
      }
    }

    final List<Issue> missing = new ArrayList<>();
    for (Issue issue : allIssues) {
      if (!LintIdeIssueRegistry.isRelevant(issue) || registered.contains(issue.getId())) {
        continue;
      }

      assertFalse(LintIdeProject.SUPPORT_CLASS_FILES); // When enabled, adjust this to register class based registrations
      Implementation implementation = issue.getImplementation();
      if (implementation.getScope().contains(Scope.CLASS_FILE) ||
          implementation.getScope().contains(Scope.ALL_CLASS_FILES) ||
          implementation.getScope().contains(Scope.JAVA_LIBRARIES)) {
        boolean isOk = false;
        for (EnumSet<Scope> analysisScope : implementation.getAnalysisScopes()) {
          if (!analysisScope.contains(Scope.CLASS_FILE)
              && !analysisScope.contains(Scope.ALL_CLASS_FILES)
              && !analysisScope.contains(Scope.JAVA_LIBRARIES)) {
            isOk = true;
            break;
          }
        }
        if (!isOk) {
          System.out.println("Skipping issue " + issue + " because it requires classfile analysis. Consider rewriting in IDEA.");
          continue;
        }
      }

      missing.add(issue);
    }

    String sourceTree = System.getenv(ADT_SOURCE_TREE);
    if (sourceTree == null) {
      sourceTree = System.getProperty(ADT_SOURCE_TREE);
    }
    File root = sourceTree != null ? new File(sourceTree) : null;
    if (root != null && !new File(root, ".repo").isDirectory()) {
      fail("Invalid directory: should be pointing to the root of a tools checkout directory");
    }

    // Spit out registration information for the missing elements
    if (!missing.isEmpty()) {
      missing.sort((issue1, issue2) -> String.CASE_INSENSITIVE_ORDER.compare(issue1.getId(), issue2.getId()));

      StringBuilder sb = new StringBuilder(1000);
      sb.append("Missing registration for ").append(missing.size()).append(" issues (out of a total issue count of ")
        .append(allIssues.size()).append(")");
      if (root == null) {
        sb.append("\n***If you set the environment variable " + ADT_SOURCE_TREE + " (or set it as a system property in the test run " +
                  "config) this test can automatically create/edit the files for you!***\n");
        sb.append("\nAdd to android/src/META-INF/android-plugin.xml (and please try to preserve the case insensitive alphabetical " +
                  "order):\n");
      }
      for (Issue issue : missing) {
        StringBuilder desc = new StringBuilder();

        desc.append("<globalInspection hasStaticDescription=\"true\" shortName=\"");
        desc.append(LINT_INSPECTION_PREFIX);
        String id = issue.getId();
        desc.append(id);
        desc.append("\" displayName=\"");
        desc.append(XmlUtils.toXmlAttributeValue(issue.getBriefDescription(TextFormat.TEXT)));
        desc.append("\" bundle=\"messages.AndroidBundle\" enabledByDefault=\"");
        desc.append(issue.isEnabledByDefault());
        desc.append("\" level=\"");
        desc.append(issue.getDefaultSeverity() == Severity.ERROR || issue.getDefaultSeverity() == Severity.FATAL ?
                    "ERROR" : issue.getDefaultSeverity() == Severity.WARNING ? "WARNING" : "INFO");
        desc.append("\" implementationClass=\"com.android.tools.idea.lint.AndroidLint");
        desc.append(id);
        desc.append("Inspection\"/>\n");

        boolean performed = false;
        if (root != null) {
          String insert = desc.toString();
          // Try to make the edit directly
          File plugin = new File(root, "tools/adt/idea/android/src/META-INF/android-plugin.xml");
          if (plugin.exists()) {
            String original = Files.toString(plugin, Charsets.UTF_8);
            int begin = 0;
            while (true) {
              int end = original.indexOf('\n', begin);
              if (end == -1) {
                break;
              }
              String line = original.substring(begin, end);
              String trimmed = line.trim();
              if ((trimmed.startsWith("<globalInspection hasStaticDescription=\"true\" shortName=\"AndroidLint") &&
                   trimmed.compareToIgnoreCase(insert) > 0) ||
                  // Passed all lint registration entries: insert it here (must be newly last alphabetical issue)
                  trimmed.startsWith("<globalInspection hasStaticDescription=\"true\" shortName=\"PermissionUsageInspection\"")) {
                String contents = original.substring(0, begin) + "    " + insert + original.substring(begin);
                Files.write(contents, plugin, Charsets.UTF_8);
                sb.append(" <automatically updated plugin.xml in ").append(root).append(">\n");
                performed = true;
                break;
              }

              begin = end + 1;
            }
          }
        }

        if (!performed) {
          sb.append("    ").append(desc);
        }
      }

      sb.append("\nAdd to com.android.tools.idea.lint:\n");
      for (Issue issue : missing) {
        String id = issue.getId();
        String detectorClass = getDetectorClass(issue).getName();
        String detectorName = getDetectorClass(issue).getSimpleName();
        String issueName = getIssueFieldName(issue);
        String messageKey = getMessageKey(issue);
        int year = Calendar.getInstance().get(Calendar.YEAR);
        //noinspection StringConcatenationInsideStringBufferAppend,ConcatenationWithEmptyString
        String code = "" +
                  "/*\n" +
                  " * Copyright (C) " + year + " The Android Open Source Project\n" +
                  " *\n" +
                  " * Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
                  " * you may not use this file except in compliance with the License.\n" +
                  " * You may obtain a copy of the License at\n" +
                  " *\n" +
                  " *      http://www.apache.org/licenses/LICENSE-2.0\n" +
                  " *\n" +
                  " * Unless required by applicable law or agreed to in writing, software\n" +
                  " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                  " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                  " * See the License for the specific language governing permissions and\n" +
                  " * limitations under the License.\n" +
                  " */\n" +
                  "package com.android.tools.idea.lint;\n" +
                  "\n" +
                  "import " + detectorClass + ";\n" +
                  "import org.jetbrains.android.inspections.lint.AndroidLintInspectionBase;\n" +
                  "import org.jetbrains.android.util.AndroidBundle;\n" +
                  "\n" +
                  "public class AndroidLint" + id + "Inspection extends AndroidLintInspectionBase {\n" +
                  "  public AndroidLint" + id + "Inspection() {\n" +
                  "    super(AndroidBundle.message(\"android.lint.inspections." + messageKey + "\"), " + detectorName + "." + issueName + ");\n" +
                  "  }\n" +
                  "}\n";

        if (root != null) {
          File packageDir = new File(root, "tools/adt/idea/android/src/com/android/tools/idea/lint");
          assertTrue(packageDir.toString(), packageDir.exists());
          File to = new File(packageDir, "AndroidLint" + id + "Inspection.java");
          if (!to.isFile()) {
            Files.write(code, to, Charsets.UTF_8);
            sb.append(" <automatically created ").append(to).append(">\n");
          } else {
            sb.append(" <already exists: ").append(to).append(">\n");
          }
        } else {
          sb.append(code);
        }
      }

      sb.append("\nAdd to AndroidBundle.properties:\n");
      for (Issue issue : missing) {
        String messageKey = getMessageKey(issue);
        StringBuilder desc = new StringBuilder();
        desc.append("android.lint.inspections.").append(messageKey).append("=")
          .append(escapePropertyValue(getBriefDescription(issue))).append("\n");

        boolean performed = false;
        if (root != null) {
          String insert = desc.toString();
          // Try to make the edit directly
          File propertyFile = new File(root, "tools/adt/idea/android/resources/messages/AndroidBundle.properties");
          if (propertyFile.exists()) {
            String original = Files.toString(propertyFile, Charsets.UTF_8);
            int begin = 0;
            while (true) {
              int end = original.indexOf('\n', begin);
              if (end == -1) {
                break;
              }
              String line = original.substring(begin, end);
              if ((line.startsWith("android.lint.inspections.") &&
                   !line.startsWith("android.lint.inspections.group.name") &&
                   !line.startsWith("android.lint.inspections.subgroup.name") &&
                   line.compareToIgnoreCase(insert) > 0) ||
                  // Passed all lint keys: insert it here (must be newly last alphabetical issue)
                  line.startsWith("android.lint.fix.")) {
                performed = true;
                String contents = original.substring(0, begin) + insert + original.substring(begin);
                Files.write(contents, propertyFile, Charsets.UTF_8);
                desc.append(" <automatically updated AndroidBundle.properties.xml in ").append(root).append(">\n");
                break;
              }

              begin = end + 1;
            }
          }
        }

        if (!performed) {
          sb.append(desc);
        }
      }

      sb.append("\nAdded registrations for ").append(missing.size()).append(" issues (out of a total issue count of ")
        .append(allIssues.size()).append(")\n");

      System.out.println(sb.toString());
      return false;
    } else if (LIST_ISSUES_WITH_QUICK_FIXES) {
      System.out.println("The following inspections have quickfixes (used for Reporter.java):\n");
      List<String> fields = Lists.newArrayListWithExpectedSize(quickfixes.size());
      Set<String> imports = Sets.newHashSetWithExpectedSize(quickfixes.size());

      // These two are handled by the ResourceTypeInspection's quickfixes; they're
      // not handled by lint per se, but on the command line (in HTML reports) they're
      // flagged by lint, so include them in the list
      quickfixes.add(SupportAnnotationDetector.CHECK_PERMISSION);
      quickfixes.add(SupportAnnotationDetector.MISSING_PERMISSION);
      quickfixes.add(SupportAnnotationDetector.CHECK_RESULT);

      for (Issue issue : quickfixes) {
        String detectorName = getDetectorClass(issue).getName();
        imports.add(detectorName);
        int index = detectorName.lastIndexOf('.');
        if (index != -1) {
          detectorName = detectorName.substring(index + 1);
        }
        String issueName = getIssueFieldName(issue);
        fields.add(detectorName + "." + issueName);
      }
      Collections.sort(fields);
      List<String> sortedImports = Lists.newArrayList(imports);
      Collections.sort(sortedImports);
      for (String cls : sortedImports) {
        System.out.println("import " + cls + ";");
      }
      System.out.println();
      System.out.println("sStudioFixes = Sets.newHashSet(\n    " + Joiner.on(",\n    ").join(fields) + "\n);\n");
    }

    return true;
  }

  private static String getCategoryBundleKey(Category category) {
    StringBuilder sb = new StringBuilder(100);
    sb.append("android.lint.inspections.group.name.");
    String name = category.getFullName().toLowerCase(Locale.US);
    for (int i = 0, n = name.length(); i < n; i++) {
      char c = name.charAt(i);
      if (Character.isLetter(c)) {
        sb.append(c);
      } else {
        sb.append('.');
      }
    }
    return sb.toString();
  }

  private static String getIssueFieldName(Issue issue) {
    Class<? extends Detector> detectorClass = getDetectorClass(issue);

    // Use reflection on the detector class, check all field instances and compare id's to locate the right one
    for (Field field : detectorClass.getDeclaredFields()) {
      if ((field.getModifiers() & Modifier.STATIC) != 0) {
        try {
          Object o = field.get(null);
          if (o instanceof Issue) {
            if (issue.getId().equals(((Issue)o).getId())) {
              return field.getName();
            }
          }
        }
        catch (IllegalAccessException e) {
          // pass; use default instead
        }
      }
    }

    return "/*findFieldFor: " + issue.getId() + "*/TODO";
  }

  private static Class<? extends Detector> getDetectorClass(Issue issue) {
    Class<? extends Detector> detectorClass = issue.getImplementation().getDetectorClass();

    // Undo the effects of LintIdeIssueRegistry
    if (detectorClass == LintIdeGradleDetector.class) {
      detectorClass = GradleDetector.class;
    } else if (detectorClass == LintIdeViewTypeDetector.class) {
      detectorClass = ViewTypeDetector.class;
    }
    return detectorClass;
  }

  private static String getBriefDescription(Issue issue) {
    return issue.getBriefDescription(TextFormat.TEXT);
  }

  private static String getMessageKey(Issue issue) {
    return camelCaseToUnderlines(issue.getId()).replace('_','.').toLowerCase(Locale.US);
  }

  private static String camelCaseToUnderlines(String string) {
    if (string.isEmpty()) {
      return string;
    }

    StringBuilder sb = new StringBuilder(2 * string.length());
    int n = string.length();
    boolean lastWasUpperCase = Character.isUpperCase(string.charAt(0));
    for (int i = 0; i < n; i++) {
      char c = string.charAt(i);
      boolean isUpperCase = Character.isUpperCase(c);
      if (isUpperCase && !lastWasUpperCase) {
        sb.append('_');
      }
      lastWasUpperCase = isUpperCase;
      c = Character.toLowerCase(c);
      sb.append(c);
    }

    return sb.toString();
  }
}
