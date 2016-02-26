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
package com.android.tools.idea.configurations;

import com.android.resources.ResourceType;
import com.android.tools.idea.model.ManifestInfo;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.model.MergedManifest;
import com.android.tools.idea.model.MergedManifest.ActivityAttributes;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.refactoring.psi.SearchUtils;
import icons.AndroidIcons;
import org.jetbrains.android.inspections.lint.SuppressLintIntentionAction;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Iterator;

import static com.android.SdkConstants.*;

public class ActivityMenuAction extends FlatComboAction {
  private final RenderContext myRenderContext;

  public ActivityMenuAction(RenderContext renderContext) {
    myRenderContext = renderContext;
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(AndroidIcons.Activity);
    presentation.setDescription("Associate with Activity...");
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myRenderContext.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      String activity = configuration.getActivity();
      String label = getActivityLabel(activity, true);
      presentation.setText(label);
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  /**
   * Returns a suitable label to use to display the given activity
   *
   * @param fqcn the activity class to look up a label for
   * @param brief if true, generate a brief label (suitable for a toolbar
   *            button), otherwise a fuller name (suitable for a menu item)
   * @return the label
   */
  @NotNull
  public static String getActivityLabel(@Nullable String fqcn, boolean brief) {
    if (fqcn == null) {
      return "";
    }

    if (brief) {
      String label = fqcn;
      int packageIndex = label.lastIndexOf('.');
      if (packageIndex != -1) {
        label = label.substring(packageIndex + 1);
      }
      int innerClass = label.lastIndexOf('$');
      if (innerClass != -1) {
        label = label.substring(innerClass + 1);
      }

      // Also strip out the "Activity" or "Fragment" common suffix if this is a long name
      if (label.endsWith("Activity") && label.length() > 12) { // 8 for name and a few others (e.g. don't make FooActivity just Foo)
        label = label.substring(0, label.length() - 8);
      } else if (label.endsWith("Fragment") && label.length() > 12) {
        label = label.substring(0, label.length() - 8);
      }

      return label;
    }

    return fqcn;
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    DefaultActionGroup group = new DefaultActionGroup("Activity", true);

    Configuration configuration = myRenderContext.getConfiguration();
    if (configuration != null) {
      Module module = myRenderContext.getModule();
      assert module != null;
      String activity = configuration.getActivity();
      String currentFqcn = null;
      // Note: We need the manifest package, not the current variant's package, since
      // the activity names etc are always relative to the manifest package, not the effective
      // variant package
      String pkg = ManifestInfo.get(module).getPackage();
      if (activity != null && !activity.isEmpty()) {
        int dotIndex = activity.indexOf('.');
        if (dotIndex <= 0) {
          if (pkg != null) {
            activity = pkg + (dotIndex == -1 ? "." : "") + activity;
          }
          currentFqcn = activity;
        } else {
          currentFqcn = activity;
        }

        String title = String.format("Open %1$s", activity.substring(activity.lastIndexOf('.') + 1).replace('$','.'));
        group.add(new ShowActivityAction(myRenderContext, title, activity));
        group.addSeparator();
      }

      // List activities that reference the R.layout.<self> field here
      boolean haveSpecificActivities = false;
      VirtualFile file = configuration.getFile();
      if (file != null) {
        String layoutName = ResourceHelper.getResourceName(file);
        Project project = module.getProject();
        String rLayoutFqcn = StringUtil.notNullize(pkg) + '.' + R_CLASS + '.' + ResourceType.LAYOUT.getName();
        PsiClass layoutClass = findClassSafe(JavaPsiFacade.getInstance(project), rLayoutFqcn, GlobalSearchScope.projectScope(project));
        if (layoutClass != null) {
          PsiClass activityBase = findClassSafe(JavaPsiFacade.getInstance(project), CLASS_ACTIVITY, GlobalSearchScope.allScope(project));
          PsiField field = layoutClass.findFieldByName(layoutName, false);
          if (field != null && activityBase != null) {
            Iterable<PsiReference> allReferences = SearchUtils.findAllReferences(field, GlobalSearchScope.projectScope(project));
            Iterator<PsiReference> iterator = allReferences.iterator();
            if (iterator.hasNext()) {
              PsiReference reference = iterator.next();
              PsiElement element = reference.getElement();
              if (element != null) {
                PsiFile containingFile = element.getContainingFile();
                if (containingFile instanceof PsiJavaFile) {
                  PsiJavaFile javaFile = (PsiJavaFile)containingFile;
                  for (PsiClass cls : javaFile.getClasses()) {
                    if (cls.isInheritor(activityBase, false)) {
                      String fqcn = cls.getQualifiedName();
                      if (fqcn != null && !fqcn.equals(currentFqcn)) {
                        String className = cls.getName();
                        String title = String.format("Associate with %1$s", className);
                        group.add(new ChooseActivityAction(myRenderContext, title, fqcn));
                        haveSpecificActivities = true;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      if (haveSpecificActivities) {
        group.addSeparator();
      }

      group.add(new ChooseActivityAction(myRenderContext, "Associate with other Activity...", null));
    }

    return group;
  }

  /**
   * Try to look up class by name and scope using JavaPsiFacade, return null if IndexNotReadyException is thrown.
   */
  @Nullable
  private static PsiClass findClassSafe(@NotNull JavaPsiFacade facade, @NotNull String qualifiedName, GlobalSearchScope scope) {
    PsiClass result;
    try {
      result = facade.findClass(qualifiedName, scope);
    }
    catch (IndexNotReadyException e) {
      result = null;
    }
    return result;
  }

  private static class ChooseActivityAction extends AnAction {
    private final RenderContext myRenderContext;
    private String myActivity;

    public ChooseActivityAction(@NotNull RenderContext renderContext, @NotNull String title, @Nullable String activity) {
      super(title);
      myRenderContext = renderContext;
      myActivity = activity;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final Module module = myRenderContext.getModule();
      if (module == null) {
        return;
      }
      if (myActivity == null) {
        myActivity = ChooseClassDialog.openDialog(module, "Activities", false /*includeAll*/, CLASS_ACTIVITY);
        if (myActivity == null) {
          return;
        }
      }
      Configuration configuration = myRenderContext.getConfiguration();
      if (configuration != null) {
        configuration.setActivity(myActivity);

        // TODO: Should this be done elsewhere?
        // Would be nice if any call to setActivity() (from anywhere) would have this effect,
        // but is the semantics of editing a file under a write lock from a simple setter clear?
        // What about the bulk editing scheme in the configuration; should this be queued until the batch
        // edits are complete?
        final XmlFile file = myRenderContext.getXmlFile();
        assert file != null;
        WriteCommandAction<Void> action = new WriteCommandAction<Void>(module.getProject(), "Choose Activity", file) {
          @Override
          protected void run(@NotNull Result<Void> result) throws Throwable {
            String activity = myActivity;
            String pkg = ManifestInfo.get(module).getPackage();
            if (pkg != null && activity.startsWith(pkg) && activity.length() > pkg.length()
                && activity.charAt(pkg.length()) == '.') {
              activity = activity.substring(pkg.length());
            }
            SuppressLintIntentionAction.ensureNamespaceImported(myRenderContext.getModule().getProject(), file, TOOLS_URI);
            XmlTag rootTag = file.getRootTag();
            if (rootTag != null) {
              rootTag.setAttribute(ATTR_CONTEXT, TOOLS_URI, activity);
            }
          }
        };
        action.execute();

        // Consider switching themes if the given activity has an implied theme
        MergedManifest manifestInfo = ManifestInfo.get(module);
        ActivityAttributes attributes = manifestInfo.getActivityAttributes(myActivity);
        if (attributes != null) {
          String theme = attributes.getTheme();
          if (theme != null) {
            assert theme.startsWith(PREFIX_RESOURCE_REF) : theme;
            configuration.setTheme(theme);
          }
        }
      }
    }
  }

  private static class ShowActivityAction extends AnAction {
    private final RenderContext myRenderContext;
    private final String myActivity;

    public ShowActivityAction(@NotNull RenderContext renderContext, @NotNull String title, @NotNull String activity) {
      super(title);
      myRenderContext = renderContext;
      myActivity = activity;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      Module module = myRenderContext.getModule();
      if (module == null) {
        return;
      }
      Project project = module.getProject();
      PsiClass clz = JavaPsiFacade.getInstance(project).findClass(myActivity.replace('$','.'), GlobalSearchScope.allScope(project));
      if (clz != null) {
        PsiFile containingFile = clz.getContainingFile();
        if (containingFile != null) {
          VirtualFile file = containingFile.getVirtualFile();
          if (file != null) {
            OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file, clz.getTextOffset());
            FileEditorManager.getInstance(project).openEditor(descriptor, true);
          }
        }
      }
    }
  }
}
