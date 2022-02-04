Feature: Resilience
	The application should maintain reliable connections with the registered devices.

	Scenario Outline: Reconnect when home screen detected.
		Given a registered device with url "https://example.com/a"
		When the device has home screen
		Then the device loaded url "https://example.com/a"
		And the device connected 2 times
	Scenario Outline: Reconnect when connection closed.
		Given a registered device with url "https://example.com/a"
		When the device has connection closed
		Then the device loaded url "https://example.com/a"
		And the device connected 2 times
	Scenario Outline: Reconnect when pings are lost.
		Given a registered device with url "https://example.com/a"
		When the device has lost pings
		Then the device loaded url "https://example.com/a"
		And the device connected 2 times
	Scenario Outline: Reconnect when device sends broken messages.
		Given a registered device with url "https://example.com/a"
		When the device has broken messages
		Then the device loaded url "https://example.com/a"
		And the device connected 2 times
