/*
 * Copyright (C) 2018 The Android Open Source Project
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
package org.jetbrains.android;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_DRAWABLE;
import static com.android.SdkConstants.ATTR_SRC;
import static com.android.SdkConstants.DOT_XML;

import com.android.SdkConstants;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.res.FileResourceReader;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.ui.resourcechooser.common.ResourcePickerSources;
import com.android.tools.idea.ui.resourcechooser.util.ResourceChooserHelperKt;
import com.android.tools.idea.ui.resourcemanager.rendering.MultipleColorIcon;
import com.android.utils.HashCodes;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.Consumer;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import java.awt.Color;
import java.awt.MouseInfo;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.Icon;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtPsiFactoryKt;
import org.xmlpull.v1.XmlPullParser;

/**
 * Static methods to be used by Android annotators.
 */
public class AndroidAnnotatorUtil {
  static final int MAX_ICON_FILE_SIZE = 20000;
  private static final String SET_COLOR_COMMAND_NAME = "Change Color";
  private static final int ICON_SIZE = 8;

  /**
   * Returns a bitmap to be used as an icon to annotate an Android resource reference in an XML file.
   *
   * @param resourceValue    the resource value defining the resource being referenced
   * @param resourceResolver the resource resolver to use
   * @param facet            the android facet
   * @return the bitmap for the annotation icon, or null to have no annotation icon
   */
  @Nullable
  public static VirtualFile resolveDrawableFile(@NotNull ResourceValue resourceValue,
                                                @NotNull ResourceResolver resourceResolver,
                                                @NotNull AndroidFacet facet) {
    Project project = facet.getModule().getProject();
    VirtualFile file = IdeResourcesUtil.resolveDrawable(resourceResolver, resourceValue, project);
    if (file != null && file.getPath().endsWith(DOT_XML)) {
      file = pickRenderableFileFromXML(file, resourceResolver, project, facet, resourceValue);
    }
    return pickSmallestDpiFile(file);
  }

  @Nullable
  private static VirtualFile pickRenderableFileFromXML(@NotNull VirtualFile file,
                                                       @NotNull ResourceResolver resourceResolver,
                                                       @NotNull Project project,
                                                       @NotNull AndroidFacet facet,
                                                       @NotNull ResourceValue resourceValue) {
    try {
      XmlPullParser parser = FileResourceReader.createXmlPullParser(file);
      if (parser == null) {
        return null;
      }
      if (parser.nextTag() != XmlPullParser.START_TAG) {
        return null;
      }

      String source;
      String tagName = parser.getName();

      switch (tagName) {
        case "vector": {
          // Take a look and see if we have a bitmap we can fall back to.
          LocalResourceRepository resourceRepository = ResourceRepositoryManager.getAppResources(facet);
          List<ResourceItem> items =
            resourceRepository.getResources(resourceValue.getNamespace(), resourceValue.getResourceType(), resourceValue.getName());
          for (ResourceItem item : items) {
            FolderConfiguration configuration = item.getConfiguration();
            DensityQualifier densityQualifier = configuration.getDensityQualifier();
            if (densityQualifier != null) {
              Density density = densityQualifier.getValue();
              if (density != null && density.isValidValueForDevice()) {
                return IdeResourcesUtil.getSourceAsVirtualFile(item);
              }
            }
          }
          // Vectors are handled in the icon cache.
          return file;
        }

        case "bitmap":
        case "nine-patch":
          source = parser.getAttributeValue(ANDROID_URI, ATTR_SRC);
          break;

        case "clip":
        case "inset":
        case "scale":
          source = parser.getAttributeValue(ANDROID_URI, ATTR_DRAWABLE);
          break;

        default:
          // <set>, <drawable> etc - no bitmap to be found. These need to rendered by layoutlib.
          return file;
      }
      if (source == null) {
        return null;
      }
      ResourceValue resValue = resourceResolver.findResValue(source, resourceValue.isFramework());
      return resValue == null ? null : IdeResourcesUtil.resolveDrawable(resourceResolver, resValue, project);
    }
    catch (Throwable ignore) {
      // Not logging for now; afraid to risk unexpected crashes in upcoming preview. TODO: Re-enable.
      //Logger.getInstance(AndroidColorAnnotator.class).warn(String.format("Could not read/render icon image %1$s", file), e);
      return null;
    }
  }

