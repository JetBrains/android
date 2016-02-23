/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.templates;

import com.android.SdkConstants;
import com.android.ide.common.xml.XmlFormatPreferences;
import com.android.ide.common.xml.XmlFormatStyle;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.manifmerger.ManifestMerger2;
import com.android.manifmerger.MergingReport;
import com.android.resources.ResourceFolderType;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.utils.StdLogger;
import com.android.utils.XmlUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.*;
import com.intellij.util.SystemProperties;
import freemarker.template.TemplateException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.SdkConstants.*;

/**
 * Utility class to support the recipe.xml merge instruction.
 */
public class RecipeMergeUtils {
  private static final Logger LOG = Logger.getInstance(RecipeMergeUtils.class);

  private static final String MERGE_ATTR_STRATEGY = "templateMergeStrategy";
  private static final String MERGE_ATTR_STRATEGY_REPLACE = "replace";
  private static final String MERGE_ATTR_STRATEGY_PRESERVE = "preserve";

  /**
   * Finds include ':module_name_1', ':module_name_2',... statements in settings.gradle files
   */
  private static final Pattern INCLUDE_PATTERN = Pattern.compile("(^|\\n)\\s*include +(':[^']+', *)*':[^']+'");

  public static String mergeGradleSettingsFile(@NotNull String source, @NotNull String dest) throws IOException, TemplateException {
    // TODO: Right now this is implemented as a dumb text merge. It would be much better to read it into PSI using IJ's Groovy support.
    // If Gradle build files get first-class PSI support in the future, we will pick that up cheaply. At the moment, Our Gradle-Groovy
    // support requires a project, which we don't necessarily have when instantiating a template.

    StringBuilder contents = new StringBuilder(dest);

    for (String line : Splitter.on('\n').omitEmptyStrings().trimResults().split(source)) {
      if (!line.startsWith("include")) {
        throw new RuntimeException("When merging settings.gradle files, only include directives can be merged.");
      }
      line = line.substring("include".length()).trim();

      Matcher matcher = INCLUDE_PATTERN.matcher(contents);
      if (matcher.find()) {
        contents.insert(matcher.end(), ", " + line);
      }
      else {
        contents.insert(0, "include " + line + SystemProperties.getLineSeparator());
      }
    }
    return contents.toString();
  }

  /**
   * Merges sourceXml into targetXml/targetFile (targetXml is the contents of targetFile).
   * Returns the resulting xml if it still needs to be written to targetFile,
   * or null if the file has already been/doesn't need to be updated.
   */
  @Nullable
  public static String mergeXml(@NotNull RenderingContext context, String sourceXml, String targetXml, File targetFile) {
    boolean ok;
    String fileName = targetFile.getName();
    String contents;
    if (fileName.equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
      Document currentDocument = XmlUtils.parseDocumentSilently(targetXml, true);
      assert currentDocument != null : targetXml + " failed to parse";
      Document fragment = XmlUtils.parseDocumentSilently(sourceXml, true);
      assert fragment != null : sourceXml + " failed to parse";
      contents = mergeManifest(targetFile, targetXml, sourceXml);
      ok = contents != null;
    }
    else {
      // Merge plain XML files
      String parentFolderName = targetFile.getParentFile().getName();
      ResourceFolderType folderType = ResourceFolderType.getFolderType(parentFolderName);
      // mergeResourceFile handles the file updates itself, so no content is returned in this case.
      contents = mergeResourceFile(context, targetXml, sourceXml, fileName, folderType);
      ok = contents != null;
    }

    // Finally write out the merged file
    if (!ok) {
      // Just insert into file along with comment, using the "standard" conflict
      // syntax that many tools and editors recognize.

      contents = wrapWithMergeConflict(targetXml, sourceXml);

      // Report the conflict as a warning:
      context.getWarnings().add(String.format("Merge conflict for: %1$s this file must be fixed by hand", targetFile.getName()));
    }
    return contents;
  }

