package org.jetbrains.android.refactoring;

import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMigration;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.containers.HashMap;
import icons.AndroidIcons;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.dom.resources.StyleItem;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.android.util.ErrorReporter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidRefactoringUtil {
  private AndroidRefactoringUtil() {
  }

  @Nullable
  static StyleRefData getParentStyle(@NotNull Style style) {
    final ResourceValue parentStyleRefValue = style.getParentStyle().getValue();

    if (parentStyleRefValue != null) {
      final String parentStyleName = parentStyleRefValue.getResourceName();

      if (parentStyleName != null) {
        return new StyleRefData(parentStyleName, parentStyleRefValue.getPackage());
      }
    }
    else {
      final String styleName = style.getName().getStringValue();

      if (styleName != null) {
        final int idx = styleName.lastIndexOf('.');

        if (idx > 0) {
          return new StyleRefData(styleName.substring(0, idx), null);
        }
      }
    }
    return null;
  }

  @Nullable
  static Map<AndroidAttributeInfo, String> computeAttributeMap(@NotNull Style style,
                                                               @NotNull ErrorReporter errorReporter,
                                                               @NotNull String errorReportTitle) {
    final Map<AndroidAttributeInfo, String> attributeValues = new HashMap<AndroidAttributeInfo, String>();

    for (StyleItem item : style.getItems()) {
      final String attributeName = item.getName().getStringValue();
      String attributeValue = item.getStringValue();

      if (attributeName == null || attributeName.length() <= 0 || attributeValue == null) {
        continue;
      }
      final int idx = attributeName.indexOf(':');
      final String localName = idx >= 0 ? attributeName.substring(idx + 1) : attributeName;
      final String nsPrefix = idx >= 0 ? attributeName.substring(0, idx) : null;

      if (nsPrefix != null) {
        if (!AndroidUtils.SYSTEM_RESOURCE_PACKAGE.equals(nsPrefix)) {
          errorReporter.report(RefactoringBundle.getCannotRefactorMessage("Unknown XML attribute prefix '" + nsPrefix + ":'"),
                               errorReportTitle);
          return null;
        }
      }
      else {
        errorReporter.report(
          RefactoringBundle.getCannotRefactorMessage("The style contains attribute without 'android' prefix."),
          errorReportTitle);
        return null;
      }
      attributeValues.put(new AndroidAttributeInfo(localName, nsPrefix), attributeValue);
    }
    return attributeValues;
  }

  // Code copied from MigrationUtil since it's not marked public
  @NotNull
  static PsiClass findOrCreateClass(@NotNull Project project, @NotNull PsiMigration migration, @NotNull String qName) {
    PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(qName, GlobalSearchScope.allScope(project));
    if (aClass == null) {
      aClass = WriteAction.compute(() -> migration.createClass(qName));
    }
    return aClass;
  }

  @NotNull
  static PsiPackage findOrCreatePackage(@NotNull Project project, @NotNull PsiMigration migration, @NotNull String qName) {
    PsiPackage aPackage = JavaPsiFacade.getInstance(project).findPackage(qName);
    if (aPackage != null) {
      return aPackage;
    }
    else {
      return WriteAction.compute(() -> migration.createPackage(qName));
    }
  }

  static void offerToSync(@NotNull Project project, @NotNull String title) {
    int dialogResult = Messages.showYesNoCancelDialog(project,
                                                      "A project sync is necessary after the refactoring. Sync the project now?",
                                                      title,
                                                      AndroidIcons.GradleSync);
    if (dialogResult == Messages.YES) {
      GradleSyncInvoker.Request syncRequest = GradleSyncInvoker.Request.userRequest();
      syncRequest.generateSourcesOnSuccess = true;
      syncRequest.runInBackground = false;
      GradleSyncInvoker.getInstance().requestProjectSync(project, syncRequest);
    }
  }
}
