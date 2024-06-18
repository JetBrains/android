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
import com.android.tools.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.projectsystem.AndroidModuleSystem
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.testing.AndroidModuleDependency
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.AndroidProjectRule.Companion.withAndroidModels
import com.android.tools.idea.testing.AndroidProjectStubBuilder
import com.android.tools.idea.testing.waitForUpdates
import com.android.tools.idea.util.toVirtualFile
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.collect.Lists
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.UsefulTestCase
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeoutException
import java.util.stream.Collectors
import org.intellij.lang.annotations.Language
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Test for [SampleDataResourceRepository] and [SampleDataListener]. */
@RunsInEdt
class SampleDataResourceRepositoryTest {
  @Rule
  val myProjectRule: AndroidProjectRule =
    withAndroidModels(
      AndroidModuleModelBuilder(
        ":",
        "debug",
        AndroidProjectBuilder().withAndroidModuleDependencyList {
          it: AndroidProjectStubBuilder?,
          variant: String? ->
          Lists.newArrayList(AndroidModuleDependency(":lib", "debug"))
        },
      ),
      AndroidModuleModelBuilder(
        ":lib",
        "debug",
        AndroidProjectBuilder()
          .withProjectType { it: AndroidProjectStubBuilder? ->
            IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
          }
          .withAndroidModuleDependencyList { it: AndroidProjectStubBuilder?, variant: String? ->
            Lists.newArrayList(AndroidModuleDependency(":transitive", "debug"))
          },
      ),
      AndroidModuleModelBuilder(
        ":transitive",
        "debug",
        AndroidProjectBuilder().withProjectType { it: AndroidProjectStubBuilder? ->
          IdeAndroidProjectType.PROJECT_TYPE_LIBRARY
        },
      ),
    )
  @Rule val myEdtRule: EdtRule = EdtRule()
  private var myAppModuleSystem: AndroidModuleSystem? = null
  private var myFacet: AndroidFacet? = null

  @Before
  @Throws(Exception::class)
  fun setUp() {
    myAppModuleSystem = myProjectRule.module.getModuleSystem()
    myFacet = AndroidFacet.getInstance(myProjectRule.module)
  }

  @After
  @Throws(Exception::class)
  fun tearDown() {
    SampleDataResourceItem.invalidateCache()
  }

  private fun addLayoutFile(): PsiFile {
    @Language("XML")
    val layoutText =
      """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />"""

    return myProjectRule.fixture.addFileToProject("src/main/res/layout/layout.xml", layoutText)
  }

  @Test
  @Throws(InterruptedException::class, TimeoutException::class)
  fun testDataLoad() {
    myProjectRule.fixture.addFileToProject(
      "sampledata/strings",
      """
                                                 string1
                                                 string2
                                                 string3
                                                 
                                                 """
        .trimIndent(),
    )
    myProjectRule.fixture.addFileToProject("sampledata/images/image1.png", "Insert image here\n")
    myProjectRule.fixture.addFileToProject("sampledata/images/image2.jpg", "Insert image here 2\n")
    myProjectRule.fixture.addFileToProject("sampledata/images/image3.png", "Insert image here 3\n")
    myProjectRule.fixture.addFileToProject("sampledata/root_image.png", "Insert image here 3\n")
    val repo = SampleDataResourceRepository(myFacet!!, myProjectRule.testRootDisposable)
    waitForUpdates(repo)

    Assert.assertEquals(3, getResources(repo).size.toLong())
    Assert.assertEquals(1, getResources(repo, "strings").size.toLong())
    Assert.assertEquals(1, getResources(repo, "images").size.toLong())
    Assert.assertEquals(1, getResources(repo, "root_image.png").size.toLong())
  }

