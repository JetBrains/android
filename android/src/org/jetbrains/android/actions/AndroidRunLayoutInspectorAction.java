package org.jetbrains.android.actions;

import com.android.ddmlib.Client;
import com.android.tools.idea.ddms.actions.LayoutInspectorAction;
import com.android.tools.idea.fd.actions.RestartActivityAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import icons.AndroidIcons;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidBundle;

public class AndroidRunLayoutInspectorAction extends AnAction {
  public AndroidRunLayoutInspectorAction() {
    super(AndroidBundle.message("android.ddms.actions.layoutinspector.title"),
          AndroidBundle.message("android.ddms.actions.layoutinspector.description"),
          AndroidIcons.Ddms.LayoutInspector);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    if (RestartActivityAction.isDebuggerPaused(e.getProject())) {
      e.getPresentation().setDescription(AndroidBundle.message("android.ddms.actions.layoutinspector.description.disabled"));
      e.getPresentation().setEnabled(false);
    }
    else {
      e.getPresentation().setDescription(AndroidBundle.message("android.ddms.actions.layoutinspector.description"));
      e.getPresentation().setEnabled(true);
    }
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    Project project = e.getProject();
    assert project != null;

    if (!AndroidSdkUtils.activateDdmsIfNecessary(project)) {
      return;
    }

    AndroidProcessChooserDialog dialog = new AndroidProcessChooserDialog(project, false);
    dialog.show();
    if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
      Client client = dialog.getClient();
      if (client != null) {
        new LayoutInspectorAction.GetClientWindowsTask(project, client).queue();
      }
      else {
        Logger.getInstance(AndroidRunLayoutInspectorAction.class).warn("Not launching layout inspector - no client selected");
      }
    }
  }
}
