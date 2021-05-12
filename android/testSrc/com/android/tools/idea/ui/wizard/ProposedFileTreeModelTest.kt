/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.ui.wizard

import com.android.tools.idea.ui.wizard.ProposedFileTreeModel.Node
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import java.io.File
import javax.swing.Icon

/**
 * [NodeDSL] provides a simple declarative syntax for specifying the expected structure
 * of a [ProposedFileTreeModel]'s backing tree. A tree is recursively built from nested
 * [node] blocks, which assign values for the file, icon, isConflicted, and isConflictedTree
 * attributes of the corresponding [NodeBuilder]s (isConflicted and isConflictedTree are optional
 * and default to false).
 *
 * A [node] block may also contain a [NodeBuilder.children] block, which can contain
 * any number of [Children.node] blocks. For example,
 *
 * node {
 *   file = File("rootDir")
 *   icon = [DIR_ICON]
 *   isConflictedTree = true
 *
 *   children {
 *     node {
 *        file = File(root, "child.txt")
 *        icon = [DEFAULT_ICON]
 *        isConflicted = true
 *        isConflictedTree = true
 *     }
 *   }
 * }
 *
 * builds a tree with root directory "rootDir", and a proposed file "rootDir/child.txt",
 * which is in conflict with an already-existing "rootDir/child.txt" file.
 */
@DslMarker
private annotation class NodeDSL

@NodeDSL
private class NodeBuilder {
  lateinit var file: File
  var icon: Icon = DEFAULT_ICON
  var conflictedFiles = listOf<File>()
  var isConflictedTree = false
  private val children = mutableListOf<Node>()

  fun build() = Node(file, conflictedFiles, icon, children, isConflictedTree)

  fun children(block: Children.() -> Unit) {
    children.addAll(Children().apply(block))
  }
}

@NodeDSL
private class Children : ArrayList<Node>() {
  fun node(block: NodeBuilder.() -> Unit) {
    add(NodeBuilder().apply(block).build())
  }
}

/**
 * Entry point function for declaring expected [ProposedFileTreeModel] structure. See [NodeDSL].
 */
private fun node(block: NodeBuilder.() -> Unit) = NodeBuilder().apply(block).build()

private fun File.createChildFile(name: String) = resolve(name).apply { createNewFile() }

private fun File.createChildDir(name: String) = resolve(name).apply { mkdir() }

@RunWith(JUnit4::class)
class ProposedFileTreeModelTest {
  @JvmField
  @Rule
  val tempFolderRule = TemporaryFolder()

  private lateinit var rootDir: File

  @Before
  fun setUp() {
    rootDir = tempFolderRule.newFolder()
  }

  @Test
  fun hasConflicts_trueIfFileAlreadyExists() {
    val foo = rootDir.createChildDir("foo")
    val treeModel = ProposedFileTreeModel(rootDir, setOf(foo.createChildFile("bar"), foo.resolve("bazz")))

    assertThat(treeModel.hasConflicts()).isTrue()
  }

  @Test
  fun hasConflicts_falseIfNoFilesAlreadyExist() {
    val foo = rootDir.createChildDir("foo")
    val treeModel = ProposedFileTreeModel(rootDir, setOf(foo.resolve("bar"), foo.resolve("bazz")))

    assertThat(treeModel.hasConflicts()).isFalse()
  }

  @Test
  fun conflictWithMoreSpecificConfiguration() {
    val filename = "file"
    val resDir = rootDir.createChildDir("res")
    val drawableDir = resDir.createChildDir("drawable")
    val drawableV24Dir = resDir.createChildDir("drawable-v24")
    val drawableV30Dir = resDir.createChildDir("drawable-v30")
    val drawableV24ExistingFile = drawableV24Dir.createChildFile(filename)
    val rootFile = rootDir.resolve(filename)
    val drawableFile = drawableDir.resolve(filename)
    val drawableV30File = drawableV30Dir.resolve(filename)

    val treeModel = ProposedFileTreeModel(rootDir, setOf(
      rootFile,
      drawableFile,
      drawableV30File
    ))

    val expectedTree = node {
      file = rootDir
      icon = DIR_ICON
      isConflictedTree = true

      children {
        node {
          file = rootFile
        }
        node {
          file = resDir
          icon = DIR_ICON
          isConflictedTree = true

          children {
            node {
              file = drawableDir
              icon = DIR_ICON
              isConflictedTree = true
              children {
                node {
                  file = drawableFile
                  conflictedFiles = listOf(drawableV24ExistingFile)
                  isConflictedTree = true
                }
              }
            }
            node {
              file = drawableV30Dir
              icon = DIR_ICON
              children {
                node {
                  file = drawableV30File
                }
              }
            }
          }
        }
      }
    }

    assertThat(treeModel.root).isEqualTo(expectedTree)
    assertThat(treeModel.getShadowConflictedFiles()).containsExactly(drawableV24ExistingFile)
  }