  @Test
  @Throws(InterruptedException::class, TimeoutException::class)
  fun testResolver() {
    @Language("XML")
    val stringsText =
      """<?xml version="1.0" encoding="utf-8"?>
<resources>
  <string name="test1">Hello 1</string>
  <string name="test2">Hello 2</string>
</resources>"""

    myProjectRule.fixture.addFileToProject(
      "sampledata/strings",
      """
                                                 string1
                                                 string2
                                                 string3
                                                 
                                                 """
        .trimIndent(),
    )
    myProjectRule.fixture.addFileToProject(
      "sampledata/ints",
      """
                                                 1
                                                 2
                                                 
                                                 """
        .trimIndent(),
    )
    myProjectRule.fixture.addFileToProject(
      "sampledata/refs",
      """
                                                 @string/test1
                                                 @string/invalid
                                                 
                                                 """
        .trimIndent(),
    )
    myProjectRule.fixture.addFileToProject(
      "sampledata/users.json", // language="JSON"
      """{
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
}""",
    )
    val image1 =
      myProjectRule.fixture.addFileToProject("sampledata/images/image1.png", "Insert image here\n")
    val image2 =
      myProjectRule.fixture.addFileToProject(
        "sampledata/images/image2.jpg",
        "Insert image here 2\n",
      )
    myProjectRule.fixture.addFileToProject("src/main/res/values/strings.xml", stringsText)
    val layout = addLayoutFile()
    val configuration: Configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.module)
        .getConfiguration(layout.virtualFile)
    waitForUpdates(StudioResourceRepositoryManager.getInstance(myFacet!!).sampleDataResources)
    val resolver = configuration.resourceResolver
    Assert.assertEquals("string1", resolver.findResValue("@sample/strings", false)!!.value)
    Assert.assertEquals("1", resolver.findResValue("@sample/ints", false)!!.value)
    Assert.assertEquals("string2", resolver.findResValue("@sample/strings", false)!!.value)
    Assert.assertEquals("string3", resolver.findResValue("@sample/strings", false)!!.value)
    Assert.assertEquals("2", resolver.findResValue("@sample/ints", false)!!.value)

    // Test passing json references
    Assert.assertEquals(
      "Name1",
      resolver.findResValue("@sample/users.json/users/name", false)!!.value,
    )

    // The order of the returned paths might depend on the file system
    val imagePaths: Set<String?> =
      ImmutableSet.of(
        resolver.findResValue("@sample/images", false)!!.value,
        resolver.findResValue("@sample/images", false)!!.value,
      )
    Assert.assertTrue(imagePaths.contains(image1.virtualFile.canonicalPath))
    Assert.assertTrue(imagePaths.contains(image2.virtualFile.canonicalPath))

