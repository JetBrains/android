// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.android.tools.idea.rendering;

import static com.android.SdkConstants.DOT_JAR;
import static com.intellij.util.io.URLUtil.FILE_PROTOCOL;
import static com.intellij.util.io.URLUtil.JAR_PROTOCOL;

import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.util.PathString;
import com.android.resources.ResourceType;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.ui.resourcechooser.common.ResourcePickerSources;
import com.android.tools.idea.ui.resourcechooser.util.ResourceChooserHelperKt;
import com.android.tools.idea.util.FileExtensions;
import com.android.utils.HashCodes;
import com.android.utils.SdkUtils;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ComponentPopupBuilder;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.io.URLUtil;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.MouseInfo;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.SwingConstants;
import org.jetbrains.android.AndroidAnnotatorUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.CompatibilityRenderTarget;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@link com.intellij.openapi.editor.markup.GutterIconRenderer} for Drawable resource references in XML files.
 */
public class GutterIconRenderer extends com.intellij.openapi.editor.markup.GutterIconRenderer implements DumbAware {
  private final static int PREVIEW_MAX_WIDTH = JBUI.scale(128);
  private final static int PREVIEW_MAX_HEIGHT = JBUI.scale(128);
  private final static String PREVIEW_TEXT = "Click Image to Open Resource";

  @NotNull private final ResourceResolver myResourceResolver;
  @NotNull private final AndroidFacet myFacet;
  @Nullable private final VirtualFile myFile;
  @NotNull private final Configuration myConfiguration;
  @NotNull private final Boolean myEditingXmlFile;
  @NotNull private final Consumer<String> myAttributeTask;

  /**
   * @param element          {@link PsiElement} being annotated, usually an XML attribute or tag.
   * @param resourceResolver {@link ResourceResolver} instance used to resolve resources from the active theme.
   * @param facet            the {@link AndroidFacet} for the active module.
   * @param file             the resource file to render in the gutter. Can be an XML file or bitmap. When null, a fallback icon will be
   *                         rendered instead. See {@link #getIcon()}.
   * @param configuration    Android {@link Configuration} associated with the containing file of the annotated element.
   */
  public GutterIconRenderer(@NotNull PsiElement element,
                            @NotNull ResourceResolver resourceResolver,
                            @NotNull AndroidFacet facet,
                            @Nullable VirtualFile file,
                            @NotNull Configuration configuration) {
    myResourceResolver = resourceResolver;
    myFacet = facet;
    myFile = file;
    myConfiguration = configuration;
    myEditingXmlFile = ReadAction.compute(element::getContainingFile).getFileType() == XmlFileType.INSTANCE;
    myAttributeTask = new AndroidAnnotatorUtil.SetAttributeConsumer(element, ResourceType.DRAWABLE);
  }

  @Override
  @NotNull
  public Icon getIcon() {
    Icon icon = myFile != null
                ? GutterIconCache.getInstance().getIcon(myFile, myResourceResolver, myFacet)
                : StudioIcons.LayoutEditor.Properties.IMAGE_PICKER;
    return icon == null ? StudioIcons.LayoutEditor.Properties.IMAGE_PICKER : icon;
  }