  @Test
  fun modelConstructedFromSetOfFiles_generatesCorrectTree() {
    val root_existingFile = rootDir.createChildFile("root_existingFile")
    val root_newFile = rootDir.resolve("root_newFile")

    val root_sub1 = rootDir.createChildDir("root_sub1")
    val root_sub1_existingFile = root_sub1.createChildFile("root_sub1_existingFile")
    val root_sub1_newFile = root_sub1.resolve("root_sub1_newFile")

    val root_sub2 = rootDir.resolve("root_sub2")
    val root_sub2_newFile = root_sub2.resolve("root_sub2_newFile")

    val treeModel = ProposedFileTreeModel(rootDir, setOf(
      root_existingFile,
      root_newFile,
      root_sub1_existingFile,
      root_sub1_newFile,
      root_sub2_newFile
    ))

    val expectedTree = node {
      file = rootDir
      icon = DIR_ICON
      isConflictedTree = true

      children {
        node {
          file = root_existingFile
          conflictedFiles = listOf(file)
          isConflictedTree = true
        }
        node {
          file = root_newFile
        }
        node {
          file = root_sub1
          icon = DIR_ICON
          isConflictedTree = true

          children {
            node {
              file = root_sub1_existingFile
              conflictedFiles = listOf(file)
              isConflictedTree = true
            }
            node {
              file = root_sub1_newFile
            }
          }
        }
        node {
          file = root_sub2
          icon = DIR_ICON

          children {
            node {
              file = root_sub2_newFile
            }
          }
        }
      }
    }

    assertThat(treeModel.root).isEqualTo(expectedTree)
    assertThat(treeModel.getShadowConflictedFiles()).isEmpty()
  }

  @Test
  fun modelConstructedFromMapOfFiles_generatesCorrectTree() {
    val root_existingFile = rootDir.createChildFile("root_existingFile")
    val root_newFile = rootDir.resolve("root_newFile")

    val root_sub1 = rootDir.createChildDir("root_sub1")
    val root_sub1_existingFile = root_sub1.createChildFile("root_sub1_existingFile")
    val root_sub1_newFile = root_sub1.resolve("root_sub1_newFile")

    val root_sub2 = rootDir.resolve("root_sub2")
    val root_sub2_newFile = root_sub2.resolve("root_sub2_newFile")

    val proposedFileToIcon = listOf(
      root_existingFile,
      root_newFile,
      root_sub1_existingFile,
      root_sub1_newFile,
      root_sub2_newFile
    ).associateWith { mock(Icon::class.java) }

    val treeModel = ProposedFileTreeModel(rootDir, proposedFileToIcon)

    val expectedTree = node {
      file = rootDir
      icon = DIR_ICON
      isConflictedTree = true

      children {
        node {
          file = root_existingFile
          icon = proposedFileToIcon[root_existingFile]!!
          conflictedFiles = listOf(file)
          isConflictedTree = true
        }
        node {
          file = root_newFile
          icon = proposedFileToIcon[root_newFile]!!
        }
        node {
          file = root_sub1
          icon = DIR_ICON
          isConflictedTree = true

          children {
            node {
              file = root_sub1_existingFile
              icon = proposedFileToIcon[root_sub1_existingFile]!!
              conflictedFiles = listOf(file)
              isConflictedTree = true
            }
            node {
              file = root_sub1_newFile
              icon = proposedFileToIcon[root_sub1_newFile]!!
            }
          }
        }
        node {
          file = root_sub2
          icon = DIR_ICON

          children {
            node {
              file = root_sub2_newFile
              icon = proposedFileToIcon[root_sub2_newFile]!!
            }
          }
        }
      }
    }

    assertThat(treeModel.root).isEqualTo(expectedTree)
    assertThat(treeModel.getShadowConflictedFiles()).isEmpty()
  }