    // Check that we wrap around
    Assert.assertEquals("string1", resolver.findResValue("@sample/strings", false)!!.value)
    val reference =
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA, "strings")
    Assert.assertEquals("string2", resolver.getResolvedResource(reference)!!.value)
    Assert.assertEquals("1", resolver.findResValue("@sample/ints", false)!!.value)
    Assert.assertTrue(imagePaths.contains(resolver.findResValue("@sample/images", false)!!.value))

    // Check reference resolution
    Assert.assertEquals(
      "Hello 1",
      resolver
        .resolveResValue(
          ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.STRING, "test", "@sample/refs")
        )!!
        .value,
    )
    // @string/invalid does not exist so the sample data will just return the unresolved reference
    Assert.assertEquals(
      "@string/invalid",
      resolver
        .resolveResValue(
          ResourceValueImpl(ResourceNamespace.RES_AUTO, ResourceType.STRING, "test", "@sample/refs")
        )!!
        .value,
    )

    // Check indexing (all calls should return the same)
    Assert.assertEquals(
      "Name2",
      resolver.findResValue("@sample/users.json/users/name[1]", false)!!.value,
    )
    Assert.assertEquals(
      "Name2",
      resolver.findResValue("@sample/users.json/users/name[1]", false)!!.value,
    )

    Assert.assertNull(resolver.findResValue("@sample/invalid", false))

    val elementRef =
      ResourceReference(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA, "strings[1]")
    Assert.assertNotNull(resolver.getResolvedResource(elementRef))
  }

  @Test
  @Throws(IOException::class, InterruptedException::class, TimeoutException::class)
  fun testSampleDataFileInvalidation_addAndDeleteFile() {
    val repo = StudioResourceRepositoryManager.getInstance(myFacet!!).sampleDataResources
    waitForUpdates(repo)
    Assert.assertTrue(getResources(repo).isEmpty())

    val strings =
      myProjectRule.fixture.addFileToProject(
        "sampledata/strings",
        """
                                                                string1
                                                                string2
                                                                string3
                                                                
                                                                """
          .trimIndent(),
      )
    waitForUpdates(repo)
    Assert.assertEquals(1, getResources(repo).size.toLong())
    Assert.assertEquals(1, getResources(repo, "strings").size.toLong())

    WriteAction.runAndWait<IOException> { strings.virtualFile.delete(null) }
    waitForUpdates(repo)
    Assert.assertTrue(getResources(repo).isEmpty())
  }

  @Test
  @Throws(IOException::class, InterruptedException::class, TimeoutException::class)
  fun testSampleDataFileInvalidation_deleteSampleDataDirectory() {
    val repo = StudioResourceRepositoryManager.getInstance(myFacet!!).sampleDataResources

    myProjectRule.fixture.addFileToProject("sampledata/strings", "string1\n")
    waitForUpdates(repo)
    Assert.assertEquals(1, getResources(repo).size.toLong())

    val sampleDir = myAppModuleSystem!!.getSampleDataDirectory().toVirtualFile()
    WriteAction.runAndWait<IOException> { sampleDir!!.delete(null) }
    waitForUpdates(repo)
    Assert.assertTrue(getResources(repo).isEmpty())
  }

  @Test
  @Throws(IOException::class, InterruptedException::class, TimeoutException::class)
  fun testSampleDataFileInvalidation_moveFiles() {
    val repo = StudioResourceRepositoryManager.getInstance(myFacet!!).sampleDataResources

    val sampleDir =
      WriteAction.computeAndWait<PathString?, IOException> {
          myAppModuleSystem!!.getOrCreateSampleDataDirectory()
        }
        .toVirtualFile()
    val stringsOutside = myProjectRule.fixture.addFileToProject("strings", "string1\n")

    // move strings into sample data directory
    WriteAction.runAndWait<IOException> { stringsOutside.virtualFile.move(null, sampleDir!!) }
    waitForUpdates(repo)
    Assert.assertEquals(1, getResources(repo).size.toLong())

    // move strings out of sample data directory
    val stringsInside = sampleDir!!.findChild(stringsOutside.name)
    WriteAction.runAndWait<IOException> { stringsInside!!.move(null, sampleDir.parent) }
    waitForUpdates(repo)
    Assert.assertTrue(getResources(repo).isEmpty())
  }

  @Test
  @Throws(IOException::class, InterruptedException::class, TimeoutException::class)
  fun testSampleDataFileInvalidation_moveSampleDataDirectory() {
    val repo = StudioResourceRepositoryManager.getInstance(myFacet!!).sampleDataResources

    val sampleDir =
      WriteAction.computeAndWait<PathString?, IOException> {
          myAppModuleSystem!!.getOrCreateSampleDataDirectory()
        }
        .toVirtualFile()
    myProjectRule.fixture.addFileToProject("sampledata/strings", "string1\n")
    waitForUpdates(repo)
    Assert.assertEquals(1, getResources(repo).size.toLong())

    WriteAction.runAndWait<IOException> {
      val newParent = sampleDir!!.parent.createChildDirectory(null, "somewhere_else")
      sampleDir.move(null, newParent)
    }
    waitForUpdates(repo)
    Assert.assertTrue(getResources(repo).isEmpty())
  }

  @Test
  @Throws(InterruptedException::class, TimeoutException::class)
  fun testJsonSampleData() {
    myProjectRule.fixture.addFileToProject(
      "sampledata/users.json",
      """{
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
}""",
    )
    myProjectRule.fixture.addFileToProject(
      "sampledata/invalid.json",
      """{
  "users": [
    {
      "name": "Name1",
      "surname": "Surname1"
    },
""",
    )
    val repo = SampleDataResourceRepository(myFacet!!, myProjectRule.testRootDisposable)
    waitForUpdates(repo)

    // Three different items are expected, one for the users/name path, other for users/surname and
    // a last one for users/phone
    Assert.assertEquals(3, getResources(repo).size.toLong())
    Assert.assertEquals(1, getResources(repo, "users.json/users/name").size.toLong())
  }

  @Test
  @Throws(InterruptedException::class, TimeoutException::class)
  fun testCsvSampleData() {
    myProjectRule.fixture.addFileToProject(
      "sampledata/users.csv",
      """
                                                 name,surname,phone
                                                 Name1,Surname1
                                                 Name2,Surname2
                                                 Name3,Surname3,555-00000
                                                 """
        .trimIndent(),
    )
    val repo = SampleDataResourceRepository(myFacet!!, myProjectRule.testRootDisposable)
    waitForUpdates(repo)

    // Three different items are expected, one for the users/name path, other for users/surname and
    // a last one for users/phone
    Assert.assertEquals(3, getResources(repo).size.toLong())
    Assert.assertEquals(1, getResources(repo, "users.csv/name").size.toLong())
  }

  @Test
  fun testResolverCacheInvalidation() {
    val sampleDataFile =
      myProjectRule.fixture.addFileToProject(
        "sampledata/strings",
        """
                                                                       string1
                                                                       string2
                                                                       string3
                                                                       
                                                                       """
          .trimIndent(),
      )
    val layout = addLayoutFile()
    val configuration: Configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.module)
        .getConfiguration(layout.virtualFile)
    val resolver = configuration.resourceResolver
    Assert.assertEquals("string1", resolver.findResValue("@sample/strings", false)!!.value)
    Assert.assertEquals("string2", resolver.findResValue("@sample/strings", false)!!.value)
    ApplicationManager.getApplication().runWriteAction {
      try {
        sampleDataFile.virtualFile.setBinaryContent(
          """new1
new2
new3
new4
"""
            .toByteArray(Charsets.UTF_8)
        )
        PsiDocumentManager.getInstance(myProjectRule.project).commitAllDocuments()
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
  @Throws(InterruptedException::class, TimeoutException::class)
  fun testImageResources() {
    myProjectRule.fixture.addFileToProject("sampledata/images/image1.png", "\n")
    myProjectRule.fixture.addFileToProject("sampledata/images/image2.png", "\n")
    myProjectRule.fixture.addFileToProject("sampledata/images/image3.png", "\n")
    val rootImagePsiFile = myProjectRule.fixture.addFileToProject("sampledata/root_image.png", "\n")

    val repository = StudioResourceRepositoryManager.getAppResources(myFacet!!)
    waitForUpdates(repository)
    val items =
      repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA).values()
    UsefulTestCase.assertSize(2, items)
    val item =
      Iterables.getOnlyElement(
        repository.getResources(ResourceNamespace.RES_AUTO, ResourceType.SAMPLE_DATA, "images")
      ) as SampleDataResourceItem
    Assert.assertEquals("images", item.name)
    Assert.assertEquals(SampleDataResourceItem.ContentType.IMAGE, item.contentType)
    val value = item.resourceValue as SampleDataResourceValue
    val fileNames =
      value.valueAsLines
        .stream()
        .map { file: String? -> File(file).name }
        .collect(Collectors.toList())
    UsefulTestCase.assertContainsElements(fileNames, "image1.png", "image2.png", "image3.png")

    val rootImageItem =
      Iterables.getOnlyElement(
        repository.getResources(
          ResourceNamespace.RES_AUTO,
          ResourceType.SAMPLE_DATA,
          "root_image.png",
        )
      ) as SampleDataResourceItem
    Assert.assertEquals(rootImageItem.contentType, rootImageItem.contentType)
    Assert.assertEquals(rootImagePsiFile.virtualFile.path, rootImageItem.valueText)
  }

  @Test
  fun testSubsetSampleData() {
    val layout = addLayoutFile()
    val configuration: Configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.module)
        .getConfiguration(layout.virtualFile)
    val resolver = configuration.resourceResolver
    val sampledLorem: ResourceValue =
      ResourceValueImpl(
        ResourceNamespace.TOOLS,
        ResourceType.SAMPLE_DATA,
        "lorem_data",
        "@sample/lorem[4:10]",
      )
    Assert.assertEquals("Lorem ipsum dolor sit amet.", resolver.dereference(sampledLorem)!!.value)
    Assert.assertEquals(
      "Lorem ipsum dolor sit amet, consectetur.",
      resolver.dereference(sampledLorem)!!.value,
    )
  }

  @Test
  fun testResetWithNoRepo() {
    StudioResourceRepositoryManager.getInstance(myFacet!!).resetAllCaches()
  }

  @Test
  @Throws(InterruptedException::class, TimeoutException::class)
  fun testSampleDataInLibrary() {
    myProjectRule.fixture.addFileToProject(
      "lib/sampledata/lib.csv",
      """
                                                 name,surname,phone
                                                 LibName1,LibSurname1
                                                 LibName2,LibSurname2
                                                 LibName3,LibSurname3,555-00000
                                                 """
        .trimIndent(),
    )
    myProjectRule.fixture.addFileToProject(
      "transitive/sampledata/transitive.csv",
      """
                                                 name,surname,phone
                                                 TransitiveName1,TransitiveSurname1
                                                 TransitiveName2,TransitiveSurname2
                                                 TransitiveName3,TransitiveSurname3,555-00000
                                                 """
        .trimIndent(),
    )
    val repo = SampleDataResourceRepository(myFacet!!, myProjectRule.testRootDisposable)
    waitForUpdates(repo)

    // Three different items are expected, one for the users/name path, other for users/surname and
    // a last one for users/phone
    Assert.assertEquals(6, getResources(repo).size.toLong())
    Assert.assertEquals(1, getResources(repo, "lib.csv/name").size.toLong())
    Assert.assertEquals(1, getResources(repo, "transitive.csv/name").size.toLong())

    val layout = addLayoutFile()
    val configuration: Configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.module)
        .getConfiguration(layout.virtualFile)
    val resolver = configuration.resourceResolver
    Assert.assertEquals("LibName1", resolver.findResValue("@sample/lib.csv/name", false)!!.value)
    Assert.assertEquals(
      "TransitiveName1",
      resolver.findResValue("@sample/transitive.csv/name", false)!!.value,
    )
  }

  @Test
  @Throws(InterruptedException::class, TimeoutException::class)
  fun testMultiModuleAppOverrides() {
    myProjectRule.fixture.addFileToProject(
      "sampledata/users.csv",
      """
                                                 name,surname,phone
                                                 AppName1,AppSurname1
                                                 AppName2,AppSurname2
                                                 AppName3,AppSurname3,555-00000
                                                 """
        .trimIndent(),
    )
    myProjectRule.fixture.addFileToProject(
      "lib/sampledata/users.csv",
      """
                                                 name,surname,phone
                                                 LibName1,LibSurname1
                                                 LibName2,LibSurname2
                                                 LibName3,LibSurname3,555-00000
                                                 """
        .trimIndent(),
    )
    myProjectRule.fixture.addFileToProject(
      "transitive/sampledata/users.csv",
      """
                                                 name,surname,phone
                                                 TransitiveName1,TransitiveSurname1
                                                 TransitiveName2,TransitiveSurname2
                                                 TransitiveName3,TransitiveSurname3,555-00000
                                                 """
        .trimIndent(),
    )
    val repo = SampleDataResourceRepository(myFacet!!, myProjectRule.testRootDisposable)
    waitForUpdates(repo)

    val layout = addLayoutFile()
    // Three different items are expected, one for the users/name path, other for users/surname and
    // a last one for users/phone
    Assert.assertEquals(3, getResources(repo).size.toLong())
    Assert.assertEquals(1, getResources(repo, "users.csv/name").size.toLong())
    val configuration: Configuration =
      ConfigurationManager.getOrCreateInstance(myProjectRule.module)
        .getConfiguration(layout.virtualFile)
    val resolver = configuration.resourceResolver
    Assert.assertEquals("AppName1", resolver.findResValue("@sample/users.csv/name", false)!!.value)
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
