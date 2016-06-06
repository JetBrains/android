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

import com.android.tools.lint.checks.*;
import com.android.tools.lint.detector.api.*;
import com.android.utils.XmlUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.AndroidTestCase;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import static com.android.utils.SdkUtils.escapePropertyValue;

/** Ensures that all relevant lint checks are available and registered */
public class AndroidLintInspectionToolProviderTest extends AndroidTestCase {
  private static final boolean LIST_ISSUES_WITH_QUICK_FIXES = false;
  public void testAllLintChecksRegistered() throws Exception {
    assertTrue(checkAllLintChecksRegistered(getProject()));
  }

  private static boolean sDone;
  @SuppressWarnings("deprecation")
  public static boolean checkAllLintChecksRegistered(Project project) throws Exception {
    if (sDone) {
      return true;
    }
    sDone = true;

    // For some reason, I can't just use
    //   AndroidLintInspectionBase.getInspectionShortNameByIssue
    // to iterate the available inspections from unit tests; at runtime this will enumerate all
    // the available inspections, but from unit tests (even when extending IdeaTestCase) it's empty.
    // So instead we take advantage of the knowledge that all our inspections are declared as inner classes
    // of AndroidLintInspectionToolProvider and we iterate these instead. This won't catch cases if
    // we declare a class there and forget to register in the plugin, but it's better than nothing.
    Set<String> registered = Sets.newHashSetWithExpectedSize(200);
    Set<Issue> quickfixes = Sets.newLinkedHashSetWithExpectedSize(200);
    for (Class<?> c : AndroidLintInspectionToolProvider.class.getDeclaredClasses()) {
      if (AndroidLintInspectionBase.class.isAssignableFrom(c) && ((c.getModifiers() & Modifier.ABSTRACT) == 0)) {
        AndroidLintInspectionBase provider = (AndroidLintInspectionBase)c.newInstance();
        registered.add(provider.getIssue().getId());

        boolean hasQuickFix = true;
        try {
          provider.getClass().getDeclaredMethod("getQuickFixes", String.class);
        } catch (NoSuchMethodException e1) {
          try {
            provider.getClass().getDeclaredMethod("getQuickFixes", PsiElement.class, PsiElement.class, String.class);
          } catch (NoSuchMethodException e2) {
            hasQuickFix = false;
          }
        }
        if (hasQuickFix) {
          quickfixes.add(provider.getIssue());
        }
      }
    }

    final List<Issue> missing = new ArrayList<Issue>();
    final IntellijLintIssueRegistry fullRegistry = new IntellijLintIssueRegistry();

    List<Issue> allIssues = fullRegistry.getIssues();
    for (Issue issue : allIssues) {
      if (!isRelevant(issue) || registered.contains(issue.getId())) {
        continue;
      }

      assertFalse(IntellijLintProject.SUPPORT_CLASS_FILES); // When enabled, adjust this to register class based registrations
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

      final String inspectionShortName = AndroidLintInspectionBase.getInspectionShortNameByIssue(project, issue);
      if (inspectionShortName == null) {
        missing.add(issue);
        continue;
      }

      // ELSE: compare severity, enabledByDefault, etc, message, etc

      final HighlightDisplayKey key = HighlightDisplayKey.find(inspectionShortName);
      if (key == null) {
        //fail("No highlight display key for inspection " + inspectionShortName + " for issue " + issue);
        System.out.println("No highlight display key for inspection " + inspectionShortName + " for issue " + issue);
        continue;
      }

      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getCurrentProfile();
      PsiElement element = null;
      HighlightDisplayLevel errorLevel = profile.getErrorLevel(key, element);
      Severity s;
      if (errorLevel == HighlightDisplayLevel.WARNING || errorLevel == HighlightDisplayLevel.WEAK_WARNING) {
        s = Severity.WARNING;
      } else if (errorLevel == HighlightDisplayLevel.ERROR || errorLevel == HighlightDisplayLevel.NON_SWITCHABLE_ERROR) {
        s = Severity.ERROR;
      } else if (errorLevel == HighlightDisplayLevel.DO_NOT_SHOW) {
        s = Severity.IGNORE;
      } else if (errorLevel == HighlightDisplayLevel.INFO) {
        s = Severity.INFORMATIONAL;
      } else {
        //fail("Unexpected error level " + errorLevel);
        System.out.println("Unexpected error level " + errorLevel);
        continue;
      }
      Severity expectedSeverity = issue.getDefaultSeverity();
      if (expectedSeverity == Severity.FATAL) {
        expectedSeverity = Severity.ERROR;
      }
      //assertSame(expectedSeverity, s);

      if (expectedSeverity != s) {
        System.out.println("Wrong severity for " + issue + "; expected " + expectedSeverity + ", got " + s);
      }
    }

    // Spit out registration information for the missing elements
    if (!missing.isEmpty()) {
      Collections.sort(missing, new Comparator<Issue>() {
        @Override
        public int compare(Issue issue1, Issue issue2) {
          return String.CASE_INSENSITIVE_ORDER.compare(issue1.getId(), issue2.getId());
        }
      });

      StringBuilder sb = new StringBuilder(1000);
      sb.append("Missing registration for ").append(missing.size()).append(" issues (out of a total issue count of ")
        .append(allIssues.size()).append(")");
      sb.append("\nAdd to plugin.xml (and please try to preserve the case insensitive alphabetical order):\n");
      for (Issue issue : missing) {
        sb.append("    <globalInspection hasStaticDescription=\"true\" shortName=\"AndroidLint");
        String id = issue.getId();
        sb.append(id);
        sb.append("\" displayName=\"");
        sb.append(XmlUtils.toXmlAttributeValue(issue.getBriefDescription(TextFormat.TEXT)));
        sb.append("\" groupKey=\"").append(getCategoryBundleKey(issue.getCategory()))
          .append("\" bundle=\"messages.AndroidBundle\" enabledByDefault=\"");
        sb.append(issue.isEnabledByDefault());
        sb.append("\" level=\"");
        sb.append(issue.getDefaultSeverity() == Severity.ERROR || issue.getDefaultSeverity() == Severity.FATAL ?
                  "ERROR" : "WARNING");
        sb.append("\" implementationClass=\"org.jetbrains.android.inspections.lint.AndroidLintInspectionToolProvider$AndroidLint");
        sb.append(id);
        sb.append("Inspection\"/>\n");
      }

      sb.append("\nAdd to AndroidLintInspectionToolProvider.java:\n");
      for (Issue issue : missing) {
        String id = issue.getId();
        String detectorName = getDetectorClass(issue).getName();
        String issueName = getIssueFieldName(issue);
        String messageKey = getMessageKey(issue);
        //noinspection StringConcatenationInsideStringBufferAppend
        sb.append("" +
                  "  public static class AndroidLint" + id + "Inspection extends AndroidLintInspectionBase {\n" +
                  "    public AndroidLint" + id + "Inspection() {\n" +
                  "      super(AndroidBundle.message(\"android.lint.inspections." + messageKey + "\"), " + detectorName + "." + issueName + ");\n" +
                  "    }\n" +
                  "  }\n");
      }

      sb.append("\nAdd to AndroidBundle.properties:\n");
      for (Issue issue : missing) {
        String messageKey = getMessageKey(issue);
        sb.append("android.lint.inspections.").append(messageKey).append("=")
          .append(escapePropertyValue(getBriefDescription(issue))).append("\n");
      }

      sb.append("\nAdded registrations for ").append(missing.size()).append(" issues (out of a total issue count of ")
        .append(allIssues.size()).append(")\n");

      System.out.println("If necessary, add these category descriptors to AndroidBundle.properties:\n");
      Set<Category> categories = Sets.newHashSet();
      for (Issue issue : missing) {
        categories.add(issue.getCategory());
      }
      List<Category> sorted = Lists.newArrayList(categories);
      Collections.sort(sorted);
      for (Category category : sorted) {
        sb.append(getCategoryBundleKey(category)).append('=').append(
          escapePropertyValue(("Android > Lint > " + category.getFullName()).replace(":", " > "))).append("\n");
      }

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

    // Undo the effects of IntellijLintIssueRegistry
    if (detectorClass == IntellijApiDetector.class) {
      detectorClass = ApiDetector.class;
    } else if (detectorClass == IntellijGradleDetector.class) {
      detectorClass = GradleDetector.class;
    } else if (detectorClass == IntellijViewTypeDetector.class) {
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

  private static boolean isRelevant(Issue issue) {
    // Supported more directly by other IntelliJ checks(?)
    if (issue == NamespaceDetector.TYPO ||                  // IDEA has its own spelling check
        issue == NamespaceDetector.UNUSED ||                // IDEA already does full validation
        issue == ManifestTypoDetector.ISSUE ||              // IDEA already does full validation
        issue == ManifestDetector.WRONG_PARENT ||           // IDEA checks for this in Java code
        // Reimplemented by ResourceTypeInspection
        issue.getImplementation().getDetectorClass() == SupportAnnotationDetector.class) {
      return false;
    }

    return true;
  }
}
