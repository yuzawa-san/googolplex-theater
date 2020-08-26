package com.jyuzawa.googolplex_theater.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * This class is a POJO for JSON deserialization.
 *
 * @author jyuzawa
 */
public final class ReceiverResponse {
  private static final String TYPE_RECEIVER_STATUS = "RECEIVER_STATUS";

  public int requestId;
  public String type;
  public ReceiverResponse.Status status;
  public String reason;

  private static final class Status {
    private final List<ReceiverResponse.Application> applications;

    @JsonCreator
    public Status(@JsonProperty("applications") List<ReceiverResponse.Application> applications) {
      this.applications = applications;
    }
  }

  private static final class Application {
    private final String appId;
    private final boolean isIdleScreen;
    private final String transportId;

    @JsonCreator
    public Application(
        @JsonProperty("appId") String appId,
        @JsonProperty("isIdleScreen") boolean isIdleScreen,
        @JsonProperty("transportId") String transportId) {
      this.appId = appId;
      this.isIdleScreen = isIdleScreen;
      this.transportId = transportId;
    }
  }

  /**
   * Examines the receiver status and applications list to determine if this message is an
   * application status update.
   *
   * @return whether this message is an application status
   */
  public boolean isApplicationStatus() {
    return TYPE_RECEIVER_STATUS.equals(type)
        && status != null
        && status.applications != null
        && !status.applications.isEmpty();
  }

  /**
   * This method requires isApplicationStatus() to have returned true.
   *
   * @return whether the application has quit and now the idle screen is running
   */
  public boolean isIdleScreen() {
    for (ReceiverResponse.Application application : status.applications) {
      if (application.isIdleScreen) {
        return true;
      }
    }
    return false;
  }

  /**
   * The transport ID must be extracted from the application status to establish a session with the
   * device.
   *
   * @param appId this application must be running
   * @return an ID to use with the session request
   */
  public String getApplicationTransportId(String appId) {
    for (ReceiverResponse.Application application : status.applications) {
      if (appId.equals(application.appId)) {
        return application.transportId;
      }
    }
    return null;
  }
}
