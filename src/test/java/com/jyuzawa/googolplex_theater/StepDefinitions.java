package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class StepDefinitions {
  private String today;
  private String actualAnswer;

  @Given("today is {string}")
  public void today_is(String today) {
    this.today = today;
  }

  @When("I ask whether it's Friday yet")
  public void i_ask_whether_it_s_Friday_yet() {
    actualAnswer = GoogolplexTheater.isItFriday(today);
  }

  @When("I ask whether it's Monday yet")
  public void i_ask_whether_it_s_Monday_yet() {
    actualAnswer = GoogolplexTheater.isItMonday(today);
  }

  @Then("I should be told {string}")
  public void i_should_be_told(String expectedAnswer) {
    assertEquals(expectedAnswer, actualAnswer);
  }
}
