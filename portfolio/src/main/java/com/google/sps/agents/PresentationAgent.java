package com.google.sps.agents;

// Imports the Google Cloud client library

/**
 * Presentation Agent
 *
 * <p>Handles a hardcoded presentation intent for the beginning of our presentation.
 */
public class PresentationAgent implements Agent {

  private String presentationLink =
      "https://docs.google.com/presentation/d/1-9sG6dZ6CBAmGoq2EGX-JmCXZrnz8eX7q_758kvIAd8/edit#slide=id.g8efff64a56_1_0";

  @Override
  public String getOutput() {
    return "Okay, let's do it. Redirecting to presentation slides.";
  }

  @Override
  public String getDisplay() {
    return null;
  }

  @Override
  public String getRedirect() {
    return presentationLink;
  }
}
