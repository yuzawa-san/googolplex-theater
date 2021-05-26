package com.jyuzawa.googolplex_theater;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

class GoogolplexTheaterTest {

  private GoogolplexTheater gt;

  @BeforeEach
  void setup() {
    gt = new GoogolplexTheater();
    CommandSpec spec = Mockito.mock(CommandSpec.class);
    CommandLine commandLine = Mockito.mock(CommandLine.class);
    Mockito.when(spec.commandLine()).thenReturn(commandLine);
    gt.spec = spec;
    gt.castConfigFile = new File("./src/dist/conf/cast_config.json");
  }

  @Test
  void appIdTest() {
    gt.validate();
    gt.appId = "ABCDEFGH";
    gt.validate();
    gt.appId = "not-an-app-id";
    assertThrows(
        ParameterException.class,
        () -> {
          gt.validate();
        });
  }

  @Test
  void castConfigTest() {
    gt.validate();
    gt.castConfigFile = new File("/not/a/real/file.json");
    assertThrows(
        ParameterException.class,
        () -> {
          gt.validate();
        });
  }
}
