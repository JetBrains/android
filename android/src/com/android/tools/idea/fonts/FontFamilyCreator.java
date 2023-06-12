/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.fonts;

import static com.android.SdkConstants.ANDROID_URI;
import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.TAG_APPLICATION;
import static com.android.SdkConstants.TAG_ARRAY;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_META_DATA;
import static com.android.SdkConstants.TAG_RESOURCE;

import com.android.ide.common.fonts.FontDetail;
import com.android.ide.common.fonts.FontFamily;
import com.android.ide.common.fonts.FontProvider;
import com.android.ide.common.resources.escape.string.StringResourceEscaper;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.AndroidVersion;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.instantapp.InstantApps;
import com.android.tools.idea.model.AndroidModuleInfo;
import com.android.tools.idea.model.StudioAndroidModuleInfo;
import com.android.tools.lint.checks.FontDetector;
import com.android.utils.XmlUtils;
import com.intellij.core.CoreBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThrowableRunnable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.intellij.lang.annotations.Language;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.ResourceFolderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Create a font file for a new downloadable font.
 *
 * The file can optionally embed the font file directly in the project.
 * Otherwise the following is created:
 * <ul>
 *   <li>An xml font-family file</li>
 *   <li>An xml values file with the certificate for the font provider</li>
 *   <li>An xml values file with a list of all downloadable fonts that should be pre-loaded</li>
 * </ul>
 * In addition the manifest file is updated to include the pre-loaded list of fonts.
 */
public class FontFamilyCreator {
  private static final String PRELOADED_FONTS = "preloaded_fonts";
  private static final String PRELOADED_FONTS_FILE = "preloaded_fonts.xml";
  private static final String FONT_CERTS_FILE = "font_certs.xml";

  private final AndroidFacet myFacet;
  private final Project myProject;
  private final DownloadableFontCacheService myService;

  public FontFamilyCreator(@NotNull AndroidFacet facet) {
    Module module = InstantApps.findBaseFeature(facet);
    myFacet = module != null ? AndroidFacet.getInstance(module) : facet;
    myProject = facet.getModule().getProject();
    myService = DownloadableFontCacheService.getInstance();
  }

  @NotNull
  public String createFontFamily(@NotNull FontDetail font, @NotNull String fontName, boolean downloadable) {
    Project project = myFacet.getModule().getProject();
    final ThrowableRunnable<IOException> throwableRunnable = () -> {
      if (downloadable) {
        createDownloadableFont(font, fontName);
      }
      else {
        createEmbeddedFont(font, fontName);
      }
    };
    ApplicationManager.getApplication().invokeLater(
      () -> {
        try {
          WriteCommandAction.writeCommandAction(project).withName("Create new font file").run(throwableRunnable);
        }
        catch (IOException e) {
          ExceptionUtil.rethrowAllAsUnchecked(e);
        }
      },
      project.getDisposed());
    return "@font/" + fontName;
  }

