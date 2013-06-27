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
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionProfile;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.android.AndroidTestCase;

import java.util.*;

/** Ensures that all relevant lint checks are available and registered */
public class AndroidLintInspectionToolProviderTest extends AndroidTestCase {
  public void testAllLintChecksRegistered() throws Exception {
    // For some reason, when running as unit tests, IntellijLintRegistry does
    // not return any of the registered lint checks, so the call below
    // can't find the issues that are missing. Therefore, I instead
    // modified AndroidLintExternalAnnotator to call
    //  testAllLintChecksRegistered(file.getProject());
    // at runtime instead and captured the output.

    //assertTrue(testAllLintChecksRegistered(myFixture.getProject()));
  }

  private static boolean sDone;
  public static boolean testAllLintChecksRegistered(Project project) throws Exception {
    if (sDone) {
      return true;
    }
    sDone = true;
    final List<Issue> missing = new ArrayList<Issue>();
    final IntellijLintIssueRegistry fullRegistry = new IntellijLintIssueRegistry();

    List<Issue> allIssues = fullRegistry.getIssues();
    for (Issue issue : allIssues) {
      if (!isRelevant(issue)) {
        continue;
      }

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

      final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
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
      Collections.sort(missing);

      StringBuilder sb = new StringBuilder(1000);
      sb.append("Missing registration for " + missing.size() + " issues (out of a total issue count of " + allIssues.size() + ")");
      sb.append("\nAdd to plugin.xml:\n");
      for (Issue issue : missing) {
        sb.append("    <globalInspection hasStaticDescription=\"true\" shortName=\"AndroidLint");
        String id = issue.getId();
        sb.append(id);
        sb.append("\" displayName=\"");
        sb.append(XmlUtils.toXmlAttributeValue(issue.getBriefDescription(Issue.OutputFormat.TEXT)));
        sb.append("\" groupKey=\"android.lint.inspections.group.name\" bundle=\"messages.AndroidBundle\" enabledByDefault=\"");
        sb.append(issue.isEnabledByDefault());
        sb.append("\"  level=\"");
        sb.append(issue.getDefaultSeverity() == Severity.ERROR || issue.getDefaultSeverity() == Severity.FATAL ?
                  "ERROR" : "WARNING");
        sb.append("\" implementationClass=\"org.jetbrains.android.inspections.lint.AndroidLintInspectionToolProvider$AndroidLint");
        sb.append(id);
        sb.append("Inspection\"/>\n");
      }

      sb.append("\nAdd to AndroidLintInspectionToolProvider.java:\n");
      for (Issue issue : missing) {
        String id = issue.getId();
        String detectorName = issue.getImplementation().getDetectorClass().getName();
        String issueName = getIssueFieldName(issue);
        String messageKey = getMessageKey(issue);
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
        sb.append("android.lint.inspections." + messageKey + "=" + getBriefDescription(issue).replace(":", "\\:").replace("=", "\\=") + "\n");
      }

      sb.append("\nAdded registrations for " + missing.size() + " issues (out of a total issue count of " + allIssues.size() + ")");

      System.out.println(sb.toString());
      return false;
    }

    return true;
  }

  private static String getIssueFieldName(Issue issue) {
    Class<? extends Detector> detectorClass = issue.getImplementation().getDetectorClass();
    //PsiManager.getInstance(myFixture.getProject())

    //return "ISSUE"; // TODO: Figure out a better way to do it
    return "/*findFieldFor: " + issue.getId() + "*/TODO";
  }

  private static String getBriefDescription(Issue issue) {
    return issue.getBriefDescription(Issue.OutputFormat.TEXT);
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
    if (issue == NamespaceDetector.TYPO ||             // IDEA has its own spelling check
        issue == NamespaceDetector.UNUSED ||           // IDEA already does full validation
        issue == ManifestTypoDetector.ISSUE ||         // IDEA already does full validation
        issue == ManifestDetector.WRONG_PARENT ||      // IDEA already does full validation
        issue == DeprecationDetector.ISSUE ||
        issue == LocaleDetector.STRING_LOCALE) {       // IDEA checks for this in Java code
      return false;
    }

    return true;
  }
}
