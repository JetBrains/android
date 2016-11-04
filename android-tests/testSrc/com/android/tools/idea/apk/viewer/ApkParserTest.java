/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.File;
import java.io.IOException;

public class ApkParserTest extends AndroidTestCase {
  public void testTreeCreation() throws IOException {
    VirtualFile virtualFile = myFixture.copyFileToProject("apk/1.apk");
    VirtualFile apkRoot = ApkFileSystem.getInstance().getRootByLocal(virtualFile);
    assertNotNull(apkRoot);

    DefaultMutableTreeNode treeNode = ApkParser.createTreeNode(apkRoot);
    assertEquals("1.apk 649\n" +
                 "  AndroidManifest.xml 13\n" +
                 "  res 6\n" +
                 "    anim 6\n" +
                 "      fade.xml 6\n", dumpTree(treeNode));
  }

  public void testRezipHasSameContents() throws IOException {
    VirtualFile virtualFile = myFixture.copyFileToProject("apk/1.apk");

    File compressedFile = ApkParser.getZipCompressedApk(VfsUtilCore.virtualToIoFile(virtualFile));
    VirtualFile compressedVirtualFile = VfsUtil.findFileByIoFile(compressedFile, true);
    assertNotNull(compressedVirtualFile);

    VirtualFile uncompressedRoot = ApkFileSystem.getInstance().getRootByLocal(virtualFile);
    VirtualFile compressedRoot = ApkFileSystem.getInstance().getRootByLocal(compressedVirtualFile);

    String compressedTree = dumpTree(ApkParser.createTreeNode(compressedRoot));
    String uncompressedTree = dumpTree(ApkParser.createTreeNode(uncompressedRoot));

    // skip the first line in both versions since they will be different due to file names being different
    compressedTree = compressedTree.substring(compressedTree.indexOf('\n'));
    uncompressedTree = uncompressedTree.substring(uncompressedTree.indexOf('\n'));
    assertEquals(compressedTree, uncompressedTree);
  }

  public void testApkWithZip() throws IOException {
    VirtualFile virtualFile = myFixture.copyFileToProject("apk/2.apk");
    VirtualFile apkRoot = ApkFileSystem.getInstance().getRootByLocal(virtualFile);
    assertNotNull(apkRoot);

    DefaultMutableTreeNode treeNode = ApkParser.createTreeNode(apkRoot);
    assertEquals("2.apk 960\n" +
                 "  instant-run.zip 352\n" +
                 "    instant-run 2\n" +
                 "      classes1.dex 2\n" +
                 "  AndroidManifest.xml 13\n" +
                 "  res 6\n" +
                 "    anim 6\n" +
                 "      fade.xml 6\n",
                 dumpTree(treeNode));
  }

  private static String dumpTree(@NotNull DefaultMutableTreeNode treeNode) {
    StringBuilder sb = new StringBuilder(30);
    dumpTree(sb, treeNode, 0);
    return sb.toString();
  }

  private static void dumpTree(@NotNull StringBuilder sb, @NotNull DefaultMutableTreeNode treeNode, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("  ");
    }
    ApkEntry entry = ApkEntry.fromNode(treeNode);
    assertNotNull(entry);
    sb.append(entry.getName());
    sb.append(' ');
    sb.append(entry.getSize());
    sb.append('\n');

    for (int i = 0; i < treeNode.getChildCount(); i++) {
      dumpTree(sb, (DefaultMutableTreeNode)treeNode.getChildAt(i), depth + 1);
    }
  }
}