  @Override
  @Nullable
  public AnAction getClickAction() {
    return new GutterIconClickAction(myFile, myResourceResolver, myFacet, myConfiguration);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GutterIconRenderer that = (GutterIconRenderer)o;

    if (!myFacet.equals(that.myFacet)) return false;
    if (!Objects.equals(myFile, that.myFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return HashCodes.mix(myFacet.hashCode(), Objects.hashCode(myFile));
  }

  private final static String SET_RESOURCE_COMMAND_NAME = "Resource picked";

  private void setAttribute(@NotNull String attributeString) {
    Project project = myFacet.getModule().getProject();
    ApplicationManager.getApplication().invokeLater(
      () -> WriteCommandAction.runWriteCommandAction(
        project, SET_RESOURCE_COMMAND_NAME, null, () -> myAttributeTask.consume(attributeString)),
      project.getDisposed());
  }

  private static void openImageResourceTab(@NotNull Project project, @NotNull VirtualFile navigationTarget) {
    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, navigationTarget, -1);
    FileEditorManager.getInstance(project).openEditor(descriptor, true);
  }

  private class GutterIconClickAction extends AnAction implements NavigationTargetProvider {

    @Nullable private final VirtualFile myFile;
    @NotNull private final ResourceResolver myResourceResolver;
    @NotNull private final AndroidFacet myFacet;
    @NotNull private final Configuration myConfiguration;
    @Nullable private VirtualFile myNavigationTarget;
    private boolean myNavigationTargetComputed;

    private GutterIconClickAction(@Nullable VirtualFile file, @NotNull ResourceResolver resourceResolver, @NotNull AndroidFacet facet,
                                  @NotNull Configuration configuration) {
      myFile = file;
      myResourceResolver = resourceResolver;
      myFacet = facet;
      myConfiguration = configuration;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
      final Editor editor = event.getData(CommonDataKeys.EDITOR);
      if (editor == null) return;

      Project project = editor.getProject();
      if (project == null) return;

      List<ResourcePickerSources> pickerSources = new ArrayList<>();
      pickerSources.add(ResourcePickerSources.PROJECT);
      pickerSources.add(ResourcePickerSources.ANDROID);
      pickerSources.add(ResourcePickerSources.LIBRARY);
      if (myEditingXmlFile) {
        // We can only support theme attributes for Xml files, since we can't substitute R.color.[resource_name] for a theme attribute.
        pickerSources.add(ResourcePickerSources.THEME_ATTR);
      }
      // Show the resource picker popup.
      ResourceChooserHelperKt.createAndShowResourcePickerPopup(
        ResourceType.DRAWABLE,
        myConfiguration,
        myFacet,
        pickerSources,
        MouseInfo.getPointerInfo().getLocation(),
        resourceReference -> {
          setAttribute(resourceReference);
          return null;
        }
      );
    }

    @Nullable
    private JBPopup createPreview(@Nullable Runnable onClick) {
      if (myFile == null) {
        return null;
      }

      Icon icon = GutterIconFactory.createIcon(myFile, myResourceResolver, PREVIEW_MAX_WIDTH, PREVIEW_MAX_HEIGHT, myFacet);

      if (icon == null) {
        return null;
      }

      JBLabel label = new JBLabel(icon);
      ComponentPopupBuilder builder = JBPopupFactory.getInstance().createComponentPopupBuilder(label, null);
      if (onClick != null) {
        builder.setAdText(PREVIEW_TEXT, SwingConstants.CENTER);
      }

      JBPopup popup = builder.createPopup();

      if (onClick != null) {
        label.addMouseListener(new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent mouseEvent) {
            onClick.run();
            popup.cancel();
            label.removeMouseListener(this);
          }
        });
      }

      return popup;
    }

    @VisibleForTesting
    @Override
    @Nullable
    public VirtualFile getNavigationTarget() {
      if (!myNavigationTargetComputed && myFile != null) {
        IAndroidTarget target = myConfiguration.getTarget();

        // If myFile points to an embedded framework resource intended for rendering only,
        // remap it to a similar framework resource belonging to the project target.
        // This makes navigation by clicking on the image preview consistent with going to
        // declaration on the resource reference in an attribute value. See b/123860195.
        if (target instanceof CompatibilityRenderTarget) {
          PathString renderResourcesRoot = getResourcesRoot(target);
          PathString path = FileExtensions.toPathString(myFile);
          if (path.startsWith(renderResourcesRoot)) {
            IAndroidTarget projectTarget = ConfigurationManager.getOrCreateInstance(myFacet).getProjectTarget();
            if (projectTarget != null) {
              PathString resourcesRoot = getResourcesRoot(projectTarget);
              path = resourcesRoot.resolve(renderResourcesRoot.relativize(path));
              VirtualFile file = FileExtensions.toVirtualFile(path);
              if (file != null && file.exists()) {
                myNavigationTarget = file;
              }
            }
          }
          else {
            myNavigationTarget = myFile;
          }
        }
        myNavigationTargetComputed = true;
      }
      return myNavigationTarget;
    }
  }

  @NotNull
  private static PathString getResourcesRoot(@NotNull IAndroidTarget target) {
    String targetResources = target.getPath(IAndroidTarget.RESOURCES).toString();
    if (SdkUtils.endsWithIgnoreCase(targetResources, DOT_JAR)) {
      return new PathString(JAR_PROTOCOL, targetResources + URLUtil.JAR_SEPARATOR);
    }
    return new PathString(FILE_PROTOCOL, targetResources).getParentOrRoot();
  }

  @VisibleForTesting
  public interface NavigationTargetProvider {
    @Nullable
    VirtualFile getNavigationTarget();
  }
}