  @Nullable
  public static VirtualFile pickSmallestDpiFile(@Nullable VirtualFile resourceFile) {
    if (resourceFile != null && resourceFile.exists()) {
      // Pick the smallest resolution, if possible! E.g. if the theme resolver located
      // drawable-hdpi/foo.png, and drawable-mdpi/foo.png pick that one instead (and ditto
      // for -ldpi etc)
      VirtualFile smallest = findSmallestDpiVersion(resourceFile);
      if (smallest != null) {
        return smallest;
      }

      // TODO: For XML drawables, look in the rendered output to see if there's a DPI version we can use:
      // These are found in  ${module}/build/generated/res/pngs/debug/drawable-*dpi

      long length = resourceFile.getLength();
      if (length < MAX_ICON_FILE_SIZE) {
        return resourceFile;
      }
    }

    return null;
  }

  @Nullable
  private static VirtualFile findSmallestDpiVersion(@NotNull VirtualFile bitmap) {
    VirtualFile parentFile = bitmap.getParent();
    if (parentFile == null) {
      return null;
    }
    VirtualFile resFolder = parentFile.getParent();
    if (resFolder == null) {
      return null;
    }
    String parentName = parentFile.getName();
    FolderConfiguration config = FolderConfiguration.getConfigForFolder(parentName);
    if (config == null) {
      return null;
    }
    DensityQualifier qualifier = config.getDensityQualifier();
    if (qualifier == null) {
      return null;
    }
    Density density = qualifier.getValue();
    if (density != null && density.isValidValueForDevice()) {
      String fileName = bitmap.getName();
      Density[] densities = Density.values();
      // Iterate in reverse, since the Density enum is in descending order.
      for (int i = densities.length; --i >= 0; ) {
        Density d = densities[i];
        if (d.isValidValueForDevice()) {
          String folderName = parentName.replace(density.getResourceValue(), d.getResourceValue());
          VirtualFile folder = resFolder.findChild(folderName);
          if (folder != null) {
            bitmap = folder.findChild(fileName);
            if (bitmap != null) {
              if (bitmap.getLength() > MAX_ICON_FILE_SIZE) {
                // No point continuing the loop; the other densities will be too big too.
                return null;
              }
              return bitmap;
            }
          }
        }
      }
    }

    return null;
  }

