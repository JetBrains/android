/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.sdk;

import com.android.ide.common.resources.*;
import com.android.io.IAbstractFile;
import com.android.io.IAbstractFolder;
import com.android.io.IAbstractResource;
import com.android.io.StreamException;
import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.rendering.LogWrapper;
import com.android.utils.ILogger;
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

/** Loader which loads in a {@link com.android.ide.common.resources.FrameworkResources} */
public class FrameworkResourceLoader {
  private static final Logger LOG = Logger.getInstance(FrameworkResourceLoader.class);

  private FrameworkResourceLoader() {
  }

  @Nullable
  public static FrameworkResources load(@NotNull IAndroidTarget myTarget) throws IOException {
    final ILogger logger = new LogWrapper(LOG);
    final String resFolderPath = myTarget.getPath(IAndroidTarget.RESOURCES);
    final VirtualFile resFolder = LocalFileSystem.getInstance().findFileByPath(resFolderPath);
    if (resFolder == null || !resFolder.isDirectory()) {
      LOG.error(AndroidBundle.message("android.directory.cannot.be.found.error", FileUtil.toSystemDependentName(resFolderPath)));
      return null;
    }

    return loadPlatformResources(new File(resFolder.getPath()), logger);
  }

  private static FrameworkResources loadPlatformResources(File resFolder, ILogger log) throws IOException {
    final IAbstractFolder resFolderWrapper = new BufferingFolderWrapper(resFolder);
    final FrameworkResources resources = new FrameworkResources(resFolderWrapper);

    loadResources(resources, null, null, resFolderWrapper);

    resources.loadPublicResources(log);
    return resources;
  }

  private static void loadResources(@NotNull ResourceRepository repository,
                                    @Nullable final String layoutXmlFileText,
                                    @Nullable VirtualFile layoutXmlFile,
                                    @NotNull IAbstractFolder... rootFolders) throws IOException {
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
      LOG.debug(new IOException(merge(errors)));
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
