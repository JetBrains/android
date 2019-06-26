/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.room.update;

import static com.android.tools.idea.room.update.TestUtils.*;
import static com.google.common.truth.Truth.*;
import static org.junit.Assert.*;

import com.android.tools.idea.room.bundle.EntityBundle;
import com.android.tools.idea.room.bundle.FieldBundle;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class EntityUpdateTest {
  private EntityBundle entity1;
  private EntityBundle entity2;
  private EntityBundle entity3;

  @Before
  public void setUp() {
    FieldBundle field1 =  createFieldBundle("column1");
    FieldBundle field2 = createFieldBundle("column2");
    FieldBundle field3 = createFieldBundle("column3");

    entity1 = createEntityBundle("table1", Arrays.asList(field1, field2));
    entity2 = createEntityBundle("table2",  Arrays.asList(field1, field2, field3));
    entity3 = createEntityBundle("table3", Arrays.asList(field1, field3));
  }

  @Test
  public void testIdenticalEntitiesReturnNoDiff() {
    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity1);
    assertThat(entityUpdate.getDiff()).isEmpty();
  }

  @Test
  public void testAddColumn() {
    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity2);
    assertEquals("Table table1 was modified:\n"
                 + "\tAdded column column3\n",
                 entityUpdate.getDiff());
  }

  @Test
  public void testDeleteColumn() {
    EntityUpdate entityUpdate = new EntityUpdate(entity2, entity1);
    assertEquals("Table table2 was modified:\n"
                 + "\tDeleted column column3\n",
                 entityUpdate.getDiff());
  }

  @Test
  public void testAddAndDeleteColumn() {
    EntityUpdate entityUpdate = new EntityUpdate(entity1, entity3);
    assertEquals("Table table1 was modified:\n"
                 + "\tDeleted column column2\n\tAdded column column3\n",
                 entityUpdate.getDiff());
  }
}
