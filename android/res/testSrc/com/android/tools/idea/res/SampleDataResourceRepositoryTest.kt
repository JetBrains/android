/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.res

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import com.android.ide.common.rendering.api.ResourceValueImpl
import com.android.ide.common.rendering.api.SampleDataResourceValue
import com.android.ide.common.resources.ResourceItem
import com.android.ide.common.resources.ResourceRepository
import com.android.ide.common.util.PathString
import com.android.resources.ResourceType
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.AndroidProjectRule.Companion.withAndroidModels
import com.android.tools.idea.testing.waitForUpdates
import com.android.tools.idea.util.toVirtualFile
import com.google.common.base.Charsets
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase
import java.io.File
import java.io.IOException
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Test for [SampleDataResourceRepository] and [SampleDataListener]. */
@RunWith(JUnit4::class)
@RunsInEdt
class SampleDataResourceRepositoryTest {

  @get:Rule
  val projectRule: AndroidProjectRule =
    withAndroidModels(
      AndroidModuleModelBuilder(
        ":",
        "debug",
        AndroidProjectBuilder().withAndroidModuleDependencyList {
          listOf(AndroidModuleDependency(":lib", "debug"))
        },
      ),
      AndroidModuleModelBuilder(
        ":lib",
        "debug",
        AndroidProjectBuilder()
          .withProjectType { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY }
          .withAndroidModuleDependencyList {
            listOf(AndroidModuleDependency(":transitive", "debug"))
          },
      ),
      AndroidModuleModelBuilder(
        ":transitive",
        "debug",
        AndroidProjectBuilder().withProjectType { IdeAndroidProjectType.PROJECT_TYPE_LIBRARY },
      ),
    )

  @get:Rule val edtRule: EdtRule = EdtRule()

  private val appModuleSystem by lazy { requireNotNull(projectRule.module.getModuleSystem()) }
  private val facet by lazy { requireNotNull(AndroidFacet.getInstance(projectRule.module)) }

  @After
  fun tearDown() {
    SampleDataResourceItem.invalidateCache()
  }

  private fun addLayoutFile(): PsiFile =
    projectRule.fixture.addFileToProject(
      "src/main/res/layout/layout.xml",
      // language=xml
      """
      <?xml version="1.0" encoding="utf-8"?>
      <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
        """
        .trimIndent(),
    )

  @Test
  fun testDataLoad() {
    projectRule.fixture.addFileToProject(
      "sampledata/strings",
      """
      string1
      string2
      string3
      
      """
        .trimIndent(),
    )
    projectRule.fixture.addFileToProject("sampledata/images/image1.png", "Insert image here\n")
    projectRule.fixture.addFileToProject("sampledata/images/image2.jpg", "Insert image here 2\n")
    projectRule.fixture.addFileToProject("sampledata/images/image3.png", "Insert image here 3\n")
    projectRule.fixture.addFileToProject("sampledata/root_image.png", "Insert image here 3\n")
    val repo = SampleDataResourceRepository(facet, projectRule.testRootDisposable)
    waitForUpdates(repo)

    assertEquals(3, getResources(repo).size)
    assertEquals(1, getResources(repo, "strings").size)
    assertEquals(1, getResources(repo, "images").size)
    assertEquals(1, getResources(repo, "root_image.png").size)
  }

