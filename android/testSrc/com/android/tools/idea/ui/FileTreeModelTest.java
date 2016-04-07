/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.android.tools.idea.ui.FileTreeModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.android.AndroidTestCase;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.util.*;

import static com.android.tools.idea.ui.FileTreeModel.Node;

/**
 *
 */
public class FileTreeModelTest extends AndroidTestCase {

  private static File createFile(String systemIndependentPath) {
    if (File.separatorChar == '\\' && systemIndependentPath.startsWith("/") && !systemIndependentPath.startsWith("//")) {
      systemIndependentPath = "/" + systemIndependentPath;
    }
    return new File(FileUtil.toSystemDependentName(systemIndependentPath));
  }

  public void testIcon() throws Exception {
    File file = createFile("/asdfasdfasdf");
    Icon icon = new ImageIcon();
    FileTreeModel model = new FileTreeModel(file);

    model.addFile(createFile("asdfasdfasdf/foo"), icon);

    Node testNode = ((Node)model.getRoot()).getChild("foo");
    assertNotNull(testNode);
    assertEquals(icon, testNode.icon);
  }

  public void testMakeNotExists() throws Exception {
    File file = createFile("/asdfasdfasdf");
    assertFalse(file.exists());

    FileTreeModel model = new FileTreeModel(file);

    Node root = (Node)model.getRoot();

    assertTrue(root.children.isEmpty());
    assertEquals("asdfasdfasdf", root.name);
    assertNull(root.icon);
    assertFalse(root.existsOnDisk);
  }

  public void testPathExistsNoOp() throws Exception {
    FileTreeModel model = new FileTreeModel(createFile("/asdf"));

    model.addFile(createFile("/asdf/foo/bar"));
    String baseString = model.toString();

    model.addFile(createFile("/asdf"));
    assertEquals(baseString, model.toString());

    model.addFile(createFile("/asdf/foo"));
    assertEquals(baseString, model.toString());

    model.addFile(createFile("foo"));
    assertEquals(baseString, model.toString());

    model.addFile(createFile("foo/bar"));
    assertEquals(baseString, model.toString());
  }

  public void testToString() throws Exception {
    File root = createFile("/asdf");
    FileTreeModel model = new FileTreeModel(root);
    assertEquals("(asdf)", model.toString());

    File file1 = createFile("/asdf/foo/bar");
    model.addFile(file1);
    assertEquals("(asdf (foo (bar)))", model.toString());

    File file2 = createFile("/asdf/foo2/blah");
    model.addFile(file2);
    assertEquals("(asdf (foo (bar))(foo2 (blah)))", model.toString());

    File file3 = createFile("/asdf/foo/bar2");
    model.addFile(file3);
    assertEquals("(asdf (foo (bar)(bar2))(foo2 (blah)))", model.toString());
  }


  public void testFileTreeConstruction() throws Exception {
    File rootFile = new File(getTestDataPath(), "projects");
    FileTreeModel model = new FileTreeModel(rootFile);
    Node rootNode = (Node)model.getRoot();
    assertTrue(nodeRepresentsFileTree(rootNode, rootFile));
  }

  /**
   * Uses DFS to compare a tree representation with a file structure
   * @return true iff both trees are identical in structure and contents
   */
  protected boolean nodeRepresentsFileTree(Node nodeRoot, File fileRoot) {
    Stack<Node> nodeStack = new Stack<>(nodeRoot);
    Stack<File> fileStack = new Stack<>(fileRoot);

    while(!fileStack.isEmpty()) {
      Node node = nodeStack.pop();
      File file = fileStack.pop();

      assertNotNull(node);
      assertEquals(file.getName(), node.name);

      List<Node> sortedNodeChildren = new ArrayList<>(node.children);
      Collections.sort(sortedNodeChildren, new Comparator<Node>() {
        @Override
        public int compare(Node o1, Node o2) {
          return o1.name.compareTo(o2.name);
        }
      });


      // Add childrent in sorted order to the stack
      File[] fileChildren = file.listFiles(new FileFilter() {
        @Override
        public boolean accept(File pathname) {
          return !pathname.isHidden();
        }
      });
      List<File> sortedFileChildren = fileChildren == null ? new ArrayList<>(0) : Arrays.asList(fileChildren);

      Collections.sort(sortedFileChildren, new Comparator<File>() {
        @Override
        public int compare(File o1, File o2) {
          return o1.getName().compareTo(o2.getName());
        }
      });

      for (Node n : sortedNodeChildren) {
        nodeStack.push(n);
      }
      for (File f : sortedFileChildren) {
        fileStack.push(f);
      }
    }

    // If there were nodes stack that don't exist in the file tree, return false
    return nodeStack.isEmpty();
  }
}
