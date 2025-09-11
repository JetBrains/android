// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.formatter;

import com.android.resources.ResourceFolderType;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.intellij.formatting.Alignment;
import com.intellij.formatting.Block;
import com.intellij.formatting.CustomFormattingModelBuilder;
import com.intellij.formatting.DelegatingFormattingModel;
import com.intellij.formatting.FormattingContext;
import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.Indent;
import com.intellij.formatting.Wrap;
import com.intellij.lang.ASTNode;
import com.intellij.lang.html.HtmlCompatibleFile;
import com.intellij.lang.xml.XmlFormattingModelBuilder;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.xml.XmlBlock;
import com.intellij.psi.formatter.xml.XmlPolicy;
import com.intellij.psi.formatter.xml.XmlTagBlock;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.dom.color.fileDescriptions.ColorStateListDomFileDescription;
import org.jetbrains.android.dom.drawable.fileDescriptions.DrawableStateListDomFileDescription;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.formatter.AndroidXmlCodeStyleSettings.MySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AndroidXmlFormattingModelBuilder implements CustomFormattingModelBuilder {
  private final XmlFormattingModelBuilder myXmlFormattingModelBuilder = new XmlFormattingModelBuilder();

  @Override
  public boolean isEngagedToFormat(@NotNull PsiElement context) {
    XmlFile xmlFile = getOriginalContainingXmlFile(context);
    if (xmlFile == null || xmlFile instanceof HtmlCompatibleFile) {
      return false;
    }

    return new ColorStateListDomFileDescription().isMyFile(xmlFile, null) ||
           new DrawableStateListDomFileDescription().isMyFile(xmlFile, null) ||
           ManifestDomFileDescription.isManifestFile(xmlFile) ||
           IdeResourcesUtil.getFolderType(xmlFile) != null;
  }

  @NotNull
  @Override
  public FormattingModel createModel(@NotNull FormattingContext formattingContext) {
    final FormattingModel baseModel = myXmlFormattingModelBuilder.createModel(formattingContext);
    CodeStyleSettings codeStyleSettings = formattingContext.getCodeStyleSettings();
    final AndroidXmlCodeStyleSettings baseSettings = AndroidXmlCodeStyleSettings.getInstance(codeStyleSettings);

    if (!baseSettings.USE_CUSTOM_SETTINGS) {
      return baseModel;
    }

    MySettings settings = getContextSpecificSettings(formattingContext.getPsiElement(), baseSettings);
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
    XmlFile xmlFile = getOriginalContainingXmlFile(context);
    if (xmlFile == null) {
      return null;
    }

    if (new ColorStateListDomFileDescription().isMyFile(xmlFile, null) || new DrawableStateListDomFileDescription().isMyFile(xmlFile, null)) {
      return settings.VALUE_RESOURCE_FILE_SETTINGS;
    }

    if (ManifestDomFileDescription.isManifestFile(xmlFile)) {
      return settings.MANIFEST_SETTINGS;
    }

    ResourceFolderType type = IdeResourcesUtil.getFolderType(xmlFile);

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

  @Nullable
  private static XmlFile getOriginalContainingXmlFile(PsiElement context) {
    // Redirecting to use the original source file. In some cases the given context is in a virtual file that's a copy of an actual source
    // file. That copy doesn't have a parent directory, which is needed to determine which files are layout files.
    PsiFile psiFile = context.getContainingFile().getOriginalFile();
    if (!(psiFile instanceof XmlFile xmlFile)) {
      return null;
    }

    // Some calls originating from FormattingChanges.detectFormattingChanges still use the old mechanism for pointing to the original file.
    PsiFile originalFile = xmlFile.getUserData(PsiFileFactory.ORIGINAL_FILE);
    if (originalFile instanceof XmlFile originalXmlFile) {
      return originalXmlFile;
    }

    return xmlFile;
  }
}
