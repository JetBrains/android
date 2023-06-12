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
package com.android.tools.idea.nav.safeargs.index

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.DataExternalizer
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class NavXmlIndexTest {
  private val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val navArgsFlagRule = FlagRule(StudioFlags.NAV_SAFE_ARGS_SUPPORT)

  @get:Rule
  val ruleChain = RuleChain.outerRule(projectRule).around(EdtRule())!!

  private val fixture: CodeInsightTestFixture by lazy {
    projectRule.fixture
  }

  @Test
  fun indexingSkippedIfFlagNotEnabled() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(false)

    val file = fixture.addFileToProject(
      "navigation/main.xml",
      //language=XML
      """
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:app="http://schemas.android.com/apk/res-auto"
                  xmlns:tools="http://schemas.android.com/tools"
                  android:id="@+id/top_level_nav"
                  app:startDestination="@id/fragment1" />
      """.trimIndent()).virtualFile


    val navXmlIndex = NavXmlIndex()
    assertThat(navXmlIndex.inputFilter.acceptInput(file)).isEqualTo(false)
  }

  @Test
  fun indexNavigationLayout() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)

    val file = fixture.addFileToProject(
      "navigation/main.xml",
      //language=XML
      """
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:app="http://schemas.android.com/apk/res-auto"
                  xmlns:tools="http://schemas.android.com/tools"
                  android:id="@+id/top_level_nav"
                  app:startDestination="@id/fragment1">

          <action android:id="@+id/action_to_fragment2"
                  app:destination="@id/fragment2" />

          <argument android:name="top_level_argument"
                    app:argType="string"
                    android:defaultValue="topLevelString" />

          <activity android:id="@+id/activity1"
                    android:name="test.safeargs.Activity1"
                    tools:layout="@layout/activity1">

              <action android:id="@+id/action_activity1_to_activity2"
                      app:destination="@id/activity2" />
          </activity>

          <fragment android:id="@+id/fragment1"
                    android:name="test.safeargs.Fragment1"
                    tools:layout="@layout/fragment1">

              <action android:id="@+id/action_fragment1_to_fragment2"
                      app:destination="@id/fragment2" />

              <action android:id="@+id/action_fragment1_to_fragment3"
                      app:destination="@id/fragment3" />
          </fragment>

          <fragment android:id="@+id/fragment2"
                    android:name="test.safeargs.Fragment2"
                    tools:layout="@layout/fragment2">

              <argument android:name="arg"
                        app:argType="string"
                        android:defaultValue="someString"/>

              <action android:id="@+id/action_fragment2_to_fragment3"
                      app:destination="@id/fragment3" />
          </fragment>

          <navigation android:id="@+id/nested_nav"
                      app:startDestination="@id/fragment3">

              <navigation android:id="@+id/double_nested_nav"
                          app:startDestination="@id/dialog1">
                  <dialog android:id="@+id/dialog1"
                          android:name="test.safeargs.Dialog1"
                          tools:layout="@layout/dialog1">
                      <argument android:name="arg1"
                            app:argType="long"
                            android:defaultValue="1234"/>
                      <action android:id="@+id/action_dialog1_to_fragment2"
                          app:destination="@id/fragment2" />
                  </dialog>
              </navigation>

              <activity android:id="@+id/activity2"
                        android:name="test.safeargs.Activity2"
                        tools:layout="@layout/activity2">

                  <argument android:name="arg1"
                            app:argType="string"
                            android:defaultValue="placeholder"/>

                  <argument android:name="arg2"
                            app:argType="boolean" />

                  <action android:id="@+id/action_activity2_to_activity1"
                          app:destination="@id/activity1" />
              </activity>

              <fragment android:id="@+id/fragment3"
                        android:name="test.safeargs.Fragment3"
                        tools:layout="@layout/fragment3">

                  <argument android:name="arg1"
                            app:argType="float" />

                  <argument android:name="arg2"
                            app:argType="integer" />

                  <action android:id="@+id/action_fragment3_to_fragment1"
                          app:destination="@id/fragment1" />

              </fragment>

          </navigation>

      </navigation>
    """.trimIndent()).virtualFile

    val navXmlIndex = NavXmlIndex()
    assertThat(navXmlIndex.inputFilter.acceptInput(file)).isEqualTo(true)

    val map = navXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    assertThat(map).hasSize(1)

    val data = map.values.first()
    assertThat(data.root.id).isEqualTo("top_level_nav")
    assertThat(data.root.toDestination()!!.name).isEqualTo(".TopLevelNav")
    assertThat(data.root.startDestination).isEqualTo("fragment1")
    assertThat(data.root.actions).hasSize(1)
    data.root.actions[0].let { action ->
      assertThat(action.id).isEqualTo("action_to_fragment2")
      assertThat(action.resolveDestination()).isEqualTo("fragment2")
    }
    assertThat(data.root.arguments).hasSize(1)
    data.root.arguments[0].let { argument ->
      assertThat(argument.type).isEqualTo("string")
      assertThat(argument.name).isEqualTo("top_level_argument")
      assertThat(argument.defaultValue).isEqualTo("topLevelString")
    }

    assertThat(data.root.potentialDestinations).hasSize(3)
    data.root.potentialDestinations[0].toDestination()!!.let { destination ->
      assertThat(destination.id).isEqualTo("activity1")
      assertThat(destination.name).isEqualTo("test.safeargs.Activity1")
      assertThat(destination.arguments).isEmpty()
      assertThat(destination.actions).hasSize(1)
      destination.actions[0].let { action ->
        assertThat(action.id).isEqualTo("action_activity1_to_activity2")
        assertThat(action.resolveDestination()).isEqualTo("activity2")
      }
    }
    data.root.potentialDestinations[1].toDestination()!!.let { destination ->
      assertThat(destination.id).isEqualTo("fragment1")
      assertThat(destination.name).isEqualTo("test.safeargs.Fragment1")
      assertThat(destination.arguments).isEmpty()
      assertThat(destination.actions).hasSize(2)
      destination.actions[0].let { action ->
        assertThat(action.id).isEqualTo("action_fragment1_to_fragment2")
        assertThat(action.resolveDestination()).isEqualTo("fragment2")
      }
      destination.actions[1].let { action ->
        assertThat(action.id).isEqualTo("action_fragment1_to_fragment3")
        assertThat(action.resolveDestination()).isEqualTo("fragment3")
      }
    }
    data.root.potentialDestinations[2].toDestination()!!.let { destination ->
      assertThat(destination.id).isEqualTo("fragment2")
      assertThat(destination.name).isEqualTo("test.safeargs.Fragment2")
      assertThat(destination.arguments).hasSize(1)
      assertThat(destination.actions).hasSize(1)
      destination.arguments[0].let { argument ->
        assertThat(argument.type).isEqualTo("string")
        assertThat(argument.name).isEqualTo("arg")
        assertThat(argument.defaultValue).isEqualTo("someString")
      }
      destination.actions[0].let { action ->
        assertThat(action.id).isEqualTo("action_fragment2_to_fragment3")
        assertThat(action.resolveDestination()).isEqualTo("fragment3")
      }
    }

    assertThat(data.root.navigations).hasSize(1)
    data.root.navigations[0].let { nested ->
      nested.navigations[0].let { doubleNested ->
        assertThat(doubleNested.id).isEqualTo("double_nested_nav")
        assertThat(doubleNested.potentialDestinations).hasSize(1)
        doubleNested.potentialDestinations[0].toDestination()!!.let { destination ->
          assertThat(destination.id).isEqualTo("dialog1")
          assertThat(destination.name).isEqualTo("test.safeargs.Dialog1")
          assertThat(destination.arguments).hasSize(1)
          assertThat(destination.actions).hasSize(1)
          destination.arguments[0].let { argument ->
            assertThat(argument.type).isEqualTo("long")
            assertThat(argument.name).isEqualTo("arg1")
            assertThat(argument.defaultValue).isEqualTo("1234")
          }
          destination.actions[0].let { action ->
            assertThat(action.id).isEqualTo("action_dialog1_to_fragment2")
            assertThat(action.resolveDestination()).isEqualTo("fragment2")
          }
        }
      }

      assertThat(nested.id).isEqualTo("nested_nav")
      assertThat(nested.toDestination()!!.name).isEqualTo(".NestedNav")
      assertThat(nested.actions).isEmpty()
      assertThat(nested.startDestination).isEqualTo("fragment3")

      assertThat(nested.potentialDestinations).hasSize(2)
      nested.potentialDestinations[0].toDestination()!!.let { destination ->
        assertThat(destination.id).isEqualTo("activity2")
        assertThat(destination.name).isEqualTo("test.safeargs.Activity2")
        assertThat(destination.arguments).hasSize(2)
        assertThat(destination.actions).hasSize(1)
        destination.arguments[0].let { argument ->
          assertThat(argument.type).isEqualTo("string")
          assertThat(argument.name).isEqualTo("arg1")
          assertThat(argument.defaultValue).isEqualTo("placeholder")
        }
        destination.arguments[1].let { argument ->
          assertThat(argument.type).isEqualTo("boolean")
          assertThat(argument.name).isEqualTo("arg2")
          assertThat(argument.defaultValue).isNull()
        }
        destination.actions[0].let { action ->
          assertThat(action.id).isEqualTo("action_activity2_to_activity1")
          assertThat(action.resolveDestination()).isEqualTo("activity1")
        }
      }
      nested.potentialDestinations[1].toDestination()!!.let { destination ->
        assertThat(destination.id).isEqualTo("fragment3")
        assertThat(destination.name).isEqualTo("test.safeargs.Fragment3")
        assertThat(destination.arguments).hasSize(2)
        assertThat(destination.actions).hasSize(1)
        destination.arguments[0].let { argument ->
          assertThat(argument.type).isEqualTo("float")
          assertThat(argument.name).isEqualTo("arg1")
          assertThat(argument.defaultValue).isNull()
        }
        destination.arguments[1].let { argument ->
          assertThat(argument.type).isEqualTo("integer")
          assertThat(argument.name).isEqualTo("arg2")
          assertThat(argument.defaultValue).isNull()
        }
        destination.actions[0].let { action ->
          assertThat(action.id).isEqualTo("action_fragment3_to_fragment1")
          assertThat(action.resolveDestination()).isEqualTo("fragment1")
        }
      }
    }

    // Verify all three destinations can be found from the root
    assertThat(data.resolvedDestinations.map { it.id })
      .containsExactly("top_level_nav", "activity1", "fragment1", "fragment2", "nested_nav", "activity2", "fragment3", "double_nested_nav",
                       "dialog1")

    verifySerializationLogic(navXmlIndex.valueExternalizer, data)
  }

  @Test
  fun navigationIdIsOptional() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)

    val file = fixture.addFileToProject(
      "navigation/main.xml",
      //language=XML
      """
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:app="http://schemas.android.com/apk/res-auto"
                  xmlns:tools="http://schemas.android.com/tools"
                  app:startDestination="@id/fragment1">

          <fragment android:id="@+id/fragment1"
                    android:name="test.safeargs.Fragment1"
                    tools:layout="@layout/fragment1" />
      </navigation>
    """.trimIndent()).virtualFile

    val navXmlIndex = NavXmlIndex()
    val map = navXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    assertThat(map).hasSize(1)

    val data = map.values.first()
    assertThat(data.root.id).isNull()
    assertThat(data.root.startDestination).isEqualTo("fragment1")

    verifySerializationLogic(navXmlIndex.valueExternalizer, data)
  }

  @Test
  fun customTagsAreTreatedAsPotentialDestinations() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)

    val file = fixture.addFileToProject(
      "navigation/main.xml",
      //language=XML
      """
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:app="http://schemas.android.com/apk/res-auto"
                  xmlns:tools="http://schemas.android.com/tools"
                  app:startDestination="@id/fragment1">

          <fragment android:id="@+id/fragment1"
                    android:name="test.safeargs.Fragment1"
                    tools:layout="@layout/fragment1" />

          <customDestination android:id="@+id/custom1"
                             android:name="test.safeargs.Custom1"
                             tools:layout="@layout/custom1" />

          <unknownTag /> <!-- Probably breaks compiling but shouldn't break indexing -->
          
      </navigation>
    """.trimIndent()).virtualFile

    val navXmlIndex = NavXmlIndex()
    val map = navXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    assertThat(map).hasSize(1)

    val data = map.values.first()
    assertThat(data.root.potentialDestinations).hasSize(3)
    // unknownTag, though potential, doesn't meet destination requirements, so it is stripped out at this time
    assertThat(data.root.potentialDestinations.mapNotNull { it.toDestination()?.id }).containsExactly("fragment1", "custom1")

    verifySerializationLogic(navXmlIndex.valueExternalizer, data)
  }

  @Test
  fun camelCaseIdsInNavigationTagAreSupported() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)

    val file = fixture.addFileToProject(
      "navigation/main.xml",
      //language=XML
      """
      <!-- Recommend syntax is "camel_case_graph" but "camelCaseGraph" works too -->
      <navigation xmlns:android="http://schemas.android.com/apk/res/android"
                  xmlns:app="http://schemas.android.com/apk/res-auto"
                  xmlns:tools="http://schemas.android.com/tools"
                  android:id="@+id/camelCaseGraph"
                  app:startDestination="@id/fragment">

          <fragment android:id="@+id/fragment"
                    android:name="test.safeargs.Fragment"
                    tools:layout="@layout/fragment" />

      </navigation>
    """.trimIndent()).virtualFile

    val navXmlIndex = NavXmlIndex()
    val map = navXmlIndex.indexer.map(FileContentImpl.createByFile(file))

    assertThat(map).hasSize(1)

    val data = map.values.first()
    data.root.toDestination()!!.let { dest ->
      assertThat(dest.id).isEqualTo("camelCaseGraph")
      assertThat(dest.name).isEqualTo(".CamelCaseGraph")
    }
    verifySerializationLogic(navXmlIndex.valueExternalizer, data)
  }

  @Test
  fun indexRecoversFromUnrelatedXml() {
    StudioFlags.NAV_SAFE_ARGS_SUPPORT.override(true)

    val file = fixture.addFileToProject(
      "navigation/main.xml",
      //language=XML
      """
        <?xml version="1.0" encoding="utf-8"?>
        <manifest xmlns:android="http://schemas.android.com/apk/res/android"
              package="com.example.nav.safeargs">
            <application android:label="Safe Args Test" />
        </manifest>
    """.trimIndent()).virtualFile

    val navXmlIndex = NavXmlIndex()
    assertThat(navXmlIndex.inputFilter.acceptInput(file)).isEqualTo(true)

    val map = navXmlIndex.indexer.map(FileContentImpl.createByFile(file))
    assertThat(map).isEmpty()
  }

  private fun verifySerializationLogic(valueExternalizer: DataExternalizer<NavXmlData>, data: NavXmlData) {
    val bytesOut = ByteArrayOutputStream()
    DataOutputStream(bytesOut).use { valueExternalizer.save(it, data) }

    val bytesIn = ByteArrayInputStream(bytesOut.toByteArray())
    val dataCopy = DataInputStream(bytesIn).use { valueExternalizer.read(it) }
    assertThat(dataCopy).isEqualTo(data)
  }
}
