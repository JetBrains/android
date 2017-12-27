package org.jetbrains.android.formatter;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.ResourceHelper;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.lang.xml.XmlFormattingModelBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.formatter.xml.XmlTagBlock;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.color.ColorDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.DrawableStateListDomFileDescription;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings.MySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlFormattingModelBuilder implements CustomFormattingModelBuilder {
  private final XmlFormattingModelBuilder myXmlFormattingModelBuilder = new XmlFormattingModelBuilder();

  @Override
  public boolean isEngagedToFormat(@NotNull PsiElement context) {
    Object psiFile = context.getContainingFile();

    if (!(psiFile instanceof XmlFile)) {
      return false;
    }

    XmlFile xmlFile = (XmlFile)psiFile;

    return ColorDomFileDescription.isColorResourceFile(xmlFile) ||
           new DrawableStateListDomFileDescription().isMyFile(xmlFile, null) ||
           ManifestDomFileDescription.isManifestFile(xmlFile) ||
           ResourceHelper.getFolderType(xmlFile) != null;
  }

  @NotNull
  @Override
  public FormattingModel createModel(PsiElement element, CodeStyleSettings codeStyleSettings) {
    final FormattingModel baseModel = myXmlFormattingModelBuilder.createModel(element, codeStyleSettings);
    final AndroidXmlCodeStyleSettings baseSettings = AndroidXmlCodeStyleSettings.getInstance(codeStyleSettings);

    if (!baseSettings.USE_CUSTOM_SETTINGS) {
      return baseModel;
    }

    MySettings settings = getContextSpecificSettings(element, baseSettings);
    return settings != null
           ? new DelegatingFormattingModel(baseModel, createDelegatingBlock(baseModel, settings, codeStyleSettings))
           : baseModel;
  }

  private static Block createDelegatingBlock(FormattingModel model,
                                             AndroidXmlCodeStyleSettings.MySettings customSettings,
                                             CodeStyleSettings settings) {
    final Block block = model.getRootBlock();

    if (block instanceof XmlBlock) {
      final XmlBlock b = (XmlBlock)block;
      final XmlPolicy policy = customSettings.createXmlPolicy(settings, model.getDocumentModel());
      return new XmlBlock(b.getNode(), b.getWrap(), b.getAlignment(), policy, b.getIndent(), b.getTextRange()) {
        @Override
        protected XmlTagBlock createTagBlock(ASTNode child, Indent indent, Wrap wrap, Alignment alignment) {
          return new XmlTagBlock(child, wrap, alignment, myXmlFormattingPolicy,
                                 indent != null ? indent : Indent.getNoneIndent(),
                                 isPreserveSpace());
        }
      };
    }
    else {
      return block;
    }
  }

  @Nullable
  @Override
  public TextRange getRangeAffectingIndent(PsiFile file, int offset, ASTNode elementAtOffset) {
    return myXmlFormattingModelBuilder.getRangeAffectingIndent(file, offset, elementAtOffset);
  }

  @Nullable
  private static MySettings getContextSpecificSettings(@NotNull PsiElement context, @NotNull AndroidXmlCodeStyleSettings settings) {
    Object psiFile = context.getContainingFile();

    if (!(psiFile instanceof XmlFile)) {
      return null;
    }

    XmlFile xmlFile = (XmlFile)psiFile;

    if (ColorDomFileDescription.isColorResourceFile(xmlFile) || new DrawableStateListDomFileDescription().isMyFile(xmlFile, null)) {
      return settings.VALUE_RESOURCE_FILE_SETTINGS;
    }

    if (ManifestDomFileDescription.isManifestFile(xmlFile)) {
      return settings.MANIFEST_SETTINGS;
    }

    ResourceFolderType type = ResourceHelper.getFolderType(xmlFile);

    if (type == null) {
      return null;
    }

    switch (type) {
      case ANIM:
      case ANIMATOR:
      case COLOR:
      case DRAWABLE:
      case FONT:
      case INTERPOLATOR:
        return settings.OTHER_SETTINGS;
      case LAYOUT:
        return settings.LAYOUT_SETTINGS;
      case MENU:
      case MIPMAP:
      case NAVIGATION:
      case RAW:
      case TRANSITION:
        return settings.OTHER_SETTINGS;
      case VALUES:
        return settings.VALUE_RESOURCE_FILE_SETTINGS;
      case XML:
        return settings.OTHER_SETTINGS;
      default:
        return null;
    }
  }
}
