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

import com.android.ide.common.rendering.api.ResourceNamespace;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.AbstractResourceRepository;
import com.android.ide.common.resources.ResourceItem;
import com.android.ide.common.resources.ResourceResolver;
import com.android.ide.common.resources.ResourceRepositoryUtil;
import com.android.ide.common.resources.configuration.DensityQualifier;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.Density;
import com.android.resources.ResourceType;
import com.android.tools.adtui.LightCalloutPopup;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.res.FileResourceReader;
import com.android.tools.idea.res.LocalResourceRepository;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceRepositoryManager;
import com.android.tools.idea.ui.resourcechooser.ColorPicker;
import com.android.tools.idea.ui.resourcechooser.colorpicker2.ColorPickerBuilder;
import com.android.tools.idea.ui.resourcechooser.colorpicker2.internal.MaterialColorPalette;
import com.android.tools.idea.ui.resourcechooser.colorpicker2.internal.MaterialGraphicalColorPipetteProvider;
import com.android.utils.HashCodes;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.ColorIcon;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.JBUI;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xmlpull.v1.XmlPullParser;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;

import static com.android.SdkConstants.*;

/**
 * Static methods to be used by Android annotators.
 */
public class AndroidAnnotatorUtil {
  static final int MAX_ICON_SIZE = 5000;
  private static final String SET_COLOR_COMMAND_NAME = "Change Color";
  private static final int ICON_SIZE = 8;