  /**
   * Merges the given resource file contents into the given resource file
   */
  @SuppressWarnings("StatementWithEmptyBody")
  public static String mergeResourceFile(@NotNull RenderingContext context,
                                         @NotNull String targetXml,
                                         @NotNull String sourceXml,
                                         @NotNull String fileName,
                                         @Nullable ResourceFolderType folderType) {
    XmlFile targetPsiFile = (XmlFile)PsiFileFactory.getInstance(context.getProject())
      .createFileFromText("targetFile", XMLLanguage.INSTANCE, StringUtil.convertLineSeparators(targetXml));
    XmlFile sourcePsiFile = (XmlFile)PsiFileFactory.getInstance(context.getProject())
      .createFileFromText("sourceFile", XMLLanguage.INSTANCE, StringUtil.convertLineSeparators(sourceXml));
    XmlTag root = targetPsiFile.getDocument().getRootTag();
    assert root != null : "Cannot find XML root in target: " + targetXml;

    XmlAttribute[] attributes = sourcePsiFile.getRootTag().getAttributes();
    for (XmlAttribute attr : attributes) {
      if (attr.getNamespacePrefix().equals(XMLNS_PREFIX)) {
        root.setAttribute(attr.getName(), attr.getValue());
      }
    }

    List<XmlTagChild> prependElements = Lists.newArrayList();
    XmlText indent = null;
    if (folderType == ResourceFolderType.VALUES) {
      // Try to merge items of the same name
      Map<String, XmlTag> old = Maps.newHashMap();
      for (XmlTag newSibling : root.getSubTags()) {
        old.put(getResourceId(newSibling), newSibling);
      }
      for (PsiElement child : sourcePsiFile.getRootTag().getChildren()) {
        if (child instanceof XmlComment) {
          if (indent != null) {
            prependElements.add(indent);
          }
          prependElements.add((XmlTagChild)child);
        }
        else if (child instanceof XmlText) {
          indent = (XmlText)child;
        }
        else if (child instanceof XmlTag) {
          XmlTag subTag = (XmlTag)child;
          String mergeStrategy = subTag.getAttributeValue(MERGE_ATTR_STRATEGY);
          subTag.setAttribute(MERGE_ATTR_STRATEGY, null);
          // remove the space left by the deleted attribute
          CodeStyleManager.getInstance(context.getProject()).reformat(subTag);
          String name = getResourceId(subTag);
          XmlTag replace = name != null ? old.get(name) : null;
          if (replace != null) {
            // There is an existing item with the same id. Either replace it
            // or preserve it depending on the "templateMergeStrategy" attribute.
            // If that attribute does not exist, default to preserving it.

            // Let's say you've used the activity wizard once, and it
            // emits some configuration parameter as a resource that
            // it depends on, say "padding". Then the user goes and
            // tweaks the padding to some other number.
            // Now running the wizard a *second* time for some new activity,
            // we should NOT go and set the value back to the template's
            // default!
            if (MERGE_ATTR_STRATEGY_REPLACE.equals(mergeStrategy)) {
              child = replace.replace(child);
              // When we're replacing, the line is probably already indented. Skip the initial indent
              if (child.getPrevSibling() instanceof XmlText && prependElements.get(0) instanceof XmlText) {
                prependElements.remove(0);
                // If we're adding something we'll need a newline/indent after it
                if (!prependElements.isEmpty()) {
                  prependElements.add(indent);
                }
              }
              for (XmlTagChild element : prependElements) {
                root.addBefore(element, child);
              }
            }
            else if (MERGE_ATTR_STRATEGY_PRESERVE.equals(mergeStrategy)) {
              // Preserve the existing value.
            }
            else if (replace.getText().trim().equals(child.getText().trim())) {
              // There are no differences, do not issue a warning.
            }
            else {
              // No explicit directive given, preserve the original value by default.
              context.getWarnings().add(String.format(
                "Ignoring conflict for the value: %1$s wanted: \"%2$s\" but it already is: \"%3$s\" in the file: %4$s", name,
                child.getText(), replace.getText(), fileName));
            }
          }
          else {
            if (indent != null) {
              prependElements.add(indent);
            }
            subTag = root.addSubTag(subTag, false);
            for (XmlTagChild element : prependElements) {
              root.addBefore(element, subTag);
            }
          }
          prependElements.clear();
        }
      }
    }
    else {
      // In other file types, such as layouts, just append all the new content
      // at the end.
      for (PsiElement child : sourcePsiFile.getRootTag().getChildren()) {
        if (child instanceof XmlTag) {
          root.addSubTag((XmlTag)child, false);
        }
      }
    }
    return targetPsiFile.getText();
  }

  /**
   * Merges the given manifest fragment into the given manifest file
   */
  @Nullable
  private static String mergeManifest(@NotNull final File targetManifest, @NotNull final String targetXml, @NotNull final String mergeText) {
    try {
      //noinspection SpellCheckingInspection
      final File tempFile2 = new File(targetManifest.getParentFile(), "nevercreated.xml");
      StdLogger logger = new StdLogger(StdLogger.Level.INFO);
      MergingReport mergeReport = ManifestMerger2.newMerger(targetManifest, logger, ManifestMerger2.MergeType.APPLICATION)
        .withFeatures(ManifestMerger2.Invoker.Feature.EXTRACT_FQCNS, ManifestMerger2.Invoker.Feature.NO_PLACEHOLDER_REPLACEMENT)
        .addLibraryManifest(tempFile2)
        .withFileStreamProvider(new ManifestMerger2.FileStreamProvider() {
          @Override
          protected InputStream getInputStream(@NotNull File file) throws FileNotFoundException {
            String text = FileUtil.filesEqual(file, targetManifest) ? targetXml : mergeText;
            return new ByteArrayInputStream(text.getBytes(Charsets.UTF_8));
          }
        })
        .merge();
      if (mergeReport.getResult().isSuccess()) {
        return mergeReport.getMergedDocument(MergingReport.MergedManifestKind.MERGED);
      }
      return null;
    }
    catch (ManifestMerger2.MergeFailureException e) {
      LOG.warn(e);
      return null;
    }
  }


  private static String getResourceId(@NotNull XmlTag tag) {
    String name = tag.getAttributeValue(ATTR_NAME);
    if (name == null) {
      name = tag.getAttributeValue(ATTR_ID);
    }

    return name;
  }

  @NotNull
  private static XmlFormatPreferences createXmlFormatPreferences() {
    // TODO: implement
    return XmlFormatPreferences.defaults();
  }

  /**
   * Wraps the given strings in the standard conflict syntax
   */
  private static String wrapWithMergeConflict(String original, String added) {
    String sep = "\n";
    return "<<<<<<< Original" + sep + original + sep + "=======" + sep + added + ">>>>>>> Added" + sep;
  }
}