  @Test
  fun testResolver() {
    projectRule.fixture.addFileToProject(
      "sampledata/strings",
      """
      string1
      string2
      string3
      
      """
        .trimIndent(),
    )
    projectRule.fixture.addFileToProject(
      "sampledata/ints",
      """
      1
      2
      
      """
        .trimIndent(),
    )
    projectRule.fixture.addFileToProject(
      "sampledata/refs",
      """
      @string/test1
      @string/invalid
      
       """
        .trimIndent(),
    )
    projectRule.fixture.addFileToProject(
      "sampledata/users.json",
      // language=JSON
      """
      {
        "users": [
          {
            "name": "Name1",
            "surname": "Surname1"
          },
          {
            "name": "Name2",
            "surname": "Surname2"
          },
          {
            "name": "Name3",
            "surname": "Surname3",
            "phone": "555-00000"
          }
        ]
      }
      """
        .trimIndent(),
    )
    val image1 =
      projectRule.fixture.addFileToProject("sampledata/images/image1.png", "Insert image here\n")
    val image2 =
      projectRule.fixture.addFileToProject("sampledata/images/image2.jpg", "Insert image here 2\n")
    projectRule.fixture.addFileToProject(
      "src/main/res/values/strings.xml",
      // language=xml
      """
      <?xml version="1.0" encoding="utf-8"?>
      <resources>
        <string name="test1">Hello 1</string>
        <string name="test2">Hello 2</string>
      </resources>"""
        .trimIndent(),
    )

    val layout = addLayoutFile()
    val configuration =
      ConfigurationManager.getOrCreateInstance(projectRule.module)
        .getConfiguration(layout.virtualFile)
    waitForUpdates(StudioResourceRepositoryManager.getInstance(facet).sampleDataResources)
    val resolver = configuration.resourceResolver

    assertEquals("string1", resolver.findResValue("@sample/strings", false)!!.value)
    assertEquals("1", resolver.findResValue("@sample/ints", false)!!.value)
    assertEquals("string2", resolver.findResValue("@sample/strings", false)!!.value)
    assertEquals("string3", resolver.findResValue("@sample/strings", false)!!.value)
    assertEquals("2", resolver.findResValue("@sample/ints", false)!!.value)

    // Test passing json references
    assertEquals("Name1", resolver.findResValue("@sample/users.json/users/name", false)!!.value)

    // The order of the returned paths might depend on the file system
    val imagePaths =
      setOf(
        resolver.findResValue("@sample/images", false)!!.value,
        resolver.findResValue("@sample/images", false)!!.value,
      )
    assertTrue(imagePaths.contains(image1.virtualFile.canonicalPath))
    assertTrue(imagePaths.contains(image2.virtualFile.canonicalPath))

    // Check that we wrap around
    assertEquals("string1", resolver.findResValue("@sample/strings", false)!!.value)
    val reference =
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA, "strings")
    assertEquals("string2", resolver.getResolvedResource(reference)!!.value)
    assertEquals("1", resolver.findResValue("@sample/ints", false)!!.value)
    assertTrue(imagePaths.contains(resolver.findResValue("@sample/images", false)!!.value))