  @Test
  fun modelConstructedFromMapOfFiles_redundantDirectoriesMarkedWithCustomIcons() {
    val root_sub1 = rootDir.createChildDir("root_sub1")
    val root_sub1_newFile = root_sub1.resolve("root_sub1_newFile")

    val root_sub2 = rootDir.createChildDir("root_sub2")
    val root_sub2_newFile = root_sub2.resolve("root_sub2_newFile")

    val proposedFileToIcon = listOf(
      root_sub1_newFile,
      root_sub1,
      root_sub2,
      root_sub2_newFile
    ).associateWith { mock(Icon::class.java) }

    val treeModel = ProposedFileTreeModel(rootDir, proposedFileToIcon)

    val expectedTree = node {
      file = rootDir
      icon = DIR_ICON

      children {
        node {
          file = root_sub1
          icon = proposedFileToIcon[root_sub1]!!

          children {
            node {
              file = root_sub1_newFile
              icon = proposedFileToIcon[root_sub1_newFile]!!
            }
          }
        }
        node {
          file = root_sub2
          icon = proposedFileToIcon[root_sub2]!!

          children {
            node {
              file = root_sub2_newFile
              icon = proposedFileToIcon[root_sub2_newFile]!!
            }
          }
        }
      }
    }

    assertThat(treeModel.root).isEqualTo(expectedTree)
    assertThat(treeModel.getShadowConflictedFiles()).isEmpty()
  }

  @Test
  fun modelConstructedFromSetOfFiles_redundantDirectoriesMarkedWithDirIcon() {
    val root_sub1 = rootDir.createChildDir("root_sub1")
    val root_sub1_newFile = root_sub1.resolve("root_sub1_newFile")

    val root_sub2 = rootDir.createChildDir("root_sub2")
    val root_sub2_newFile = root_sub2.resolve("root_sub2_newFile")

    val treeModel = ProposedFileTreeModel(rootDir, setOf(
      root_sub1_newFile,
      root_sub1,
      root_sub2,
      root_sub2_newFile
    ))

    val expectedTree = node {
      file = rootDir
      icon = DIR_ICON

      children {
        node {
          file = root_sub1
          icon = DIR_ICON

          children {
            node {
              file = root_sub1_newFile
            }
          }
        }
        node {
          file = root_sub2
          icon = DIR_ICON

          children {
            node {
              file = root_sub2_newFile
            }
          }
        }
      }
    }

    assertThat(treeModel.root).isEqualTo(expectedTree)
    assertThat(treeModel.getShadowConflictedFiles()).isEmpty()
  }

  @Test
  fun modelConstructedFromMapOfFiles_proposedExistingDirectoryMarkedWithCustomIcon() {
    val root_sub = rootDir.createChildDir("root_sub")
    val root_sub_icon = mock(Icon::class.java)
    val treeModel = ProposedFileTreeModel(rootDir, mapOf(root_sub to root_sub_icon))

    val expectedTree = node {
      file = rootDir
      icon = DIR_ICON

      children {
        node {
          file = root_sub
          icon = root_sub_icon
        }
      }
    }

    assertThat(treeModel.root).isEqualTo(expectedTree)
    assertThat(treeModel.getShadowConflictedFiles()).isEmpty()
  }

  @Test
  fun modelConstructedFromSetOfFiles_proposedExistingDirectoryMarkedWithDirIcon() {
    val root_sub = rootDir.createChildDir("root_sub")
    val treeModel = ProposedFileTreeModel(rootDir, setOf(root_sub))

    val expectedTree = node {
      file = rootDir
      icon = DIR_ICON

      children {
        node {
          file = root_sub
          icon = DIR_ICON
        }
      }
    }

    assertThat(treeModel.root).isEqualTo(expectedTree)
    assertThat(treeModel.getShadowConflictedFiles()).isEmpty()
  }
}