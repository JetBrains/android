/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.android;

import com.android.SdkConstants;
import com.android.tools.idea.lang.aidl.AidlFileType;
import com.intellij.ide.fileTemplates.*;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiManager;
import icons.AndroidArtworkIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Properties;

/**
 * @author yole
 */
public class AndroidFileTemplateProvider implements FileTemplateGroupDescriptorFactory {
  @NonNls public static final String REMOTE_INTERFACE_TEMPLATE = "Remote_Interface.aidl";
  @NonNls public static final String ANDROID_MANIFEST_TEMPLATE = SdkConstants.FN_ANDROID_MANIFEST_XML;
  @NonNls public static final String VALUE_RESOURCE_FILE_TEMPLATE = "valueResourceFile.xml";
  @NonNls public static final String RESOURCE_FILE_TEMPLATE = "resourceFile.xml";
  @NonNls public static final String LAYOUT_RESOURCE_FILE_TEMPLATE = "layoutResourceFile.xml";
  @NonNls public static final String LAYOUT_RESOURCE_VERTICAL_FILE_TEMPLATE = "layoutResourceFile_vertical.xml";
  @NonNls public static final String ACTIVITY = "Activity.java";
  @NonNls public static final String FRAGMENT = "Fragment.java";
  @NonNls public static final String APPLICATION = "Application.java";
  @NonNls public static final String SERVICE = "Service.java";
  @NonNls public static final String BROADCAST_RECEIVER = "Broadcast_Receiver.java";
  @NonNls public static final String DEFAULT_PROPERTIES_TEMPLATE = "default.properties";

  @Override
  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateGroupDescriptor group = new FileTemplateGroupDescriptor("Android", AndroidArtworkIcons.Icons.Android);
    group.addTemplate(new FileTemplateDescriptor(ANDROID_MANIFEST_TEMPLATE, StdFileTypes.XML.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(VALUE_RESOURCE_FILE_TEMPLATE, StdFileTypes.XML.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(RESOURCE_FILE_TEMPLATE, StdFileTypes.XML.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(LAYOUT_RESOURCE_FILE_TEMPLATE, StdFileTypes.XML.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(LAYOUT_RESOURCE_VERTICAL_FILE_TEMPLATE, StdFileTypes.XML.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(ACTIVITY, StdFileTypes.JAVA.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(FRAGMENT, StdFileTypes.JAVA.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(APPLICATION, StdFileTypes.JAVA.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(SERVICE, StdFileTypes.JAVA.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(BROADCAST_RECEIVER, StdFileTypes.JAVA.getIcon()));
    group.addTemplate(new FileTemplateDescriptor(REMOTE_INTERFACE_TEMPLATE, AidlFileType.INSTANCE.getIcon()));
    return group;
  }

  // must be invoked in a write action
  @Nullable
  public static PsiElement createFromTemplate(@NotNull Project project,
                                              @NotNull VirtualFile rootDir,
                                              @NotNull String templateName,
                                              @NotNull String fileName,
                                              @NotNull Properties properties) throws Exception {
    rootDir.refresh(false, false);
    PsiDirectory directory = PsiManager.getInstance(project).findDirectory(rootDir);
    if (directory != null) {
      return createFromTemplate(templateName, fileName, directory, properties);
    }
    return null;
  }


  @Nullable
  public static PsiElement createFromTemplate(@NotNull Project project,
                                        @NotNull VirtualFile rootDir,
                                        @NotNull String templateName,
                                        @NotNull String fileName) throws Exception {
    return createFromTemplate(project, rootDir, templateName, fileName, FileTemplateManager.getInstance(project).getDefaultProperties());
  }

  public static PsiElement createFromTemplate(String templateName, String fileName, @NotNull PsiDirectory directory, Properties properties)
    throws Exception {
    FileTemplateManager manager = FileTemplateManager.getInstance(directory.getProject());
    FileTemplate template = manager.getJ2eeTemplate(templateName);
    return FileTemplateUtil.createFromTemplate(template, fileName, properties, directory);
  }

  public static PsiElement createFromTemplate(String templateName, String fileName, @NotNull PsiDirectory directory) throws Exception {
    return createFromTemplate(templateName, fileName, directory, FileTemplateManager.getInstance(directory.getProject()).getDefaultProperties());
  }

  @NotNull
  public static String getFileNameByNewElementName(@NotNull String name) {
    if (!FileUtilRt.extensionEquals(name, "xml")) {
      name += ".xml";
    }
    return name;
  }
}