  private void createDownloadableFont(@NotNull FontDetail font, @NotNull String fontName) throws IOException {
    VirtualFile fontFolder = getResourceFolder(ResourceFolderType.FONT);
    String content = createFontFamilyContent(font);
    saveContent(fontFolder, fontName + ".xml", content.getBytes(StandardCharsets.UTF_8));

    FontProvider provider = font.getFamily().getProvider();
    createCertFileIfNeeded(provider);
    createOrUpdateFile(PRELOADED_FONTS_FILE, TAG_ARRAY, PRELOADED_FONTS, "@font/", fontName, FontFamilyCreator::insertItem);
    addPreloadedFontsToManifest();

    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private void createEmbeddedFont(@NotNull FontDetail font, @NotNull String fontName) throws IOException {
    VirtualFile fontFolder = getResourceFolder(ResourceFolderType.FONT);
    File cachedFile = myService.getCachedFontFile(font);
    if (cachedFile != null && cachedFile.exists()) {
      saveContent(fontFolder, fontName + "." + FileUtilRt.getExtension(cachedFile.getName()), FileUtil.loadFileBytes(cachedFile));
    }
  }

  private void saveContent(@NotNull VirtualFile folder, @NotNull String fileName, byte[] content) throws IOException {
    folder.createChildData(this, fileName).setBinaryContent(content);
  }

  @NotNull
  private VirtualFile getResourceFolder(@NotNull ResourceFolderType folderType) throws IOException {
    @SuppressWarnings("deprecation")
    VirtualFile resourceDirectory = ResourceFolderManager.getInstance(myFacet).getPrimaryFolder();

    if (resourceDirectory == null) {
      throw new IOException("PrimaryResourceDirectory is null");
    }

    VirtualFile fontFolder = resourceDirectory.findChild(folderType.getName());

    if (fontFolder == null) {
      fontFolder = resourceDirectory.createChildDirectory(this, folderType.getName());
    }
    return fontFolder;
  }

  public static String getFontName(@NotNull FontDetail font) {
    String name = font.getFamily().getName();
    String styleName = StringUtil.trimStart(font.getStyleName(), "Regular").trim();
    if (!styleName.isEmpty()) {
      name += " " + styleName;
    }
    return DownloadableFontCacheServiceImpl.convertNameToFilename(name);
  }

  /**
   * The format of the font-family file depends on the min SDK version.
   * <ul>
   *   <li>For O release or above include only android attributes.</li>
   *   <li>For O preview both android and appCompat attributes must be present.</li>
   *   <li>Prior to O preview include only the appCompat attributes.</li>
   * </ul>
   */
  @NotNull
  @Language("XML")
  private String createFontFamilyContent(@NotNull FontDetail font) {
    FontFamily family = font.getFamily();
    FontProvider provider = family.getProvider();
    AndroidModuleInfo info = StudioAndroidModuleInfo.getInstance(myFacet);
    AndroidVersion minSdkVersion = info.getMinSdkVersion();
    if (minSdkVersion.getApiLevel() >= FontDetector.FUTURE_API_VERSION_WHERE_DOWNLOADABLE_FONTS_WORK_IN_FRAMEWORK) {
      return String.format(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
        "<font-family xmlns:android=\"http://schemas.android.com/apk/res/android\"%n" +
        "        android:fontProviderAuthority=\"" + escapeXmlValue(provider.getAuthority()) + "\"%n" +
        "        android:fontProviderPackage=\"" + escapeXmlValue(provider.getPackageName()) + "\"%n" +
        "        android:fontProviderQuery=\"" + escapeXmlValue(font.generateQuery(false)) + "\"%n" +
        "        android:fontProviderCerts=\"@array/" + escapeXmlValue(provider.getCertificateResourceName()) + "\">%n" +
        "</font-family>%n");
    }
    else {
      return String.format(
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
        "<font-family xmlns:app=\"http://schemas.android.com/apk/res-auto\"%n" +
        "        app:fontProviderAuthority=\"" + escapeXmlValue(provider.getAuthority()) + "\"%n" +
        "        app:fontProviderPackage=\"" + escapeXmlValue(provider.getPackageName()) + "\"%n" +
        "        app:fontProviderQuery=\"" + escapeXmlValue(font.generateQuery(false)) + "\"%n" +
        "        app:fontProviderCerts=\"@array/" + escapeXmlValue(provider.getCertificateResourceName()) + "\">%n" +
        "</font-family>%n");
    }
  }

  @NotNull
  @Language("XML")
  private static String createValuesFileContent(@NotNull String tag, @NotNull String name, @NotNull String prefix, @NotNull String value) {
    return String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<resources>%n" +
      "    <" + tag + " name=\"" + escapeXmlValue(name) + "\" translatable=\"false\">%n" +
      "        <item>" + prefix + StringResourceEscaper.escape(value, true) + "</item>%n" +
      "    </" + tag + ">%n" +
      "</resources>%n");
  }

  @NotNull
  @Language("XML")
  private static String createCertificateFileContent(@NotNull FontProvider provider) {
    String certName = escapeXmlValue(provider.getCertificateResourceName());
    return String.format(
      "<?xml version=\"1.0\" encoding=\"utf-8\"?>%n" +
      "<resources>%n" +
      "    <array name=\"" + certName + "\">%n" +
      "        <item>@array/" + certName + "_dev</item>%n" +
      "        <item>@array/" + certName + "_prod</item>%n" +
      "    </array>%n" +
      "    <string-array name=\"" + certName + "_dev\">%n" +
      "        <item>%n" +
      "            " + provider.getDevelopmentCertificate() + "%n" +
      "        </item>%n" +
      "    </string-array>%n" +
      "    <string-array name=\"" + certName + "_prod\">%n" +
      "        <item>%n" +
      "            " + provider.getCertificate() + "%n" +
      "        </item>%n" +
      "    </string-array>%n" +
      "</resources>%n");
  }

  @SuppressWarnings("SameParameterValue")
  private void createOrUpdateFile(@NotNull String fileName,
                                  @NotNull String tagName,
                                  @NotNull String name,
                                  @NotNull String prefix,
                                  @NotNull String value,
                                  @NotNull XmlTagUpdater updater) throws IOException {
    VirtualFile valuesFolder = getResourceFolder(ResourceFolderType.VALUES);
    VirtualFile file = valuesFolder.findChild(fileName);
    if (file == null) {
      String content = createValuesFileContent(tagName, name, prefix, value);
      saveContent(valuesFolder, fileName, content.getBytes(StandardCharsets.UTF_8));
    }
    else {
      PsiFile psiFile = AndroidPsiUtils.getPsiFileSafely(myProject, file);
      if (psiFile instanceof XmlFile) {
        XmlFile xmlFile = (XmlFile)psiFile;
        XmlTag rootTag = xmlFile.getRootTag();
        if (rootTag != null) {
          XmlTag newTag = updateFile(rootTag, tagName, name, prefix, value, tag -> tag.getAttributeValue(ATTR_NAME), updater);
          if (newTag != null) {
            CodeStyleManager.getInstance(myProject).reformat(rootTag);
          }
        }
      }
    }
  }

