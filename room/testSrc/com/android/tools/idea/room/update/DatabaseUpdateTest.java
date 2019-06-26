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

import com.android.tools.idea.room.bundle.DatabaseBundle;
import com.android.tools.idea.room.bundle.EntityBundle;
import com.android.tools.idea.room.bundle.FieldBundle;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class DatabaseUpdateTest {
  private DatabaseBundle db1;
  private DatabaseBundle db2;
  private DatabaseBundle db3;
  private DatabaseBundle db4;

  @Before
  public void setUp() {
    FieldBundle field1 = createFieldBundle("column1");
    FieldBundle field2 = createFieldBundle("column2");
    FieldBundle field3 = createFieldBundle("column3");

    EntityBundle entity1 = createEntityBundle("table1", Arrays.asList(field1, field2));
    EntityBundle entity2 = createEntityBundle("table2",  Arrays.asList(field1, field2, field3));
    EntityBundle entity3 = createEntityBundle("table3", Arrays.asList(field1, field3));
    EntityBundle modifiedEntity1 = createEntityBundle("table1", Arrays.asList(field1, field3));

    db1 = createDatabaseBundle(1, Arrays.asList(entity1, entity2));
    db2 = createDatabaseBundle(2, Arrays.asList(entity1, entity2, entity3));
    db3 = createDatabaseBundle(3, Arrays.asList(entity1, entity3));
    db4 = createDatabaseBundle(4, Arrays.asList(modifiedEntity1, entity2));
  }

  @Test
  public void testSameDatabase() {
    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db1, db1);
    assertThat(databaseUpdate.getDiff()).isEmpty();
  }

  @Test
  public void testAddEntity() {
    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db1, db2);
    assertEquals("Table table3 was added\n",
                 databaseUpdate.getDiff());
  }

  @Test
  public void testDeleteEntity() {
    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db2, db3);
    assertEquals("Table table2 was deleted\n",
                 databaseUpdate.getDiff());
  }

  @Test
  public void testModifyEntity() {
    DatabaseUpdate databaseUpdate = new DatabaseUpdate(db1, db4);
    assertEquals("Table table1 was modified:\n"
                 + "\tDeleted column column2\n"
                 + "\tAdded column column3\n",
                 databaseUpdate.getDiff());
  }
}
