package com.jyuzawa.googolplex_theater;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("com/jyuzawa/googolplex_theater")
@ConfigurationParameter(
    key = Constants.GLUE_PROPERTY_NAME,
    value = "com.jyuzawa.googolplex_theater")
@ConfigurationParameter(
    key = Constants.PLUGIN_PROPERTY_NAME,
    value = "pretty,html:build/reports/cucumber.html")
public class RunCucumber {}