    // Check reference resolution
    assertEquals(
      "Hello 1",
      resolver
        .resolveResValue(
          ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.STRING, "test", "@sample/refs")
        )!!
        .value,
    )
    // @string/invalid does not exist so the sample data will just return the unresolved reference
    assertEquals(
      "@string/invalid",
      resolver
        .resolveResValue(
          ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.STRING, "test", "@sample/refs")
        )!!
        .value,
    )

    // Check indexing (all calls should return the same)
    assertEquals("Name2", resolver.findResValue("@sample/users.json/users/name[1]", false)!!.value)
    assertEquals("Name2", resolver.findResValue("@sample/users.json/users/name[1]", false)!!.value)

    assertNull(resolver.findResValue("@sample/invalid", false))

    val elementRef =
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA, "strings[1]")
    assertNotNull(resolver.getResolvedResource(elementRef))
  }

  @Test
  fun testSampleDataFileInvalidation_addAndDeleteFile() {
    val repo = StudioResourceRepositoryManager.getInstance(facet).sampleDataResources
    waitForUpdates(repo)
    assertTrue(getResources(repo).isEmpty())

    val strings =
      projectRule.fixture.addFileToProject(
        "sampledata/strings",
        """
        string1
        string2
        string3
        
        """
          .trimIndent(),
      )
    waitForUpdates(repo)
    assertEquals(1, getResources(repo).size)
    assertEquals(1, getResources(repo, "strings").size)

    WriteAction.runAndWait<IOException> { strings.virtualFile.delete(null) }
    waitForUpdates(repo)
    assertTrue(getResources(repo).isEmpty())
  }

  @Test
  fun testSampleDataFileInvalidation_deleteSampleDataDirectory() {
    val repo = StudioResourceRepositoryManager.getInstance(facet).sampleDataResources

    projectRule.fixture.addFileToProject("sampledata/strings", "string1\n")
    waitForUpdates(repo)
    assertEquals(1, getResources(repo).size)

    val sampleDir = requireNotNull(appModuleSystem.getSampleDataDirectory().toVirtualFile())
    WriteAction.runAndWait<IOException> { sampleDir.delete(null) }
    waitForUpdates(repo)
    assertTrue(getResources(repo).isEmpty())
  }

  @Test
  fun testSampleDataFileInvalidation_moveFiles() {
    val repo = StudioResourceRepositoryManager.getInstance(facet).sampleDataResources

    val sampleDir =
      requireNotNull(
        WriteAction.computeAndWait<PathString?, IOException> {
            appModuleSystem.getOrCreateSampleDataDirectory()
          }
          .toVirtualFile()
      )
    val stringsOutside = projectRule.fixture.addFileToProject("strings", "string1\n")

    // move strings into sample data directory
    WriteAction.runAndWait<IOException> { stringsOutside.virtualFile.move(null, sampleDir) }
    waitForUpdates(repo)
    assertEquals(1, getResources(repo).size)

    // move strings out of sample data directory
    val stringsInside = requireNotNull(sampleDir.findChild(stringsOutside.name))
    WriteAction.runAndWait<IOException> { stringsInside.move(null, sampleDir.parent) }
    waitForUpdates(repo)
    assertTrue(getResources(repo).isEmpty())
  }

  @Test
  fun testSampleDataFileInvalidation_moveSampleDataDirectory() {
    val repo = StudioResourceRepositoryManager.getInstance(facet).sampleDataResources

    val sampleDir =
      requireNotNull(
        WriteAction.computeAndWait<PathString?, IOException> {
            appModuleSystem.getOrCreateSampleDataDirectory()
          }
          .toVirtualFile()
      )
    projectRule.fixture.addFileToProject("sampledata/strings", "string1\n")
    waitForUpdates(repo)
    assertEquals(1, getResources(repo).size)

    WriteAction.runAndWait<IOException> {
      val newParent = sampleDir.parent.createChildDirectory(null, "somewhere_else")
      sampleDir.move(null, newParent)
    }
    waitForUpdates(repo)
    assertTrue(getResources(repo).isEmpty())
  }

  @Test
  fun testJsonSampleData() {
    projectRule.fixture.addFileToProject(
      "sampledata/users.json",
      // language=JSON
      """
      {
        "users": [
          {
            "name": "Name1",
            "surname": "Surname1"
          },
          {
            "name": "Name2",
            "surname": "Surname2"
          },
          {
            "name": "Name3",
            "surname": "Surname3",
            "phone": "555-00000"
          }
        ]
      }
      """
        .trimIndent(),
    )
    projectRule.fixture.addFileToProject(
      "sampledata/invalid.json",
      // language=JSON
      """
      {
        "users": [
          {
            "name": "Name1",
            "surname": "Surname1"
          },
      """
        .trimIndent(),
    )
    val repo = SampleDataResourceRepository(facet, projectRule.testRootDisposable)
    waitForUpdates(repo)

    // Three different items are expected, one for the users/name path, other for users/surname and
    // a last one for users/phone
    assertEquals(3, getResources(repo).size)
    assertEquals(1, getResources(repo, "users.json/users/name").size)
  }

  @Test
  fun testCsvSampleData() {
    projectRule.fixture.addFileToProject(
      "sampledata/users.csv",
      """
      name,surname,phone
      Name1,Surname1
      Name2,Surname2
      Name3,Surname3,555-00000
      """
        .trimIndent(),
    )
    val repo = SampleDataResourceRepository(facet, projectRule.testRootDisposable)
    waitForUpdates(repo)

    // Three different items are expected, one for the users/name path, other for users/surname and
    // a last one for users/phone
    assertEquals(3, getResources(repo).size)
    assertEquals(1, getResources(repo, "users.csv/name").size)
  }

  @Test
  fun testResolverCacheInvalidation() {
    val sampleDataFile =
      projectRule.fixture.addFileToProject(
        "sampledata/strings",
        """
        string1
        string2
        string3
        
        """
          .trimIndent(),
      )
    val layout = addLayoutFile()
    val resolver =
      ConfigurationManager.getOrCreateInstance(projectRule.module)
        .getConfiguration(layout.virtualFile)
        .resourceResolver
    assertEquals("string1", resolver.findResValue("@sample/strings", false)!!.value)
    assertEquals("string2", resolver.findResValue("@sample/strings", false)!!.value)
    ApplicationManager.getApplication().runWriteAction {
      try {
        sampleDataFile.virtualFile.setBinaryContent(
          """
          new1
          new2
          new3
          new4
          """
            .trimIndent()
            .toByteArray(Charsets.UTF_8)
        )
        PsiDocumentManager.getInstance(projectRule.project).commitAllDocuments()
      } catch (e: IOException) {
        e.printStackTrace()
      }
    }

    // The cursor does not get reset when the file is changed so we expect "new3" as opposed as
    // getting "new1"
    // Ignored temporarily since cache invalidation needs still work
    // assertEquals("new3", resolver.findResValue("@sample/strings", false).getValue());
  }

  @Test
  fun testImageResources() {
    projectRule.fixture.addFileToProject("sampledata/images/image1.png", "\n")
    projectRule.fixture.addFileToProject("sampledata/images/image2.png", "\n")
    projectRule.fixture.addFileToProject("sampledata/images/image3.png", "\n")
    val rootImagePsiFile = projectRule.fixture.addFileToProject("sampledata/root_image.png", "\n")

    val repository = StudioResourceRepositoryManager.getAppResources(facet)
    waitForUpdates(repository)
    val items =
      repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA).values()
    UsefulTestCase.assertSize(2, items)
    val item =
      repository
        .getResources(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA, "images")
        .single() as SampleDataResourceItem
    assertEquals("images", item.name)
    assertEquals(SampleDataResourceItem.ContentType.IMAGE, item.contentType)
    val value = item.resourceValue as SampleDataResourceValue
    val fileNames = value.valueAsLines.map { file -> File(file).name }
    UsefulTestCase.assertContainsElements(fileNames, "image1.png", "image2.png", "image3.png")

    val rootImageItem =
      repository
        .getResources(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA, "root_image.png")
        .single() as SampleDataResourceItem
    assertEquals(rootImageItem.contentType, rootImageItem.contentType)
    assertEquals(rootImagePsiFile.virtualFile.path, rootImageItem.valueText)
  }

  @Test
  fun testSubsetSampleData() {
    val layout = addLayoutFile()
    val configuration =
      ConfigurationManager.getOrCreateInstance(projectRule.module)
        .getConfiguration(layout.virtualFile)
    val resolver = configuration.resourceResolver
    val sampledLorem: ResourceValue =
      ResourceValueImpl(
        ResourceNamespace.TOOLS,
        ResourceType.SAMPLE_DATA,
        "lorem_data",
        "@sample/lorem[4:10]",
      )
    assertEquals("Lorem ipsum dolor sit amet.", resolver.dereference(sampledLorem)!!.value)
    assertEquals(
      "Lorem ipsum dolor sit amet, consectetur.",
      resolver.dereference(sampledLorem)!!.value,
    )
  }

  @Test
  fun testResetWithNoRepo() {
    StudioResourceRepositoryManager.getInstance(facet).resetAllCaches()
  }

  @Test
  fun testSampleDataInLibrary() {
    projectRule.fixture.addFileToProject(
      "lib/sampledata/lib.csv",
      // language=csv
      """
      name,surname,phone
      LibName1,LibSurname1
      LibName2,LibSurname2
      LibName3,LibSurname3,555-00000
      """
        .trimIndent(),
    )
    projectRule.fixture.addFileToProject(
      "transitive/sampledata/transitive.csv",
      // language=csv
      """
      name,surname,phone
      TransitiveName1,TransitiveSurname1
      TransitiveName2,TransitiveSurname2
      TransitiveName3,TransitiveSurname3,555-00000
      """
        .trimIndent(),
    )
    val repo = SampleDataResourceRepository(facet, projectRule.testRootDisposable)
    waitForUpdates(repo)

    // Three different items are expected, one for the users/name path, other for users/surname and
    // a last one for users/phone
    assertEquals(6, getResources(repo).size)
    assertEquals(1, getResources(repo, "lib.csv/name").size)
    assertEquals(1, getResources(repo, "transitive.csv/name").size)

    val layout = addLayoutFile()
    val configuration =
      ConfigurationManager.getOrCreateInstance(projectRule.module)
        .getConfiguration(layout.virtualFile)
    val resolver = configuration.resourceResolver
    assertEquals("LibName1", resolver.findResValue("@sample/lib.csv/name", false)!!.value)
    assertEquals(
      "TransitiveName1",
      resolver.findResValue("@sample/transitive.csv/name", false)!!.value,
    )
  }

  @Test
  fun testMultiModuleAppOverrides() {
    projectRule.fixture.addFileToProject(
      "sampledata/users.csv",
      // language=csv
      """
      name,surname,phone
      AppName1,AppSurname1
      AppName2,AppSurname2
      AppName3,AppSurname3,555-00000
      """
        .trimIndent(),
    )
    projectRule.fixture.addFileToProject(
      "lib/sampledata/users.csv",
      // language=csv
      """
      name,surname,phone
      LibName1,LibSurname1
      LibName2,LibSurname2
      LibName3,LibSurname3,555-00000
      """
        .trimIndent(),
    )
    projectRule.fixture.addFileToProject(
      "transitive/sampledata/users.csv",
      // language=csv
      """
      name,surname,phone
      TransitiveName1,TransitiveSurname1
      TransitiveName2,TransitiveSurname2
      TransitiveName3,TransitiveSurname3,555-00000
      """
        .trimIndent(),
    )
    val repo = SampleDataResourceRepository(facet, projectRule.testRootDisposable)
    waitForUpdates(repo)

    val layout = addLayoutFile()
    // Three different items are expected, one for the users/name path, other for users/surname and
    // a last one for users/phone
    assertEquals(3, getResources(repo).size)
    assertEquals(1, getResources(repo, "users.csv/name").size)
    val configuration =
      ConfigurationManager.getOrCreateInstance(projectRule.module)
        .getConfiguration(layout.virtualFile)
    val resolver = configuration.resourceResolver
    assertEquals("AppName1", resolver.findResValue("@sample/users.csv/name", false)!!.value)
  }

  companion object {
    private fun getResources(repo: ResourceRepository): Collection<ResourceItem> {
      return repo.getResources(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA).values()
    }

    private fun getResources(repo: ResourceRepository, resName: String): List<ResourceItem> {
      return repo.getResources(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA, resName)
    }
  }
}
