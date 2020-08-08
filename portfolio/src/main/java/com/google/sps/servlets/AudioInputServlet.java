// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.servlets;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.users.UserService;
import com.google.appengine.api.users.UserServiceFactory;
import com.google.cloud.translate.*;
import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.sps.agents.TranslateAgent;
import com.google.sps.data.DialogFlowClient;
import com.google.sps.data.Output;
import com.google.sps.data.RecommendationsClient;
import com.google.sps.utils.AgentUtils;
import com.google.sps.utils.AudioUtils;
import com.google.sps.utils.SpeechUtils;
import java.io.IOException;
import java.util.*;
import javax.servlet.ServletInputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Servlet that takes in audio stream and retrieves * user input string to display. */
@WebServlet("/audio-input")
public class AudioInputServlet extends HttpServlet {

  private DatastoreService datastore = DatastoreServiceFactory.getDatastoreService();
  private UserService userService = UserServiceFactory.getUserService();
  private RecommendationsClient recommender = new RecommendationsClient();

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
    response.setContentType("application/json");
    response.setCharacterEncoding("UTF-8");

    // Convert input stream into bytestring for DialogFlow API input
    ServletInputStream stream = request.getInputStream();
    ByteString bytestring = ByteString.readFrom(stream);
    String sessionID = request.getParameter("session-id");
    String language = request.getParameter("language");
    Output output = null;

    if (language.equals("English")) {
      output = handleEnglishQuery(bytestring, null, sessionID);
    } else {
      try {
        output = handleForeignQuery(bytestring, language, sessionID);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    // Convert to JSON string
    String json = new Gson().toJson(output);
    response.getWriter().write(json);
  }

  private Output handleEnglishQuery(ByteString bytestring, String languageCode, String sessionID) {
    DialogFlowClient result = AudioUtils.detectIntentStream(bytestring);
    if (result == null) {
      return null;
    }
    return AgentUtils.getOutput(
        result, languageCode, userService, datastore, sessionID, recommender);
  }

  private Output handleForeignQuery(ByteString bytestring, String language, String sessionID) {
    String languageCode = AgentUtils.getLanguageCode(language);
    String userInput =
        AudioUtils.detectSpeechLanguage(bytestring.toByteArray(), languageCode);
    String englishLanguageCode = AgentUtils.getLanguageCode("English");
    // Google Translate API - convert userInput from language to English
    Translation inputTranslation =
        TranslateAgent.translate(userInput, languageCode, englishLanguageCode);

    String translatedInputText = inputTranslation.getTranslatedText();
    ByteString inputByteString = null;
    try {
      inputByteString = SpeechUtils.synthesizeText(translatedInputText, languageCode);
    } catch (Exception e) {
      e.printStackTrace();
    }

    DialogFlowClient englishResult =
        (new TextInputServlet()).detectIntentStream(translatedInputText, englishLanguageCode);
    Output englishOutput =
          AgentUtils.getOutput(
              englishResult, languageCode, userService, datastore, sessionID, recommender);
    // Google Translate API - convert input and fulfillment to appropriate language
    String fulfillment = englishOutput.getFulfillmentText();
    String fulfillmentTranslation =
        TranslateAgent.translate(fulfillment, englishLanguageCode, languageCode)
            .getTranslatedText();
    byte[] byteArray = AgentUtils.getByteStringToByteArray(fulfillmentTranslation, languageCode);
    System.out.println("user input: " + userInput);
    System.out.println("fulfillment output: " + fulfillmentTranslation);
    Output languageOutput =
        new Output(
            userInput,
            fulfillmentTranslation,
            byteArray,
            null,
            englishOutput.getRedirect(),
            englishResult.getIntentName());
    return languageOutput;
  }
}
