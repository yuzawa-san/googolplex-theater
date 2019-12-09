# googolplex-theater

Maintain multiple Chromecast screens on you network without using your browser.
Ideal for digital signage applications.
Originally developed to display statistics dashboards.
Persistence provided using smart reconnect capabilities.

There are several tools and libraries out there, but this project is intended to be very minimalist.
There is no UI or backing database, rather there is a simple JSON config file which is watched for changes.
The JSON configuration is conveyed to the receiver application, which by default accepts url to display in an iframe.
The receiver application can be customized easily to suit your needs. 

## Requirements

* Java 8 or later. The [gradle wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) is used to build this application.
* The application must run on the same network as your Chromecasts.
* Multicast DNS must work on your network and on the machine you run the application on.
* For custom receivers: a [Chromecast developer registration](https://developers.google.com/cast/docs/registration#RegisterApp) and configured developer [devices](https://cast.google.com/publish)
* IMPORTANT: URLs must be https and must not [deny framing](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/X-Frame-Options) 

## Compilation

```
./gradlew shadowJar
```

## Configuration

A sample cast configuration is provided in `SAMPLE_cast_config.json`.
Copy over to `cast_config.json` and edit accordingly.
The location of your configuration can be customized using a command line argument.
The file is automatically watched for changes.
Some example use cases involve using cron and putting your config under version control and pulling from origin periodically, or downloading from S3/web, or updating using rsync/scp.

## Running

```
java -jar build/libs/googolplex-theater-all.jar
```
will show all runtime options.

```
java -jar build/libs/googolplex-theater-all.jar -a APP_ID
```
will run the application for the registered APP_ID.

## Contributions

This is a side project, so I will accept contributions, but provide no guarantee to the speed at which I can accept submissions given the need to test this with hardware.

Run [spotless](https://github.com/diffplug/spotless) to ensure everything is properly formatted:

```
./gradlew spotlessApply
```

## Related Projects

This application overlaps in functionality with some of these fine projects:

* [dashcast](https://github.com/stestagg/dashcast) - simple dashboard display application 
* [greenscreen](https://github.com/groupon/greenscreen) - original digital signage implementation
* [multicast](https://github.com/superhawk610/multicast) - a fork/refactor of greenscreen
* [node-castv2](https://github.com/thibauts/node-castv2) - nodejs library
* [nodecastor](https://github.com/vincentbernat/nodecastor) - nodejs library
* [chromecast-java-api-v2](https://github.com/vitalidze/chromecast-java-api-v2) - java library
* [pychromecast](https://github.com/balloob/pychromecast) - python library

## Name

It is designed for multiple Chromecasts, rather than a [googol](https://en.wikipedia.org/wiki/Googol) or [googolplex](https://en.wikipedia.org/wiki/Googolplex).
It is from [The Simpsons](https://simpsons.fandom.com/wiki/Springfield_Googolplex_Theatres). The developer made it singular and decided to use the American spelling.