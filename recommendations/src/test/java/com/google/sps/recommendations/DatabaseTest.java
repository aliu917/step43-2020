/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.sps.recommendations;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(MockitoJUnitRunner.Silent.class)
public class DatabaseTest {

  private static Logger log = LoggerFactory.getLogger(DatabaseTest.class);
  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());
  private DatastoreService datastore;

  /** Set up datastore service testing instance. */
  @Before
  public void setUp() {
    helper.setUp();
    datastore = DatastoreServiceFactory.getDatastoreService();
  }

  /** Tear down datastore service testing instance. */
  @After
  public void tearDown() {
    helper.tearDown();
  }

  /** Test groceri database seeding. */
  @Test
  public void testDatabaseReset() throws Exception {
    databaseHelper("groceri");
  }

  /** Test Frac-groceri database seeding. */
  @Test
  public void testFracDatabaseReset() throws Exception {
    databaseHelper("Frac-groceri");
  }

  /**
   * Method to assist in setting the database with seeded values and checking that the corrent
   * number of items now populate the database.
   *
   * @param category The category to reset and query in the database
   */
  private void databaseHelper(String category) throws Exception {
    DatabaseUtils.resetDatabase(datastore);
    Query query = new Query(category);
    List<Entity> results = datastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
    assertEquals(5, results.size());
  }
}
