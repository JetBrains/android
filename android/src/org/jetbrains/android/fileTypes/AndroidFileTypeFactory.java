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
package org.jetbrains.android.fileTypes;

import com.android.tools.idea.fileTypes.AndroidNinePatchFileType;
import com.android.tools.idea.fileTypes.AndroidRenderscriptFileType;
import com.android.tools.idea.lang.aidl.AidlFileType;
import com.android.tools.idea.rendering.webp.WebpImageReaderSpi;
import com.intellij.openapi.fileTypes.FileTypeConsumer;
import com.intellij.openapi.fileTypes.FileTypeFactory;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class AndroidFileTypeFactory extends FileTypeFactory {

  @Override
  public void createFileTypes(@NotNull FileTypeConsumer consumer) {
    consumer.consume(AidlFileType.INSTANCE, AidlFileType.DEFAULT_ASSOCIATED_EXTENSION);
    consumer.consume(AndroidRenderscriptFileType.INSTANCE, AndroidRenderscriptFileType.fileNameMatchers());
    consumer.consume(AndroidNinePatchFileType.INSTANCE, AndroidNinePatchFileType.EXTENSION);
    WebpImageReaderSpi.ensureWebpRegistered();
    consumer.consume(ImageFileTypeManager.getInstance().getImageFileType(), WebpImageReaderSpi.EXT_WEBP);
  }
}
