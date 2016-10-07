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
package com.android.tools.idea.apk.viewer.diff;

import com.android.tools.idea.apk.viewer.ApkEntry;
import com.android.tools.idea.apk.viewer.ApkFileSystem;
import com.android.tools.idea.apk.viewer.ApkParser;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;
import java.io.IOException;

public class ApkDiffParserTest extends AndroidTestCase {

  public void testTreeCreation_1v2() throws IOException {
    VirtualFile virtualFile1 = myFixture.copyFileToProject("apk/1.apk");
    VirtualFile apkRoot1 = ApkFileSystem.getInstance().getRootByLocal(virtualFile1);
    assertNotNull(apkRoot1);

    VirtualFile virtualFile2 = myFixture.copyFileToProject("apk/2.apk");
    VirtualFile apkRoot2 = ApkFileSystem.getInstance().getRootByLocal(virtualFile2);
    assertNotNull(apkRoot2);

    DefaultMutableTreeNode treeNode = ApkDiffParser.createTreeNode(apkRoot1, apkRoot2);
    assertEquals("1.apk 649 960 311\n" +
                 "  instant-run.zip 0 352 352\n" +
                 "    instant-run 0 2 2\n" +
                 "      classes1.dex 0 2 2\n" +
                 "  res 6 6 0\n" +
                 "    anim 6 6 0\n" +
                 "      fade.xml 6 6 0\n" +
                 "  AndroidManifest.xml 13 13 0\n",
                 dumpTree(treeNode));
  }

  public void testTreeCreation_2v1() throws IOException {
    VirtualFile virtualFile1 = myFixture.copyFileToProject("apk/1.apk");
    VirtualFile apkRoot1 = ApkFileSystem.getInstance().getRootByLocal(virtualFile1);
    assertNotNull(apkRoot1);

    VirtualFile virtualFile2 = myFixture.copyFileToProject("apk/2.apk");
    VirtualFile apkRoot2 = ApkFileSystem.getInstance().getRootByLocal(virtualFile2);
    assertNotNull(apkRoot2);

    DefaultMutableTreeNode treeNode = ApkDiffParser.createTreeNode(apkRoot2, apkRoot1);
    assertEquals("2.apk 960 649 -311\n" +
                 "  res 6 6 0\n" +
                 "    anim 6 6 0\n" +
                 "      fade.xml 6 6 0\n" +
                 "  AndroidManifest.xml 13 13 0\n" +
                 "  instant-run.zip 352 0 -352\n" +
                 "    instant-run 2 0 -2\n" +
                 "      classes1.dex 2 0 -2\n",
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
    ApkDiffEntry entry = (ApkDiffEntry)ApkEntry.fromNode(treeNode);
    assertNotNull(entry);
    sb.append(entry.getName());
    sb.append(' ');
    sb.append(entry.getOldSize());
    sb.append(' ');
    sb.append(entry.getNewSize());
    sb.append(' ');
    sb.append(entry.getSize());
    sb.append('\n');

    for (int i = 0; i < treeNode.getChildCount(); i++) {
      dumpTree(sb, (DefaultMutableTreeNode)treeNode.getChildAt(i), depth + 1);
    }
  }
}