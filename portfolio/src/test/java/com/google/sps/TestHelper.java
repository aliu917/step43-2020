/*
 * Copyright 2019 Google Inc.
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

package com.google.sps.servlets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.api.datastore.FetchOptions;
import com.google.appengine.api.datastore.Query;
import com.google.appengine.api.datastore.Query.CompositeFilter;
import com.google.appengine.api.datastore.Query.CompositeFilterOperator;
import com.google.appengine.api.datastore.Query.Filter;
import com.google.appengine.api.datastore.Query.FilterOperator;
import com.google.appengine.api.datastore.Query.FilterPredicate;
import com.google.appengine.api.datastore.Query.SortDirection;
import com.google.appengine.api.users.User;
import com.google.appengine.api.users.UserService;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.cloud.dialogflow.v2.SessionsClient;
import com.google.gson.Gson;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import com.google.sps.data.*;
import com.google.sps.utils.*;
import java.io.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.servlet.http.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestHelper {

  private static Logger log = LoggerFactory.getLogger(TestHelper.class);

  @Mock DialogFlowClient dialogFlowMock;
  @Mock UserService userServiceMock;
  @Mock RecommendationsClient recommenderMock;

  @InjectMocks TextInputServlet textInputServlet;

  HttpServletRequest request;
  HttpServletResponse response;
  TextInputServlet servlet;
  DatastoreService customDatastore;
  String sessionID;

  private final LocalServiceTestHelper helper =
      new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());

  /** Default constructor for TestHelper that sets up testing environment and empty mocks. */
  public TestHelper() throws URISyntaxException {
    helper.setUp();
    customDatastore = DatastoreServiceFactory.getDatastoreService();
    userServiceMock = mock(UserService.class);
    request = mock(HttpServletRequest.class);
    response = mock(HttpServletResponse.class);
    dialogFlowMock = mock(DialogFlowClient.class);
    recommenderMock = mock(RecommendationsClient.class);
    doNothing().when(recommenderMock).setUserID(any(String.class));
    doNothing()
        .when(recommenderMock)
        .saveAggregateListData(
            any(String.class), any(List.class), any(Boolean.class), any(Boolean.class));
    // when(recommenderMock.setUserID(any(String.class))).doNothing();
    // when(recommenderMock.saveAggregateListData(any(String.class), any(List.class),
    // any(Boolean.class))).doNothing();
    when(recommenderMock.getPastRecommendations(any(String.class)))
        .thenReturn(new ArrayList<Pair<String, Double>>());
    when(recommenderMock.getUserRecommendations(any(String.class)))
        .thenReturn(new ArrayList<Pair<String, Double>>());
    servlet = new TestableTextInputServlet();
    sessionID = "fallbackTestingID";
    setLoggedIn();
  }

  /**
   * TestHelper constructor that runs end-to-end testing of Dialogflow and agents. Use to test both
   * Dialogflow's ability to detect the proper intent and verify agent fulfillment. Default has a
   * logged in user. (Must set logged out user manually using set method).
   *
   * @param inputText Text input of user's intent sent to Dialogflow.
   */
  public TestHelper(String inputText) throws URISyntaxException {
    this();
    servlet = new TestableDFTextInputServlet();
    setInputText(inputText);
  }

  /**
   * TestHelper constructor that mocks Dialogflow with expected intent detection. Use to test proper
   * agent fulfillment given the correct Dialogflow output.
   *
   * @param inputText Text input of user's intent sent to Dialogflow.
   * @param parameters String json that consists of expected parameters identified by Dialogflow.
   * @param intentName String name of expected intent identified by Dialogflow.
   */
  public TestHelper(String inputText, String parameters, String intentName)
      throws InvalidProtocolBufferException, URISyntaxException {
    this();
    servlet = new TestableTextInputServlet();
    setParameters(inputText, parameters, intentName);
  }

  /**
   * TestHelper constructor that mocks Dialogflow in case of insufficient intent detection. Use to
   * test proper agent fulfillment given incomplete required parameters.
   *
   * @param inputText Text input of user's intent sent to Dialogflow.
   * @param parameters String json that consists of expected parameters identified by Dialogflow.
   * @param intentName String name of expected intent identified by Dialogflow.
   * @param allParamsPresent Boolean indicating if all required parameters are present.
   */
  public TestHelper(
      String inputText, String parameters, String intentName, Boolean allParamsPresent)
      throws InvalidProtocolBufferException, URISyntaxException {
    this(inputText, parameters, intentName);
    setParamsPresent(allParamsPresent);
  }

  /**
   * TestHelper constructor that mocks Dialogflow in case of insufficient intent detection and sets
   * up session ID for current "session". Use to test proper agent fulfillment given a sessionID.
   *
   * @param inputText Text input of user's intent sent to Dialogflow.
   * @param parameters String json that consists of expected parameters identified by Dialogflow.
   * @param intentName String name of expected intent identified by Dialogflow.
   * @param id String unique ID for current session
   */
  public TestHelper(String inputText, String parameters, String intentName, String id)
      throws InvalidProtocolBufferException, URISyntaxException {
    this(inputText, parameters, intentName);
    sessionID = id;
  }

  /**
   * Gets the output object created by agent fulfillment import at the end of back-end process.
   *
   * @return Output object containing all output audio, text, and display information.
   */
  public Output getOutput() throws IOException {
    StringWriter stringWriter = new StringWriter();
    PrintWriter writer = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(writer);

    servlet.doPost(request, response);

    verify(request, atLeast(1)).getParameter("request-input");
    writer.flush();
    Output output = new Gson().fromJson(stringWriter.toString(), Output.class);
    return output;
  }

  /**
   * Sets the input text to mock http request.
   *
   * @param inputText Text input of user's intent sent to Dialogflow.
   */
  public void setInputText(String inputText) {
    when(request.getParameter("request-input")).thenReturn(inputText);
  }

  /**
   * Sets the general return parameters to mock dialogflow.
   *
   * @param inputText Text input of user's intent sent to Dialogflow.
   * @param parameters String json that consists of expected parameters identified by Dialogflow.
   * @param intentName String name of expected intent identified by Dialogflow.
   */
  public void setParameters(String inputText, String parameters, String intentName)
      throws InvalidProtocolBufferException {
    setInputText(inputText);
    setSessionID();
    Map<String, Value> map = BookAgentServlet.stringToMap(parameters);
    when(dialogFlowMock.getParameters()).thenReturn(map);
    when(dialogFlowMock.getIntentName()).thenReturn(intentName);
    when(dialogFlowMock.getQueryText()).thenReturn(inputText);
    when(dialogFlowMock.getIntentConfidence()).thenReturn((float) 1.0);
    when(dialogFlowMock.getFulfillmentText()).thenReturn("");
    when(dialogFlowMock.getAllRequiredParamsPresent()).thenReturn(true);
  }

  /**
   * Sets the mock returned for dialogflow all parameters present.
   *
   * @param allParamsPresent Boolean indicating if all required parameters are present.
   */
  public void setParamsPresent(boolean allParamsPresent) {
    when(dialogFlowMock.getAllRequiredParamsPresent()).thenReturn(allParamsPresent);
  }

  /**
   * Sets the mocks returned for a default logged-in user. Default user has: email:
   * "test@example.com" user id: "1"
   */
  public void setLoggedIn() {
    setUser("test@example.com", "1");
  }

  /** Sets the user service mock to return a logged-out user. */
  public void setLoggedOut() {
    when(userServiceMock.isUserLoggedIn()).thenReturn(false);
  }

  /** Sets the sessionID for testing */
  public void setSessionID() {
    when(request.getParameter("session-id")).thenReturn(sessionID);
  }

  /**
   * Creates a customized user.
   *
   * @param email String containing the user's email.
   * @param id String containing the user's id number.
   */
  public void setUser(String email, String id) {
    when(userServiceMock.isUserLoggedIn()).thenReturn(true);
    when(userServiceMock.getCurrentUser()).thenReturn(new User(email, "authDomain", id));
  }

  /**
   * Populates customizes datastore with desired string comments.
   *
   * @param comments List of strings containing comments to be stored in custom database.
   */
  public void setCustomDatabase(List<String> comments) {
    setCustomDatabase(comments, (new Date()).getTime());
  }

  /**
   * Populates customizes datastore with desired string comments.
   *
   * @param comments List of strings containing comments to be stored in custom database.
   */
  public void setCustomDatabase(List<String> comments, long startTime) {
    int increment = 0;
    for (String comment : comments) {
      MemoryUtils.makeCommentEntity("1", customDatastore, comment, true, startTime + (increment++));
    }
  }

  /**
   * Populates customizes datastore with desired list attributes.
   *
   * @param comments List of strings containing comments to be stored in custom database.
   */
  public void setCustomDatabase(String listName, ArrayList<String> items, long startTime) {
    MemoryUtils.makeListEntity(customDatastore, "1", items, listName, startTime);
  }

  /**
   * Retrieves a list of entity objects for the default user from the given query for testing
   * purposes to ensure that result in datastore match the expected.
   *
   * @param category String containing the type of entity we are querying for.
   * @return A list of entity objects returned by datastore.
   */
  public List<Entity> fetchDatastoreEntities(String category) {
    return fetchDatastoreEntities(category, "1");
  }

  /**
   * Retrieves a list of entity objects for all users from the given query for testing purposes to
   * ensure that result in datastore match the expected.
   *
   * @param category String containing the type of entity we are querying for.
   * @return A list of entity objects returned by datastore.
   */
  public List<Entity> fetchDatastoreAllUsers(String category) {
    Query query = new Query(category);
    return customDatastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
  }

  /**
   * Retrieves a list of entity objects from the given query for testing purposes to ensure that
   * result in datastore match the expected.
   *
   * @param category String containing the type of entity we are querying for.
   * @param userID String containing current user's unique ID users
   * @return A list of entity objects returned by datastore.
   */
  public List<Entity> fetchDatastoreEntities(String category, String userID) {
    Filter filter = new FilterPredicate("userID", FilterOperator.EQUAL, userID);
    return fetchDatastoreEntities(category, filter);
  }

  /**
   * Retrieves a list of entity objects from the given query for testing purposes to ensure that
   * result in datastore match the expected.
   *
   * @param category String containing the type of entity we are querying for.
   * @param filter Filter for query results.
   * @return A list of entity objects returned by datastore.
   */
  public List<Entity> fetchDatastoreEntities(String category, Filter filter) {
    Query query =
        new Query(category).setFilter(filter).addSort("timestamp", SortDirection.DESCENDING);
    return customDatastore.prepare(query).asList(FetchOptions.Builder.withDefaults());
  }

  /** Helper method for list database verification. */
  public void checkDatabaseItems(int size, String listName, List<String> expectedItems) {
    checkDatabaseItems(size, "1", listName, expectedItems);
  }

  /** Helper method for list database verification. */
  public void checkDatabaseItems(
      int size, String listName, List<String> expectedItems, boolean isEmpty) {
    checkDatabaseItems(size, "1", listName, expectedItems);
  }

  /**
   * Methods for database verification. Checks that database entries are equal to expected.
   *
   * @param size Number of items expected in user's list
   * @param userID String containing current user's unique ID
   * @param listName Name of the list being checked
   * @param expectedItems List of strings containing the name of all items expected to be in the
   *     user's list database
   */
  public void checkDatabaseItems(
      int size, String userID, String listName, List<String> expectedItems) {
    List<Entity> databaseQuery = fetchDatastoreEntities("List", userID);
    assertEquals(size, databaseQuery.size());
    Entity entity = databaseQuery.get(0);
    assertEquals(listName, (String) entity.getProperty("listName"));
    ArrayList<String> items = (ArrayList<String>) entity.getProperty("items");
    if (expectedItems.isEmpty()) {
      assertNull(items);
      return;
    }
    for (int i = 0; i < items.size(); i++) {
      assertEquals(expectedItems.get(i), items.get(i));
    }
  }

  /** Helper method for aggregate list database verification. */
  public void checkAggregate(
      String fetchName, List<String> expectedItems, List<Integer> expectedCounts) {
    checkAggregate(fetchName, "1", expectedItems, expectedCounts);
  }

  /**
   * Methods for aggregate database verification. Checks that database entries are equal to
   * expected.
   *
   * @param fetchName Category name used to fetch subcategory from datastore
   * @param userID String containing current user's unique ID
   * @param expectedItems List of strings containing the name of all items expected to be in the
   *     user's list database
   * @param expectedCount List of integers containing expected counts for each expected item of the
   *     corresponding index.
   */
  public void checkAggregate(
      String fetchName, String userID, List<String> expectedItems, List<Integer> expectedCounts) {
    Filter filter =
        new CompositeFilter(
            CompositeFilterOperator.AND,
            Arrays.asList(
                new FilterPredicate("userID", FilterOperator.EQUAL, userID),
                new FilterPredicate("listName", FilterOperator.EQUAL, fetchName)));
    List<Entity> databaseQuery = fetchDatastoreEntities(fetchName, filter);
    assertEquals(1, databaseQuery.size());
    Entity entity = databaseQuery.get(0);
    for (int i = 0; i < expectedItems.size(); i++) {
      assertEquals(
          (long) expectedCounts.get(i),
          (long) entity.getProperty(StemUtils.stemmed(expectedItems.get(i))));
    }
  }

  /** Helper method for fractional aggregate list database verification. */
  public void checkFracAggregate(
      String fetchName, List<String> expectedItems, List<Double> expectedCounts) {
    checkFracAggregate(fetchName, "1", expectedItems, expectedCounts);
  }

  /**
   * Methods for aggregate database verification. Checks that database entries are equal to
   * expected.
   *
   * @param fetchName Category name used to fetch subcategory from datastore
   * @param userID String containing current user's unique ID
   * @param expectedItems List of strings containing the name of all items expected to be in the
   *     user's list database
   * @param expectedCount List of doubles containing expected fractional values for each expected
   *     item of the corresponding index.
   */
  public void checkFracAggregate(
      String fetchName, String userID, List<String> expectedItems, List<Double> expectedCounts) {
    Filter filter =
        new CompositeFilter(
            CompositeFilterOperator.AND,
            Arrays.asList(
                new FilterPredicate("userID", FilterOperator.EQUAL, userID),
                new FilterPredicate("listName", FilterOperator.EQUAL, fetchName)));
    List<Entity> databaseQuery = fetchDatastoreEntities("Frac-" + fetchName, filter);
    assertEquals(1, databaseQuery.size());
    Entity entity = databaseQuery.get(0);
    for (int i = 0; i < expectedItems.size(); i++) {
      double itemFreq =
          entity.getProperty(StemUtils.stemmed(expectedItems.get(i))) == null
              ? 0.0
              : (double) entity.getProperty(StemUtils.stemmed(expectedItems.get(i)));
      assertEquals(expectedCounts.get(i), itemFreq, 0.001);
    }
  }

  /**
   * Populates a user list database with the given items and frequencies out of the total number of
   * lists created.
   *
   * @param userID String containing current user's unique ID
   * @param size Number of lists of the same name created by the user
   * @param items List of strings containing items to add to list containing the number of times
   *     added to past grocery lists.
   */
  public void makeUserList(String userID, int size, List<Pair<String, Integer>> items)
      throws InvalidProtocolBufferException, IOException {
    for (int i = 0; i < size; i++) {
      List<Pair<String, Integer>> itemsList = new ArrayList<>((List<Pair<String, Integer>>) items);
      final int temp = i;
      List<Pair<String, Integer>> filteredPairs =
          itemsList.stream().filter(e -> e.getValue() > temp).collect(Collectors.toList());
      List<String> filteredStrings =
          filteredPairs.stream().map(e -> e.getKey()).collect(Collectors.toList());
      String stringItems = String.join(", ", filteredStrings);
      createSingleGroceryList(userID, "make", stringItems);
      getOutput();
    }
  }

  /**
   * Runs one command for making a single grocery list
   *
   * @param userID String containing current user's unique ID
   * @param listAction Either "make" or "add" depending on the command user wants to make
   * @param items List of strings containing items to add to list containing the number of times
   *     added to past grocery lists.
   */
  public void createSingleGroceryList(String userID, String listAction, String items)
      throws InvalidProtocolBufferException {
    setUser("test@example.com", userID);
    setParameters(
        listAction + " a grocery list with " + items,
        "{\"list-name\":\"grocery\", "
            + "\"list-objects\":\""
            + items
            + "\","
            + "\"new-list\": \"\","
            + "\"generic-list\": \"\"}",
        "memory.list - " + listAction);
  }

  /**
   * Sets mock return parameters for past recommender.
   *
   * @param itemPairs List of item and frequency pairs to be returned by mock recommender.
   */
  public void setPastRecommendations(List<Pair<String, Double>> itemPairs)
      throws URISyntaxException {
    when(recommenderMock.getPastRecommendations(any(String.class))).thenReturn(itemPairs);
  }

  /**
   * Sets mock return parameters for user recommender.
   *
   * @param itemPairs List of item and frequency pairs to be returned by mock recommender.
   */
  public void setUserRecommendations(List<Pair<String, Double>> itemPairs)
      throws URISyntaxException {
    when(recommenderMock.getUserRecommendations(any(String.class))).thenReturn(itemPairs);
  }

  /** Removes stored items in datastore instance. */
  public void tearDown() {
    helper.tearDown();
  }

  private class TestableTextInputServlet extends TextInputServlet {
    @Override
    public DialogFlowClient createDialogFlow(
        String text, String languageCode, SessionsClient sessionsClient) {
      return dialogFlowMock;
    }

    @Override
    public UserService createUserService() {
      return userServiceMock;
    }

    @Override
    public DatastoreService createDatastore() {
      return customDatastore;
    }

    @Override
    public RecommendationsClient createRecommendationsClient() {
      return recommenderMock;
    }
  }

  private class TestableDFTextInputServlet extends TextInputServlet {
    @Override
    public UserService createUserService() {
      return userServiceMock;
    }
  }
}
