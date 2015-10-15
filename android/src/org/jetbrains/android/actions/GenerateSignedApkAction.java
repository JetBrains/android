package org.jetbrains.android.actions;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.android.exportSignedPackage.CheckModulePanel;
import org.jetbrains.android.exportSignedPackage.ExportSignedPackageWizard;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

import static com.android.tools.idea.gradle.util.Projects.isBuildWithGradle;
import static com.android.tools.idea.gradle.util.Projects.requiresAndroidModel;
import static org.jetbrains.android.util.AndroidUtils.getApplicationFacets;

/**
 * @author Eugene.Kudelevsky
 */
public class GenerateSignedApkAction extends AnAction {
  public GenerateSignedApkAction() {
    super(AndroidBundle.message("android.generate.signed.apk.action.text"));
  }

  private static boolean checkFacet(@NotNull AndroidFacet facet) {
    final CheckModulePanel panel = new CheckModulePanel();
    panel.updateMessages(facet);
    final boolean hasError = panel.hasError();
    if (hasError || panel.hasWarnings()) {
      DialogWrapper dialog = new DialogWrapper(facet.getModule().getProject()) {
        {
          if (!hasError) {
            setOKButtonText("Continue");
          }
          init();
        }

        @NotNull
        @Override
        protected Action[] createActions() {
          if (hasError) {
            return new Action[]{getOKAction()};
          }
          return super.createActions();
        }

        @Override
        protected JComponent createCenterPanel() {
          return panel;
        }
      };
      dialog.setTitle(hasError ? CommonBundle.getErrorTitle() : CommonBundle.getWarningTitle());
      dialog.show();
      return !hasError && dialog.isOK();
    }
    return true;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    List<AndroidFacet> facets = getApplicationFacets(project);
    assert !facets.isEmpty();
    if (facets.size() == 1 && !checkFacet(facets.get(0))) {
      return;
    }

    ExportSignedPackageWizard wizard = new ExportSignedPackageWizard(project, facets, true);
    wizard.show();
  }

  @Override
  public void update(AnActionEvent e) {
    Project project = e.getProject();
    boolean enabled = project != null && !getApplicationFacets(project).isEmpty() &&
                      (isBuildWithGradle(project) || !requiresAndroidModel(project) /* Available for Gradle projects and legacy IDEA Android projects */);
    e.getPresentation().setEnabledAndVisible(enabled);
  }
}
