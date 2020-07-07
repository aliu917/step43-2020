package com.google.sps.agents;

// Imports the Google Cloud client library
import com.google.protobuf.Value;
import java.text.DecimalFormat;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Tip Agent calculates tip for given parameters, only supports USD deimal formatting for now */
public class Tip implements Agent {

  private static Logger log = LoggerFactory.getLogger(Tip.class);

  private final String intentName;
  private String searchText;
  private Double tipAmount;
  private Double tipAmountPerPerson;
  private String tipPercentageString = null;
  private Double tipPercentageDouble = null;
  private Double amountWithoutTip = null;
  private String currency = null;
  private String currencySymbol = "";
  private Double peopleNumber = null;
  private String fulfillment = null;
  private String display = null;
  private String redirect = null;

  public Tip(String intentName, Map<String, Value> parameters) {
    this.intentName = intentName;
    setParameters(parameters);
  }

  @Override
  public void setParameters(Map<String, Value> parameters) {
    log.info(String.valueOf(parameters));
    tipPercentageString = String.valueOf(parameters.get("tip-percentage").getStringValue());
    amountWithoutTip = parameters.get("amount-without-tip").getNumberValue();
    currency = parameters.get("currency").getStringValue();
    peopleNumber = parameters.get("people-number").getNumberValue();

    if (currency.equals("USD")) {
      currencySymbol = "$";
    }

    if (!tipPercentageString.equals("")) {
      // Convert String to Doubles
      tipPercentageString = tipPercentageString.substring(0, tipPercentageString.length() - 1);
      tipPercentageDouble = Double.valueOf(tipPercentageString);
      tipPercentageDouble = tipPercentageDouble / 100;
      log.info(tipPercentageString);
      log.info(String.valueOf(tipPercentageDouble));

      tipAmount = tipPercentageDouble * amountWithoutTip;
      DecimalFormat formatTipAmount = new DecimalFormat("#.##");
      tipAmount = Double.valueOf(formatTipAmount.format(tipAmount));
      log.info(String.valueOf(tipAmount));

      // Tip without number of people
      if (String.valueOf(peopleNumber).equals("0.0")) {
        if (currencySymbol.equals("")) {
          fulfillment = "The total tip is " + String.valueOf(tipAmount) + " " + currency;
        } else {
          fulfillment = "The total tip is " + currencySymbol + String.valueOf(tipAmount);
        }

      } else {
        // Tip with percentage and people
        tipAmountPerPerson = tipAmount / peopleNumber;
        DecimalFormat formatTipAmountPerPerson = new DecimalFormat("#.##");
        tipAmountPerPerson = Double.valueOf(formatTipAmountPerPerson.format(tipAmountPerPerson));

        if (currencySymbol.equals("")) {
          fulfillment =
              "The total tip is "
                  + String.valueOf(tipAmount)
                  + " "
                  + currency
                  + ", coming out to "
                  + String.valueOf(tipAmountPerPerson)
                  + " "
                  + currency
                  + " per person";
        } else {
          fulfillment =
              "The total tip is "
                  + currencySymbol
                  + String.valueOf(tipAmount)
                  + ", coming out to "
                  + currencySymbol
                  + String.valueOf(tipAmountPerPerson)
                  + " per person";
        }
      }
    }
  }

  @Override
  public String getOutput() {
    log.info(fulfillment);
    return fulfillment;
  }

  @Override
  public String getDisplay() {
    return display;
  }

  @Override
  public String getRedirect() {
    return redirect;
  }
}