  /**
   * Returns a bitmap to be used as an icon to annotate an Android resource reference in an XML file.
   *
   * @param file the XML file being annotated
   * @param resourceResolver the resource resolver to use
   * @param project the project
   * @param facet the android facet
   * @param resourceValue the resource value defining the resource being referenced
   * @return the bitmap for the annotation icon, or null to have no annotation icon
   */
  @Nullable
  public static VirtualFile pickBitmapFromXml(@NotNull VirtualFile file,
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

      String source = null;
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
                return ResourceHelper.getSourceAsVirtualFile(item);
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

        case "selector":
        case "level-list":
        case "layer-list":
        case "transition": {
          int childDepth = parser.getDepth() + 1;
          parser.nextTag();
          while (parser.getDepth() >= childDepth) {
            if (parser.getEventType() == XmlPullParser.START_TAG && parser.getDepth() == childDepth) {
              if (TAG_ITEM.equals(parser.getName())) {
                String value = parser.getAttributeValue(ANDROID_URI, ATTR_DRAWABLE);
                if (value != null) {
                  source = value;
                }
              }
            }
            parser.nextTag();
          }
          break;
        }

        default:
          // <shape> etc - no bitmap to be found.
          return null;
      }
      if (source == null) {
        return null;
      }
      ResourceValue resValue = resourceResolver.findResValue(source, resourceValue.isFramework());
      return resValue == null ? null : ResourceHelper.resolveDrawable(resourceResolver, resValue, project);
    }
    catch (Throwable ignore) {
      // Not logging for now; afraid to risk unexpected crashes in upcoming preview. TODO: Re-enable.
      //Logger.getInstance(AndroidColorAnnotator.class).warn(String.format("Could not read/render icon image %1$s", file), e);
      return null;
    }
  }

  @Nullable
  public static VirtualFile pickBestBitmap(@Nullable VirtualFile bitmap) {
    if (bitmap != null && bitmap.exists()) {
      // Pick the smallest resolution, if possible! E.g. if the theme resolver located
      // drawable-hdpi/foo.png, and drawable-mdpi/foo.png pick that one instead (and ditto
      // for -ldpi etc)
      VirtualFile smallest = findSmallestDpiVersion(bitmap);
      if (smallest != null) {
        return smallest;
      }

      // TODO: For XML drawables, look in the rendered output to see if there's a DPI version we can use:
      // These are found in  ${module}/build/generated/res/pngs/debug/drawable-*dpi

      long length = bitmap.getLength();
      if (length < MAX_ICON_SIZE) {
        return bitmap;
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
      for (int i = densities.length; --i >= 0;) {
        Density d = densities[i];
        if (d.isValidValueForDevice()) {
          String folderName = parentName.replace(density.getResourceValue(), d.getResourceValue());
          VirtualFile folder = resFolder.findChild(folderName);
          if (folder != null) {
            bitmap = folder.findChild(fileName);
            if (bitmap != null) {
              if (bitmap.getLength() > MAX_ICON_SIZE) {
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

  /** Looks up the resource item of the given type and name for the given configuration, if any. */
  @Nullable
  public static ResourceValue findResourceValue(@NotNull ResourceType type,
                                                @NotNull String name,
                                                boolean isFramework,
                                                @NotNull Module module,
                                                @NotNull Configuration configuration) {
    if (isFramework) {
      AbstractResourceRepository frameworkResources = configuration.getFrameworkResources();
      if (frameworkResources == null) {
        return null;
      }
      List<ResourceItem> items = frameworkResources.getResources(ResourceNamespace.ANDROID, type, name);
      if (items.isEmpty()) {
        return null;
      }
      return items.get(0).getResourceValue();
    } else {
      LocalResourceRepository appResources = ResourceRepositoryManager.getAppResources(module);
      if (appResources == null) {
        return null;
      }
      if (!appResources.hasResources(ResourceNamespace.TODO(), type, name)) {
        return null;
      }
      return ResourceRepositoryUtil.getConfiguredValue(appResources, type, name, configuration.getFullConfig());
    }
  }

  /** Picks a suitable configuration to use for resource resolution */
  @Nullable
  public static Configuration pickConfiguration(@NotNull AndroidFacet facet, @NotNull Module module, @NotNull PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return null;
    }

    VirtualFile parent = virtualFile.getParent();
    if (parent == null) {
      return null;
    }
    VirtualFile layout;
    String parentName = parent.getName();
    if (!parentName.startsWith(FD_RES_LAYOUT)) {
      layout = ResourceHelper.pickAnyLayoutFile(module, facet);
      if (layout == null) {
        return null;
      }
    } else {
      layout = virtualFile;
    }

    return ConfigurationManager.getOrCreateInstance(module).getConfiguration(layout);
  }

  public static class ColorRenderer extends GutterIconRenderer {
    private final PsiElement myElement;
    private final Color myColor;

    public ColorRenderer(@NotNull PsiElement element, @Nullable Color color) {
      myElement = element;
      myColor = color;
    }

    @NotNull
    @Override
    public Icon getIcon() {
      Color color = getCurrentColor();
      return color == null ? JBUI.scale(EmptyIcon.create(ICON_SIZE)) : JBUI.scale(new ColorIcon(ICON_SIZE, color));
    }

    @Nullable
    private Color getCurrentColor() {
      if (myColor != null) {
        return myColor;
      } else if (myElement instanceof XmlTag) {
        return ResourceHelper.parseColor(((XmlTag)myElement).getValue().getText());
      } else if (myElement instanceof XmlAttributeValue) {
        return ResourceHelper.parseColor(((XmlAttributeValue)myElement).getValue());
      } else {
        return null;
      }
    }

    @Override
    public AnAction getClickAction() {
      if (myColor != null) { // Cannot set colors that were derived.
        return null;
      }
      return new AnAction() {
        @Override
        public void actionPerformed(AnActionEvent e) {
          Editor editor = CommonDataKeys.EDITOR.getData(e.getDataContext());
          if (editor != null) {
            // Need ARGB support in platform color chooser; see
            //  https://youtrack.jetbrains.com/issue/IDEA-123498
            //Color color =
            //  ColorChooser.chooseColor(editor.getComponent(), AndroidBundle.message("android.choose.color"), getCurrentColor());
            if (StudioFlags.NELE_NEW_COLOR_PICKER.get()) {
              openNewColorPicker(getCurrentColor());
            }
            else {
              Color color = ColorPicker.showDialog(editor.getComponent(), "Choose Color", getCurrentColor(), true, null, false);
              if (color != null) {
                setColorToAttribute(color);
              }
            }
          }
        }
      };
    }

    private void openNewColorPicker(@Nullable Color currentColor) {
      LightCalloutPopup dialog = new LightCalloutPopup();

      Function1<Color, Unit> okCallback = c -> {
        setColorToAttribute(c);
        dialog.close();
        return Unit.INSTANCE;
      };

      Function1<Color, Unit> cancelCallback = c -> {
        dialog.close();
        return Unit.INSTANCE;
      };

      JPanel panel = new ColorPickerBuilder()
          .setOriginalColor(currentColor)
          .addSaturationBrightnessComponent()
          .addColorAdjustPanel(new MaterialGraphicalColorPipetteProvider())
          .addColorValuePanel()
          .addSeparator()
          .addCustomComponent(model -> new MaterialColorPalette(model))
          .addSeparator()
          .addOperationPanel(okCallback, cancelCallback)
          .addKeyAction(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
              dialog.close();
            }
          })
          .build();

      dialog.show(panel, null, MouseInfo.getPointerInfo().getLocation());
    }

    private void setColorToAttribute(@NotNull Color color) {
      // Use TransactionGuard to avoid write in unsafe context, and use WriteCommandAction to make the change undoable.
      TransactionGuard.submitTransaction(myElement.getProject(), () ->
        WriteCommandAction.runWriteCommandAction(myElement.getProject(), SET_COLOR_COMMAND_NAME, null, () -> {
          if (myElement instanceof XmlTag) {
            ((XmlTag)myElement).getValue().setText(ResourceHelper.colorToString(color));
          }
          else if (myElement instanceof XmlAttributeValue) {
            XmlAttribute attribute = PsiTreeUtil.getParentOfType(myElement, XmlAttribute.class);
            if (attribute != null) {
              attribute.setValue(ResourceHelper.colorToString(color));
            }
          }
        })
      );
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      ColorRenderer that = (ColorRenderer)o;
      // TODO: Compare with modification count in app resources (if not framework).
      if (myColor != null ? !myColor.equals(that.myColor) : that.myColor != null) return false;
      if (!myElement.equals(that.myElement)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return HashCodes.mix(myElement.hashCode(), Objects.hashCode(myColor));
    }
  }
}
