Feature: Detect device configuration and service discovery changes
	When the device configuration updates, the altered devices should refresh.
	When the service discovery finds devices, it should automatically connect.

	Scenario Outline: Connected device is left alone when configuration is not modified.
		Given a registered device with url "https://example.com/a"
		When the device url is set to "https://example.com/a"
		Then the device connected 1 times
	Scenario Outline: Device refreshes when config is modified.
		Given a registered device with url "https://example.com/a"
		When the device url is set to "https://example.com/b"
		Then the device loaded url "https://example.com/b"
		And the device connected 2 times
	Scenario Outline: Device disconnects when device is removed from configuration.
		Given a registered device with url "https://example.com/a"
		When the device is unregistered
		Then the device connected 1 times
		And the device is not connected
	Scenario Outline: Device connects when device is added from configuration.
		Given an unregistered device
		When the device url is set to "https://example.com/a"
		Then the device loaded url "https://example.com/a"
		And the device connected 1 times
