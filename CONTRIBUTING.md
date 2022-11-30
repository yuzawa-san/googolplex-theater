Feel free to contribute your own original work for bugs and enhancements. This is intended to be minimalist and easy to set up, so advanced features are not the goal here.

This is a side project, so the maintainer provides no guarantee to the speed at which submissions can be accepted given the need to test this with hardware.
A good faith effort was made to test with a "fake" Chromecast in the unit test, but nothing beats testing with real devices on a real network. Please attempt to try to test on your own hardware if possible and remark such experimentation in the PR.

NOTE: It may be necessary to compile protobuf manually prior to IDE development:
```
./gradlew generateProto
```

More information: https://github.com/yuzawa-san/.github/blob/master/CONTRIBUTING.md