  /**
   * Picks a suitable configuration to use for resource resolution within a given file.
   *
   * @param file  the file to determine a configuration for
   * @param facet {@link AndroidFacet} of the {@code file}
   */
  @Nullable
  public static Configuration pickConfiguration(@NotNull PsiFile file, @NotNull AndroidFacet facet) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }

    VirtualFile nearestConfigurationFile;
    ConfigurationManager configurationManager = ConfigurationManager.getOrCreateInstance(facet);
    if (!(file instanceof XmlFile)) {
      nearestConfigurationFile = IdeResourcesUtil.pickAnyLayoutFile(facet);
      if (nearestConfigurationFile == null) {
        return Configuration.create(configurationManager, null, FolderConfiguration.createDefault());
      }
    }
    else {
      nearestConfigurationFile = virtualFile;
    }

    return configurationManager.getConfiguration(nearestConfigurationFile);
  }


  /**
   * Return {@link FileType} if found, or {@link UnknownFileType#INSTANCE} otherwise.
   */
  @NotNull
  public static FileType getFileType(@NotNull PsiElement element) {
    return ApplicationManager.getApplication().runReadAction((Computable<FileType>)() -> {
      PsiFile file = element.getContainingFile();
      if (file != null) {
        return file.getFileType();
      }
      return UnknownFileType.INSTANCE;
    });
  }

  public static class ColorRenderer extends GutterIconRenderer {
    @NotNull private final PsiElement myElement;
    @Nullable private final Color myColor;
    @NotNull private final ResourceResolver myResolver;
    @Nullable private final ResourceValue myResourceValue;
    private final Consumer<String> mySetColorTask;
    private final boolean myIncludeClickAction;
    private final boolean myHasCustomColor;
    // TODO(b/188937633): We should fix the root caused of memory leakage instead of using weak references.
    @Nullable private final WeakReference<Configuration> myConfigurationRef;

    public ColorRenderer(@NotNull PsiElement element,
                         @Nullable Color color,
                         @NotNull ResourceResolver resolver,
                         @Nullable ResourceValue resourceValue,
                         boolean hasCustomColor,
                         @Nullable Configuration configuration) {
      myElement = element;
      myColor = color;
      myResolver = resolver;
      myResourceValue = resourceValue;

      myIncludeClickAction = true;
      myHasCustomColor = hasCustomColor;
      mySetColorTask = new SetAttributeConsumer(element, ResourceType.COLOR);
      myConfigurationRef = new WeakReference<>(configuration);
    }

    @NotNull
    @Override
    public Icon getIcon() {
      if (myResourceValue != null && myElement.isValid()) {
        AndroidFacet facet = AndroidFacet.getInstance(myElement);
        if (facet != null) {
          List<Color> colors = IdeResourcesUtil.resolveMultipleColors(myResolver, myResourceValue, facet.getModule().getProject());
          if (!colors.isEmpty()) {
            MultipleColorIcon icon = new MultipleColorIcon();
            icon.setColors(colors);
            int scaledIconSize = JBUIScale.scale(ICON_SIZE);
            icon.setWidth(scaledIconSize);
            icon.setHeight(scaledIconSize);
            return icon;
          }
          return JBUIScale.scaleIcon(EmptyIcon.create(ICON_SIZE));
        }
      }

      Color color = getCurrentColor();
      return color == null ? JBUIScale.scaleIcon(EmptyIcon.create(ICON_SIZE)) : JBUIScale.scaleIcon(new ColorIcon(ICON_SIZE, color));
    }

    @Nullable
    private Color getCurrentColor() {
      if (myColor != null) {
        return myColor;
      }
      if (myElement.isValid()) {
        if (myElement instanceof XmlTag) {
          return IdeResourcesUtil.parseColor(((XmlTag)myElement).getValue().getText());
        }
        else if (myElement instanceof XmlAttributeValue) {
          return IdeResourcesUtil.parseColor(((XmlAttributeValue)myElement).getValue());
        }
      }
      return null;
    }

    @Override
    public AnAction getClickAction() {
      if (!myIncludeClickAction) { // Cannot set colors that were derived.
        return null;
      }
      return new AnAction() {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          Editor editor = e.getData(CommonDataKeys.EDITOR);
          if (editor != null) {
            openColorPicker(getCurrentColor());
          }
        }
      };
    }

    @TestOnly
    @NotNull
    public PsiElement getElement() {
      return myElement;
    }

    private void openColorPicker(@Nullable Color currentColor) {
      List<ResourcePickerSources> pickerSources = new ArrayList<>();
      pickerSources.add(ResourcePickerSources.PROJECT);
      pickerSources.add(ResourcePickerSources.ANDROID);
      pickerSources.add(ResourcePickerSources.LIBRARY);
      if (getFileType(myElement) == XmlFileType.INSTANCE) {
        // We can only support theme attributes for Xml files, since we can't substitute R.color.[resource_name] for a theme attribute.
        pickerSources.add(ResourcePickerSources.THEME_ATTR);
      }

      // TODO: When the color is color state, open color picker with resource tab and select it.
      ResourceChooserHelperKt.createAndShowColorPickerPopup(
        currentColor,
        myResourceValue,
        myConfigurationRef.get(),
        pickerSources,
        null,
        MouseInfo.getPointerInfo().getLocation(),
        myHasCustomColor ? color -> {
          setColorToAttribute(color);
          return null;
        } : null,
        resourceString -> {
          setColorStringAttribute(resourceString);
          return null;
        });
    }

    private void setColorToAttribute(@NotNull Color color) {
      setColorStringAttribute(IdeResourcesUtil.colorToString(color));
    }

    private void setColorStringAttribute(@NotNull String colorString) {
      Project project = myElement.getProject();
      ApplicationManager.getApplication().invokeLater(
        () -> WriteCommandAction.runWriteCommandAction(project, SET_COLOR_COMMAND_NAME, null, () -> mySetColorTask.consume(colorString)),
        project.getDisposed());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ColorRenderer that = (ColorRenderer)o;
      // TODO: Compare with modification count in app resources (if not framework).
      if (!Objects.equals(myColor, that.myColor)) return false;
      if (!myElement.equals(that.myElement)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return HashCodes.mix(myElement.hashCode(), Objects.hashCode(myColor));
    }
  }

  /**
   * A {@link Consumer} that can be used to edit resources in XML and Java/Kt files.
   * <p>
   * Supports Tags and Attribute for XML and it uses the resource expression: namespace.R.resource_type.resource_name for Java/Kt.
   */
  public static class SetAttributeConsumer implements Consumer<String> {
    private PsiElement myElement;
    private final Consumer<String> myAttributeConsumer;
    private final ResourceType myResourceType;

    /**
     * @param psiElement   The PsiElement of the reference to edit. {@link XmlTag} or {@link XmlAttributeValue} for XML.
     * {@link PsiReferenceExpression} for Java and {@link KtNameReferenceExpression} for Kotlin
     * @param resourceType The type of the resource reference being edited.
     */
    public SetAttributeConsumer(PsiElement psiElement, ResourceType resourceType) {
      myElement = psiElement;
      myResourceType = resourceType;
      myAttributeConsumer = createSetAttributeTask();
    }

    @VisibleForTesting
    @NotNull
    public PsiElement getElement() {
      return myElement;
    }

    @Override
    public void consume(String s) {
      myAttributeConsumer.consume(s);
    }

    /**
     * Returns a {@link Consumer} that sets the value for all eligible Java, Kotlin, and Xml files.
     * For now it supports Java/Kotlin files with (android.)R.resource_type and XML resource files.
     */
    @NotNull
    private Consumer<String> createSetAttributeTask() {
      return attributeValue -> {
        PsiElement psiElement = myElement;
        if (psiElement instanceof PsiReferenceExpression || psiElement instanceof KtNameReferenceExpression) {
          // The element is in Java or kotlin file.
          // In Java file, the type of psiElement is PsiReferenceExpression. Its text is "R.color.[resource_name]" or "android.R.color.xxx"
          // In Kotlin file, the type of psiElement is KtNameReferenceExpression. Its text is [resource_name].
          myElement = setJavaOrKotlinAttribute(psiElement, attributeValue, myResourceType);
        }
        else if (psiElement != null) {
          // xml file cases.
          myElement = setXmlAttribute(psiElement, attributeValue);
        }
      };
    }
  }

  /**
   * Convert color attribute value (e.g. @color/resource_name or @android:color/resource_name) to Java/Kotlin Identifier.<br>
   * The returned identifier will be android.R.color.resource_name or R.color.resource_name.<br>
   * If the given color attribute value is not eligible, return null.
   */
  @Nullable
  private static String convertResourceAttributeToIdentifier(@NotNull String colorAttributeValue, @NotNull ResourceType resourceType) {
    int nameStartIndex = colorAttributeValue.lastIndexOf('/');
    if (nameStartIndex == -1) {
      return null;
    }
    String resourceName = colorAttributeValue.substring(nameStartIndex + 1);
    if (resourceName.isEmpty()) {
      return null;
    }

    StringBuilder builder = new StringBuilder();
    if (colorAttributeValue.startsWith(SdkConstants.ANDROID_PREFIX)) {
      builder.append(SdkConstants.ANDROID_PKG_PREFIX);
    }
    return builder.append(SdkConstants.R_PREFIX)
      .append(resourceType.getName()).append(".")
      .append(resourceName)
      .toString();
  }

  /**
   * Replaces the PsiElement for an expression of the given attribute value.
   *
   * @param psiElement The PsiElement of the resource reference being edited. Eg: The PsiElement of 'R.color.foo_color'
   * @param attributeValue Resource reference String to be written. Expected in the form: '@color/resource_name'
   * @return The new {@link PsiElement} resulting from replacing the original element for an expression of the given value.
   */
  public static PsiElement setJavaOrKotlinAttribute(@NotNull PsiElement psiElement,
                                                    @NotNull String attributeValue,
                                                    @NotNull ResourceType resourceType) {
    String resourceIdentifier = convertResourceAttributeToIdentifier(attributeValue, resourceType);
    if (resourceIdentifier == null) {
      return psiElement;
    }
    if (psiElement instanceof PsiReferenceExpression) {
      // Java file case.
      PsiExpression expression =
        PsiElementFactory.getInstance(psiElement.getProject()).createExpressionFromText(resourceIdentifier, psiElement);
      return psiElement.replace(expression);
    }
    else {
      // Kotlin file case
      KtExpression expression = KtPsiFactoryKt.KtPsiFactory(psiElement.getProject()).createExpression(resourceIdentifier);
      // Replace the parent with the resulting expression, but return the last child, which corresponds to the name of the resource
      return psiElement.getParent().replace(expression).getLastChild();
    }
  }

  /**
   * Sets the given attributeValue to the {@link XmlTag} or {@link XmlAttributeValue} given.
   * @return The {@link PsiElement} of either the original {@link XmlTag} or the new {@link XmlAttributeValue} set.
   */
  public static PsiElement setXmlAttribute(@NotNull PsiElement element, @NotNull String attributeValue) {
    if (element instanceof XmlTag) {
      XmlTagValue xmlTagValue = ((XmlTag)element).getValue();
      xmlTagValue.setText(attributeValue);
    }
    else if (element instanceof XmlAttributeValue) {
      XmlAttribute xmlAttribute = PsiTreeUtil.getParentOfType(element, XmlAttribute.class);
      if (xmlAttribute != null) {
        xmlAttribute.setValue(attributeValue);
        return xmlAttribute.getValueElement();
      }
    }
    return element;
  }
}
