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
package com.android.tools.idea.actions;

import com.android.SdkConstants;
import com.android.tools.idea.stats.UsageTracker;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Generate an deep link intent filter for activity in AndroidManifest.xml.
 * This action is inside code generator group.
 */
public class DeepLinkCodeGeneratorAction extends AnAction {
  private static final String IF_CONTENT_FORMAT =
      "\n<action %1$s:name=\"android.intent.action.VIEW\" />" +
      "\n<category %1$s:name=\"android.intent.category.DEFAULT\" />" +
      "\n<category %1$s:name=\"android.intent.category.BROWSABLE\" />" +
      "\n<data " +
      "\n%1$s:host=\"%2$s\"" +
      "\n%1$s:pathPrefix=\"%3$s\"" +
      "\n%1$s:scheme=\"http\"/>";
  private static final String IF_COMMENT_TEXT =
      "<!-- ATTENTION: This intent was auto-generated. Follow instructions at\n" +
      "  https://g.co/AppIndexing/AndroidStudio to publish your URLs. -->";
  private static final String DATA_COMMENT_TEXT =
      "<!-- ATTENTION: This data URL was auto-generated. We recommend that you use the HTTP scheme.\n" +
      "  TODO: Change the host or pathPrefix as necessary. -->";
  private static final String ACTIVITY_STRING = "activity";

  @Override
  public void update(AnActionEvent e) {
    Editor editor = e.getData(PlatformDataKeys.EDITOR);
    PsiFile file = e.getData(LangDataKeys.PSI_FILE);

    e.getPresentation().setEnabled(isDeepLinkAvailable(editor, file));
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    final Editor editor = e.getData(PlatformDataKeys.EDITOR);
    final PsiFile file = e.getData(LangDataKeys.PSI_FILE);

    if (project != null && editor != null && file != null) {
      UsageTracker.getInstance().trackEvent(
          UsageTracker.CATEGORY_APP_INDEXING, UsageTracker.ACTION_APP_INDEXING_DEEP_LINK_CREATED, null, null);
      WriteCommandAction.runWriteCommandAction(project, new Runnable() {
        @Override
        public void run() {
          addDeepLinkAtCaret(project, editor, file);
        }
      });
    }
  }

  /**
   * Whether we should enable insert Deep Link action.
   */
  public static boolean isDeepLinkAvailable(@Nullable Editor editor, @Nullable PsiFile file) {
    return file != null && editor != null && isAndroidManifestXmlFile(file)
           && isInsideActivityTag(editor, file);
  }

  /**
   * If the psi file is AndroidManifest.xml.
   */
  private static boolean isAndroidManifestXmlFile(@NotNull PsiFile file) {
    return file.getName().equalsIgnoreCase(SdkConstants.FN_ANDROID_MANIFEST_XML);
  }

  /**
   * If the current caret is inside a "activity" xml tag.
   */
  private static boolean isInsideActivityTag(@NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement != null) {
      return findXmlTagByName(psiElement, SdkConstants.TAG_ACTIVITY) != null;
    }
    return false;
  }

  /**
   * Finds a xml tag with given tag name, or {@code null} if not found. If the given psi element
   * is not the xml tag we want, the method will check its ancestors until reaching the root.
   */
  @Nullable
  private static XmlTag findXmlTagByName(PsiElement element, String tagName) {
    if (element == null) {
      return null;
    }

    while (element != null) {
      if (element instanceof XmlTag) {
        XmlTag tag = (XmlTag)element;
        if (tag.getName().equals(tagName)) {
          return tag;
        }
      }
      if (element instanceof PsiFile) {
        return null;
      }
      element = element.getParent();
    }
    return null;
  }

  /**
   * Inserts a deep link intent filter for the activity where the caret is in.
   */
  public static void addDeepLinkAtCaret(
      @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    PsiElement psiElement = file.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement != null) {
      XmlTag activity = findXmlTagByName(psiElement, SdkConstants.TAG_ACTIVITY);
      XmlTag manifest = ((XmlFile)file).getRootTag();
      if (activity != null && manifest != null) {
        String prefix = manifest.getPrefixByNamespace(SdkConstants.ANDROID_URI);
        String packageName = manifest.getAttributeValue(SdkConstants.ATTR_PACKAGE);
        String host = packageName == null ? "" : reversePackageName(packageName).toLowerCase(Locale.US);
        String activityName =
            activity.getAttributeValue(SdkConstants.ATTR_NAME, SdkConstants.ANDROID_URI);
        String pathPrefix = activityName == null ? "/" : "/" + generatePathPrefix(activityName);
        String tagContent =
            String.format(IF_CONTENT_FORMAT, prefix != null ? prefix : SdkConstants.ANDROID_NS_NAME, host, pathPrefix);

        XmlTag intentFilter = activity.createChildTag(SdkConstants.TAG_INTENT_FILTER, null, tagContent, false);
        XmlTag tagAdded = activity.addSubTag(intentFilter, false);
        activity.addBefore(createXmlComment(project, IF_COMMENT_TEXT), tagAdded);
        XmlTag dataTag  = tagAdded.findFirstSubTag(SdkConstants.TAG_DATA);
        if (dataTag != null) {
          tagAdded.addBefore(createXmlComment(project, DATA_COMMENT_TEXT), dataTag);
        }
      }
    }
  }

  private static XmlComment createXmlComment(@NotNull Project project, @NotNull String text) {
    // XmlElementFactory does not provide API for creating comment.
    // So we create a tag wrapping the comment, and extract comment from the created tag.
    XmlTag commentElement = XmlElementFactory.getInstance(project)
      .createTagFromText("<foo>" + text + "</foo>", XMLLanguage.INSTANCE);
    return PsiTreeUtil.getChildOfType(commentElement, XmlComment.class);
  }

  private static String reversePackageName(String packageName) {
    List<String> strs = Lists.newArrayList(packageName.split("\\."));
    return Joiner.on('.').join(Lists.reverse(strs));
  }

  private static String generatePathPrefix(String canonicalActivityName) {
    String[] strs = canonicalActivityName.split("\\.");
    if (strs.length > 0) {
      String shortActivityName = strs[strs.length - 1].toLowerCase(Locale.US);
      if (shortActivityName.endsWith(ACTIVITY_STRING)) {
        shortActivityName = shortActivityName.substring(
          0, shortActivityName.length() - ACTIVITY_STRING.length());
      }
      return shortActivityName;
    }
    return "";
  }
}
