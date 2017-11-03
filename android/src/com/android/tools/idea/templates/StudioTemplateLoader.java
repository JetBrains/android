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

import com.android.utils.SdkUtils;
import com.intellij.openapi.util.io.FileUtil;
import freemarker.cache.TemplateLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Stack;

/**
 * A custom {@link TemplateLoader} which locates templates on disk relative to a specified template folder.
 */
public final class StudioTemplateLoader implements TemplateLoader {
  // Root folder of a set of templates. This is found by finding a parent folder of the original
  // template folder with the name "templates". If no such folder exist use the template folder as root.
  // The prefix "root://" refers to this folder.
  private final File myTemplateRootFolder;
  // The top element holds the folder of the previous loaded template file.
  // Initially this is set to the folder of the main template.
  private final Stack<File> myLastTemplateFolders;
  // Specify the root folder as a prefix
  private final static String ROOT = "root://";
  // The construct: new File(String) strip 1 forward slashes in "root://", and depending on the OS we may end up with root:/ or root:\
  private final static String ROOT_FILE = "root:" + File.separator;

  /**
   * A {@link TemplateLoader} that is loading files for FreeMarker template engine.
   * @param templateFolder the folder that holds the template we are going to load first
   */
  public StudioTemplateLoader(@NotNull File templateFolder) {
    myTemplateRootFolder = findTemplateRootFolder(templateFolder);
    myLastTemplateFolders = new Stack<File>();
    myLastTemplateFolders.push(templateFolder);
  }

  /**
   * Push the folder of the last template loaded as a temporary relative reference.
   * This can be useful if we need to resolve other references that are (or could be) relative
   * to the previous loaded template.
   * @param folder the folder of a template used for resolving relative paths
   */
  public void pushTemplateFolder(@NotNull File folder) {
    myLastTemplateFolders.push(folder);
  }

  /**
   * Resolve the folderName and push the resulting folder as the last template folder.
   * @param folderName the name of a folder (may start with "root://") used for resolving relative paths
   */
  public void pushTemplateFolder(@NotNull String folderName) throws IOException {
    myLastTemplateFolders.push(resolveName(folderName));
  }

  /**
   * Pop the previous folder.
   * Use this value to restore a prior last template folder if we pushed a new folder explicitly.
   */
  public void popTemplateFolder() {
    myLastTemplateFolders.pop();
  }

  /**
   * Return the name reference of a template given a file location.
   * @param file is either an absolute file path, a relative path
   * @return the name used to pass to FreeMarker
   */
  @NotNull
  public String findTemplate(@NotNull File file) throws IOException {
    if (!file.isAbsolute()) {
      file = resolveName(file.getPath());
    }
    String path = FileUtil.getRelativePath(myTemplateRootFolder, file);
    if (path == null) {
      throw new IOException("Absolute paths must start with: " + myTemplateRootFolder.getPath());
    }
    return ROOT + FileUtil.toSystemIndependentName(path);
  }

  /**
   * Return an absolute file reference given a file that may be relative to the last template.
   * @param file is either an absolute file path or a relative path
   * @return an absolute file path
   */
  @NotNull
  public File getSourceFile(@NotNull File file) throws IOException {
    String name = findTemplate(file);
    return resolveName(name);
  }

  /**
   * This method is called directly from Freemarker
   * @param name the name reference from {@link #findTemplate}
   * @return an object representing the template
   */
  @Override
  @Nullable
  public Object findTemplateSource(@NotNull String name) throws IOException {
    File file = resolveName(name);
    TemplateSource templateSource = TemplateSource.open(file);
    pushTemplateFolder(file.getParentFile());
    return templateSource;
  }

  /**
   * Return the last modified time for the current template file.
   * @param source a {@link TemplateSource} instance
   * @return the last time the file was modified
   */
  @Override
  public long getLastModified(Object source) {
    TemplateSource templateSource = (TemplateSource) source;
    return templateSource.getLastModified();
  }

  /**
   * Return a reader for the current template file.
   * @param source a {@link TemplateSource} instance
   * @param encoding the encoding to use for reading the file
   * @return a {@link Reader} instance for reading the file
   */
  @Override
  @NotNull
  public Reader getReader(@NotNull Object source, @NotNull String encoding) throws IOException {
    TemplateSource templateSource = (TemplateSource) source;
    return new InputStreamReader(templateSource.getInputStream(), encoding);
  }

  /**
   * Close the underlying {@link InputStream} for the current template file.
   * @param source a {@link TemplateSource} instance
   */
  @Override
  public void closeTemplateSource(Object source) throws IOException {
    TemplateSource templateSource = (TemplateSource) source;
    popTemplateFolder();
    templateSource.close();
  }

  /**
   * Resolve a Freemarker name reference.
   * @param name Freemarker name reference from {@link #findTemplate}
   * @return an absolute file
   */
  @NotNull
  private File resolveName(@NotNull String name) throws IOException {
    File file;
    if (name.startsWith(ROOT)) {
      file = new File(myTemplateRootFolder, name.substring(ROOT.length()));
    }
    else if (name.startsWith(ROOT_FILE)) {
      file = new File(myTemplateRootFolder, name.substring(ROOT_FILE.length()));
    }
    else if (myLastTemplateFolders != null) {
      file = new File(myLastTemplateFolders.peek(), name);
    }
    else {
      file = new File(myTemplateRootFolder, name);
    }
    return file;
  }

  @NotNull
  private static File findTemplateRootFolder(@NotNull File templateFolder) {
    File folder = templateFolder;
    while (folder != null && !folder.getName().equals("templates")) {
      folder = folder.getParentFile();
    }
    return folder != null ? folder : templateFolder;
  }

  /**
   * Helper class for handling template source files.
   */
  private final static class TemplateSource {
    private final InputStream myInputStream;
    private final long myLastModifiedTime;

    private TemplateSource(@NotNull InputStream inputStream, long lastModified) {
      myInputStream = inputStream;
      myLastModifiedTime = lastModified;
    }

    public static TemplateSource open(@NotNull File file) throws IOException {
      if (!file.exists() && !file.isFile()) {
        return null;
      }
      return new TemplateSource(SdkUtils.fileToUrl(file).openStream(), file.lastModified());
    }

    @NotNull
    public InputStream getInputStream() {
      return myInputStream;
    }

    public long getLastModified() {
      return myLastModifiedTime;
    }

    public void close() throws IOException {
      myInputStream.close();
    }
  }
}
