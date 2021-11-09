Feature: Is it Monday yet?
  Everybody wants to know when it's Friday

  # commment does here
  Scenario Outline: Today is or is not Monday
    Given today is "<day>"
    When I ask whether it's Monday yet
    Then I should be told "<answer>"

  Examples:
    | day            | answer |
    | Monday         | TGIF   |
    | Sunday         | Nope   |
    | anything else! | Nope   |