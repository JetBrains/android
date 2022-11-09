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
package com.android.tools.idea.run.editor;

import static com.android.tools.idea.instantapp.InstantApps.findFeatureModules;

import com.android.SdkConstants;
import com.android.tools.idea.instantapp.InstantAppUrlFinder;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.ui.DoubleClickListener;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DeepLinkChooserDialog extends DialogWrapper {

  private static final String TAG_INTENT_FILTER = "intent-filter";
  private static final String TAG_ACTION = "action";
  private static final String TAG_CATEGORY = "category";
  private static final String TAG_DATA = "data";

  private static final String ACTION_VIEW = "android.intent.action.VIEW";
  private static final String CATEGORY_DEFAULT = "android.intent.category.DEFAULT";
  private static final String CATEGORY_BROWSABLE = "android.intent.category.BROWSABLE";

  Project myProject;
  String mySelectedDeepLink = null;
  JBList myList;

  public DeepLinkChooserDialog(@NotNull Project project, @Nullable Module module) {
    super(project);
    myProject = project;
    List<String> deepLinks = new ArrayList<>();
    if (module != null && module.getModuleFile() != null && module.getModuleFile().getParent() != null) {
      XmlFile manifest = getAndroidManifestPsi(module);
      if (manifest != null) {
        deepLinks.addAll(getAllDeepLinks(manifest.getRootTag()));
      }

      AndroidFacet facet = AndroidFacet.getInstance(module);
      if (facet != null) {
        List<Module> featureModules = findFeatureModules(facet);
        for (Module featureModule : featureModules) {
          deepLinks.addAll(new InstantAppUrlFinder(featureModule).getAllUrls());
        }
      }
    }
    myList = new JBList((Object[])ArrayUtil.toStringArray(deepLinks));
    myList.setEmptyText("None found in AndroidManifest.xml");
    init();
  }

  @Nullable
  public String getSelectedDeepLink() {
    return mySelectedDeepLink;
  }


  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JScrollPane scrollPane = new JBScrollPane(myList);
    myList.setSelectedIndex(0);
    new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent e) {
        int index = myList.locationToIndex(e.getPoint());
        if (index != -1 && myList.getModel().getElementAt(index) != null) {
          myList.setSelectedIndex(index);
          doOKAction();
        }
        return false;
      }
    }.installOn(myList);
    return scrollPane;
  }

  @Override
  protected void doOKAction() {
    mySelectedDeepLink = (String)myList.getSelectedValue();
    super.doOKAction();
  }

  /**
   * Returns the file of AndroidManifest.xml with type of XmlFile.
   */
  @Nullable
  private static XmlFile getAndroidManifestPsi(@NotNull Module module) {
    AndroidFacet facet = AndroidFacet.getInstance(module);
    if (facet != null) {
      VirtualFile manifest = SourceProviderManager.getInstance(facet).getMainManifestFile();
      if (manifest != null) {
        PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(manifest);
        if (psiFile instanceof XmlFile) {
          return (XmlFile)psiFile;
        }
      }
    }
    return null;
  }

  /**
   * Returns all match deep links from a root xml tag.
   *
   * @param root The root xml tag, usually is the root tag of AndroidManifest.xml
   */
  @VisibleForTesting
  static List<String> getAllDeepLinks(XmlTag root) {
    if (root == null) {
      return new ArrayList<>();
    }
    List<XmlTag> intentFilters = searchXmlTagsByName(root, TAG_INTENT_FILTER);
    List<String> deepLinks = new ArrayList<>();
    for (XmlTag intentFilter : intentFilters) {
      String deepLink = getDeepLinkFromIntentFilter(intentFilter);
      if (deepLink != null) {
        deepLinks.add(deepLink);
      }
    }
    return deepLinks;
  }

  @NotNull
  private static List<XmlTag> searchXmlTagsByName(@NotNull XmlTag root, @NotNull final String tagName) {
    final List<XmlTag> tags = new ArrayList<>();
    root.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        if (tag.getName().equalsIgnoreCase(tagName)) {
          tags.add(tag);
        }
      }
    });
    return tags;
  }

  /**
   * Check the contents of an intent filter to see if it's for deep link and return the deep link.
   * See also: https://developer.android.com/training/app-indexing/deep-linking.html
   */
  @Nullable
  private static String getDeepLinkFromIntentFilter(@NotNull XmlTag intentFilter) {
    // Check action.
    List<XmlTag> actions = searchXmlTagsByName(intentFilter, TAG_ACTION);
    boolean hasActionView = false;
    for (XmlTag action : actions) {
      String name = action.getAttributeValue(SdkConstants.ATTR_NAME, SdkConstants.ANDROID_URI);
      if (name != null && name.equals(ACTION_VIEW)) {
        hasActionView = true;
        break;
      }
    }
    if (!hasActionView) {
      return null;
    }

    // Check category
    List<XmlTag> categories = searchXmlTagsByName(intentFilter, TAG_CATEGORY);
    boolean hasDefaultCategory = false;
    boolean hasBrowsableCategory = false;
    for (XmlTag category : categories) {
      String name = category.getAttributeValue(SdkConstants.ATTR_NAME, SdkConstants.ANDROID_URI);
      if (name != null && name.equals(CATEGORY_DEFAULT)) {
        hasDefaultCategory = true;
      }
      else if (name != null && name.equals(CATEGORY_BROWSABLE)) {
        hasBrowsableCategory = true;
      }
    }
    if (!hasDefaultCategory || !hasBrowsableCategory) {
      return null;
    }

    // Parse deep link
    List<XmlTag> datas = searchXmlTagsByName(intentFilter, TAG_DATA);
    String scheme = null, host = null, pathPrefix = null, path = null;
    for (XmlTag data : datas) {
      if (null != data.getAttributeValue(SdkConstants.ATTR_SCHEME, SdkConstants.ANDROID_URI)) {
        scheme = data.getAttributeValue(SdkConstants.ATTR_SCHEME, SdkConstants.ANDROID_URI);
      }
      if (null != data.getAttributeValue(SdkConstants.ATTR_HOST, SdkConstants.ANDROID_URI)) {
        host = data.getAttributeValue(SdkConstants.ATTR_HOST, SdkConstants.ANDROID_URI);
      }
      if (null != data.getAttributeValue(SdkConstants.ATTR_PATH_PREFIX, SdkConstants.ANDROID_URI)) {
        pathPrefix = data.getAttributeValue(SdkConstants.ATTR_PATH_PREFIX, SdkConstants.ANDROID_URI);
      }
      if (null != data.getAttributeValue(SdkConstants.ATTR_PATH, SdkConstants.ANDROID_URI)) {
        path = data.getAttributeValue(SdkConstants.ATTR_PATH, SdkConstants.ANDROID_URI);
      }
    }

    if (scheme != null) {
      StringBuilder builder = new StringBuilder(scheme);
      builder.append("://");
      if (host != null) {
        builder.append(host);
        if (path != null) {
          builder.append(path);
        }
        else if (pathPrefix != null) {
          builder.append(pathPrefix);
        }
      }
      return builder.toString();
    }
    return null;
  }
}
