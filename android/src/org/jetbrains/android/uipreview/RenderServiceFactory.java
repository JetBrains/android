/*
 * Copyright (C) 2011 The Android Open Source Project
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

package org.jetbrains.android.uipreview;

import com.android.SdkConstants;
import com.android.ide.common.rendering.LayoutLibrary;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.ide.common.resources.*;
import com.android.ide.common.sdk.LoadStatus;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.android.io.StreamException;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.project.ProjectProperties;
import com.android.tools.idea.rendering.LayoutLogWrapper;
import com.android.tools.idea.rendering.LogWrapper;
import com.android.utils.ILogger;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.BufferingFileWrapper;
import org.jetbrains.android.util.BufferingFolderWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderServiceFactory {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.uipreview.RenderServiceFactory");

  private final Map<String,Map<String,Integer>> myEnumMap;

  private LayoutLibrary myLibrary;
  private FrameworkResources myResources;

  public LayoutLibrary getLibrary() {
    return myLibrary;
  }

  @Nullable
  public static RenderServiceFactory create(@NotNull IAndroidTarget target,
                                            @NotNull Map<String, Map<String, Integer>> enumMap) throws RenderingException, IOException {
    final RenderServiceFactory factory = new RenderServiceFactory(enumMap);
    if (factory.loadLibrary(target)) {
      return factory;
    }
    return null;
  }

  private RenderServiceFactory(@NotNull Map<String, Map<String, Integer>> enumMap) {
    myEnumMap = enumMap;
  }

  private boolean loadLibrary(@NotNull IAndroidTarget target) throws RenderingException, IOException {
    final String layoutLibJarPath = target.getPath(IAndroidTarget.LAYOUT_LIB);
    final VirtualFile layoutLibJar = LocalFileSystem.getInstance().findFileByPath(layoutLibJarPath);
    if (layoutLibJar == null || layoutLibJar.isDirectory()) {
      throw new RenderingException(AndroidBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(layoutLibJarPath)));
    }

    final String resFolderPath = target.getPath(IAndroidTarget.RESOURCES);
    final VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(resFolderPath);
    if (resFolder == null || !resFolder.isDirectory()) {
      throw new RenderingException(
        AndroidBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(resFolderPath)));
    }

    final String fontFolderPath = target.getPath(IAndroidTarget.FONTS);
    final VirtualFile fontFolder = LocalFileSystem.getInstance().findFileByPath(fontFolderPath);
    if (fontFolder == null || !fontFolder.isDirectory()) {
      throw new RenderingException(
        AndroidBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(fontFolderPath)));
    }

    final String platformFolderPath = target.isPlatform() ? target.getLocation() : target.getParent().getLocation();
    final File platformFolder = new File(platformFolderPath);
    if (!platformFolder.isDirectory()) {
      throw new RenderingException(
        AndroidBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(platformFolderPath)));
    }

    final File buildProp = new File(platformFolder, SdkConstants.FN_BUILD_PROP);
    if (!buildProp.isFile()) {
      throw new RenderingException(
        AndroidBundle.message("android.file.not.exist.error", FileUtil.toSystemDependentName(buildProp.getPath())));
    }

    final ILogger logger = new LogWrapper(LOG);
    final LayoutLog layoutLog = new LayoutLogWrapper(LOG);

    myLibrary = LayoutLibrary.load(layoutLibJar.getPath(), logger, ApplicationNamesInfo.getInstance().getFullProductName());
    if (myLibrary.getStatus() != LoadStatus.LOADED) {
      throw new RenderingException(myLibrary.getLoadMessage());
    }

    myResources = loadPlatformResources(new File(resFolder.getPath()), logger);

    final Map<String, String> buildPropMap = ProjectProperties.parsePropertyFile(new BufferingFileWrapper(buildProp), logger);
    return myLibrary.init(buildPropMap, new File(fontFolder.getPath()), myEnumMap, layoutLog);
  }

  private static FrameworkResources loadPlatformResources(File resFolder, ILogger log) throws IOException, RenderingException {
    final IAbstractFolder resFolderWrapper = new BufferingFolderWrapper(resFolder);
    final FrameworkResources resources = new FrameworkResources(resFolderWrapper);

    loadResources(resources, null, null, resFolderWrapper);

    resources.loadPublicResources(log);
    return resources;
  }

  // ADT
  public FrameworkResources getFrameworkResources() {
    return myResources;
  }

  public static void loadResources(@NotNull ResourceRepository repository,
                                   @Nullable final String layoutXmlFileText,
                                   @Nullable VirtualFile layoutXmlFile,
                                   @NotNull IAbstractFolder... rootFolders) throws IOException, RenderingException {
    final ScanningContext scanningContext = new ScanningContext(repository);

    for (IAbstractFolder rootFolder : rootFolders) {
      for (IAbstractResource file : rootFolder.listMembers()) {
        if (!(file instanceof IAbstractFolder)) {
          continue;
        }

        final IAbstractFolder folder = (IAbstractFolder)file;
        final ResourceFolder resFolder = repository.processFolder(folder);

        if (resFolder != null) {
          for (final IAbstractResource childRes : folder.listMembers()) {

            if (childRes instanceof IAbstractFile) {
              final VirtualFile vFile;

              if (childRes instanceof BufferingFileWrapper) {
                final BufferingFileWrapper fileWrapper = (BufferingFileWrapper)childRes;
                final String filePath = FileUtil.toSystemIndependentName(fileWrapper.getOsLocation());
                vFile = LocalFileSystem.getInstance().findFileByPath(filePath);

                if (vFile != null && Comparing.equal(vFile, layoutXmlFile) && layoutXmlFileText != null) {
                  resFolder.processFile(new MyFileWrapper(layoutXmlFileText, childRes), ResourceDeltaKind.ADDED, scanningContext);
                }
                else {
                  resFolder.processFile((IAbstractFile)childRes, ResourceDeltaKind.ADDED, scanningContext);
                }
              }
              else {
                LOG.error("childRes must be instance of " + BufferingFileWrapper.class.getName());
              }
            }
          }
        }
      }
    }

    final List<String> errors = scanningContext.getErrors();
    if (errors != null && errors.size() > 0) {
      LOG.debug(new RenderingException(merge(errors)));
    }
  }

  private static String merge(@NotNull Collection<String> strs) {
    final StringBuilder result = new StringBuilder();
    for (Iterator<String> it = strs.iterator(); it.hasNext(); ) {
      String str = it.next();
      result.append(str);
      if (it.hasNext()) {
        result.append('\n');
      }
    }
    return result.toString();
  }

  private static class MyFileWrapper implements IAbstractFile {
    private final String myLayoutXmlFileText;
    private final IAbstractResource myChildRes;

    public MyFileWrapper(String layoutXmlFileText, IAbstractResource childRes) {
      myLayoutXmlFileText = layoutXmlFileText;
      myChildRes = childRes;
    }

    @Override
    public InputStream getContents() throws StreamException {
      return new ByteArrayInputStream(myLayoutXmlFileText.getBytes());
    }

    @Override
    public void setContents(InputStream source) throws StreamException {
      throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream getOutputStream() throws StreamException {
      throw new UnsupportedOperationException();
    }

    @Override
    public PreferredWriteMode getPreferredWriteMode() {
      throw new UnsupportedOperationException();
    }

    @Override
    public long getModificationStamp() {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
      return myChildRes.getName();
    }

    @Override
    public String getOsLocation() {
      return myChildRes.getOsLocation();
    }

    @Override
    public boolean exists() {
      return true;
    }

    @Override
    public IAbstractFolder getParentFolder() {
      return myChildRes.getParentFolder();
    }

    @Override
    public boolean delete() {
      throw new UnsupportedOperationException();
    }
  }
}
