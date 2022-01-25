/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.handlers.motion.editor.utils;

import org.w3c.dom.Node;

import javax.imageio.*;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 *  helper class for saving a series of images as a GIF file
 */
public class GifWriter {
  private File mFile;
  private int mTimeBetweenFramesMS;
  private boolean mLoopContinuously;
  private String comment;
  private ImageWriter mGifWriter;
  private IIOMetadata mImageMetaData;
  private ImageWriteParam mImageWriteParam;
  private ImageOutputStream mOutputStream;

  public GifWriter(File file, int timeBetweenFramesMS, boolean loopContinuously, String comment) {
    this.mFile = file;
    this.mTimeBetweenFramesMS = timeBetweenFramesMS;
    this.mLoopContinuously = loopContinuously;
    this.comment = comment;
  }

  /**
   * save the input image into disk.
   * @param img the image to be written to disk
   * @throws IOException
   */
  public void addImage(BufferedImage img) throws IOException {
    if (mGifWriter == null) {
      setup(img.getType());
    }
    mGifWriter.writeToSequence(new IIOImage(img, null, mImageMetaData), mImageWriteParam);
  }

  /**
   * prepare and set up an imageWriter to save a GIF file
   * @param bufferedImageType type of bufferedIage to be written
   * @throws IOException
   */
  private void setup(int bufferedImageType) throws IOException {
    mOutputStream = new FileImageOutputStream(mFile);

    Iterator<ImageWriter> iter = ImageIO.getImageWritersBySuffix("gif");
    if (!iter.hasNext()) {
      throw new IOException("NO GIF writer");
    }
    mGifWriter = iter.next();
    mImageWriteParam = mGifWriter.getDefaultWriteParam();
    ImageTypeSpecifier imageTypeSpecifier =
      ImageTypeSpecifier.createFromBufferedImageType(bufferedImageType);

    mImageMetaData = mGifWriter.getDefaultImageMetadata(imageTypeSpecifier,
                                                        mImageWriteParam);

    String metaFormatName = mImageMetaData.getNativeMetadataFormatName();

    IIOMetadataNode root = (IIOMetadataNode) mImageMetaData.getAsTree(metaFormatName);

    String CONTROL_EXTENSION = "GraphicControlExtension";
    String COMMENT_EXTENSION = "CommentExtensions";
    String APP_EXTENSION = "ApplicationExtensions";

    IIOMetadataNode meta = null, commentsNode = null, appEntensionsNode = null;
    for (int i = 0; i < root.getLength(); i++) {
      Node item = root.item(i);
      String nodeName = root.item(i).getNodeName();
      if (nodeName.equalsIgnoreCase(CONTROL_EXTENSION)) {
        meta = (IIOMetadataNode) item;
      } else if (nodeName.equalsIgnoreCase(COMMENT_EXTENSION)) {
        commentsNode = (IIOMetadataNode) item;
      } else if (nodeName.equalsIgnoreCase(APP_EXTENSION)) {
        appEntensionsNode = (IIOMetadataNode) item;
      }
    }

    if (meta == null) {
      meta = new IIOMetadataNode(CONTROL_EXTENSION);
      root.appendChild(meta);
    }

    meta.setAttribute("disposalMethod", "none");
    meta.setAttribute("userInputFlag", "FALSE");
    meta.setAttribute("transparentColorFlag", "FALSE");
    meta.setAttribute("delayTime", Integer.toString(mTimeBetweenFramesMS / 10));
    meta.setAttribute("transparentColorIndex", "0");

    if (commentsNode == null) {
      commentsNode = new IIOMetadataNode(COMMENT_EXTENSION);
      root.appendChild(commentsNode);
    }
    IIOMetadataNode c1 = new IIOMetadataNode("CommentExtension");
    c1.setAttribute("value", comment);
    commentsNode.appendChild(c1);

    if (appEntensionsNode == null) {
      appEntensionsNode = new IIOMetadataNode(APP_EXTENSION);
      root.appendChild(appEntensionsNode);
    }

    IIOMetadataNode appNode = new IIOMetadataNode("ApplicationExtension");
    appNode.setAttribute("applicationID", "NETSCAPE");
    appNode.setAttribute("authenticationCode", "2.0");
    int loop = mLoopContinuously ? 0 : 1;
    appNode.setUserObject(new byte[]{0x1, (byte) loop, (byte) 0});
    appEntensionsNode.appendChild(appNode);
    mImageMetaData.setFromTree(metaFormatName, root);
    mGifWriter.setOutput(mOutputStream);
    mGifWriter.prepareWriteSequence(null);
  }

  /**
   * ensure the image saving process is ended properly
   * @throws IOException
   */
  public void close() throws IOException {
    mGifWriter.endWriteSequence();
    mOutputStream.close();
    mGifWriter = null;
  }
}