  private void createCertFileIfNeeded(@NotNull FontProvider provider) throws IOException {
    VirtualFile valuesFolder = getResourceFolder(ResourceFolderType.VALUES);
    VirtualFile file = valuesFolder.findChild(FONT_CERTS_FILE);
    if (file == null) {
      String content = createCertificateFileContent(provider);
      saveContent(valuesFolder, FONT_CERTS_FILE, content.getBytes(StandardCharsets.UTF_8));
    }
  }

  private void addPreloadedFontsToManifest() {
    Manifest manifest = Manifest.getMainManifest(myFacet);
    if (manifest == null) {
      return;
    }
    XmlTag manifestTag = manifest.getXmlTag();
    if (manifestTag == null) {
      return;
    }
    XmlTag applicationTag = manifestTag.findFirstSubTag(TAG_APPLICATION);
    if (applicationTag == null) {
      return;
    }
    XmlTag newTag = null;
    try {
      newTag = updateFile(applicationTag, TAG_META_DATA, PRELOADED_FONTS, "@array/", PRELOADED_FONTS,
                          tag -> tag.getAttributeValue(ATTR_NAME, ANDROID_URI), FontFamilyCreator::setMetaDataAttributes);
    }
    catch (IncorrectOperationException e) {
      String readOnlyErrorMessage = CoreBundle.message("cannot.modify.a.read.only.file", "").split("\'")[0];
      if (e.getMessage().startsWith(readOnlyErrorMessage)) {
        throw new UpdateManifestFileException(
          "Could not add preloaded fonts to read-only manifest file. Please reference the font file manually from Android manifest",
          e);
      }
      throw e;
    }
    if (newTag != null) {
      CodeStyleManager.getInstance(myProject).reformat(newTag);
    }
  }

  @Nullable
  private static XmlTag updateFile(@NotNull XmlTag parent,
                                   @NotNull String tagName,
                                   @NotNull String name,
                                   @NotNull String prefix,
                                   @NotNull String value,
                                   @NotNull Function<XmlTag, String> nameAttributeGetter,
                                   @NotNull XmlTagUpdater updater) {
    // Find best insert position:
    XmlTag before = null;
    XmlTag last = null;
    for (XmlTag tag : parent.getSubTags()) {
      if (tag.getName().equals(tagName)) {
        last = tag;
        String nameAttribute = nameAttributeGetter.apply(tag);
        if (nameAttribute != null && nameAttribute.compareTo(name) >= 0) {
          if (nameAttribute.equals(name)) {
            return updater.update(tag, prefix, value);
          }
          before = tag;
        }
      }
    }
    XmlTag newTag = parent.createChildTag(tagName, "", null, false);
    if (newTag == null) {
      return null;
    }
    if (before != null) {
      newTag = (XmlTag)parent.addBefore(newTag, before);
    }
    else if (last != null) {
      newTag = (XmlTag)parent.addAfter(newTag, last);
    }
    else {
      newTag = parent.addSubTag(newTag, false);
    }
    updater.update(newTag, prefix, value);
    return newTag;
  }

  @Nullable
  private static XmlTag insertItem(@NotNull XmlTag parent, @NotNull String prefix, @NotNull String newValue) {
    XmlTag before = null;
    for (XmlTag tag : parent.getSubTags()) {
      if (tag.getName().equals(TAG_ITEM)) {
        String value = tag.getValue().getText();
        int compare = value.compareTo(prefix + newValue);
        if (compare == 0) {
          return null;  // Item already present
        }
        if (compare > 0) {
          before = tag;
          break;
        }
      }
    }
    XmlTag newTag = parent.createChildTag(TAG_ITEM, "", prefix + StringResourceEscaper.escape(newValue, true), false);
    if (before != null) {
      parent.addBefore(newTag, before);
    }
    else {
      parent.addSubTag(newTag, false);
    }
    return newTag;
  }

  private static XmlTag setMetaDataAttributes(@NotNull XmlTag tag, @NotNull String prefix, @NotNull String newValue) {
    tag.setAttribute(ATTR_NAME, ANDROID_URI, PRELOADED_FONTS);
    tag.setAttribute(TAG_RESOURCE, ANDROID_URI, prefix + escapeXmlValue(newValue));
    return tag;
  }

  private interface XmlTagUpdater {
    XmlTag update(@NotNull XmlTag tag, @NotNull String prefix, @NotNull String value);
  }

  @NotNull
  private static String escapeXmlValue(@NotNull String value) {
    return XmlUtils.toXmlAttributeValue(value);
  }

  public class UpdateManifestFileException extends IncorrectOperationException {

    public UpdateManifestFileException(@NotNull String message, Throwable t) {
      super(message, t);
    }
  }
}
