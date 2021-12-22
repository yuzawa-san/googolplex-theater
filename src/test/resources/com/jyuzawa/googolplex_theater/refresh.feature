Feature: Manual Refresh
  The connected devices should be able to be manually refreshed.

  Scenario Outline: Force refresh of a single device.
    Given a registered device with url "https://example.com/a"
    When the device is refreshed
    Then the device loaded url "https://example.com/a"
    And the device connected 2 times
  Scenario Outline: Force refresh all devices.
    Given a registered device with url "https://example.com/a"
    When the devices are all refreshed
    Then the device loaded url "https://example.com/a"
    And the device connected 2 times
  Scenario Outline: Force refresh of one device does not affect other devices.
    Given a registered device with url "https://example.com/a"
    When another device is refreshed
    Then the device connected 1 times