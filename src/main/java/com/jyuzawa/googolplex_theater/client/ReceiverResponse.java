package com.jyuzawa.googolplex_theater.client;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collections;
import java.util.List;

/**
 * This class is a POJO for JSON deserialization.
 *
 * @author jyuzawa
 */
public final class ReceiverResponse {
  static final String TYPE_RECEIVER_STATUS = "RECEIVER_STATUS";

  public final int requestId;
  public final String type;
  public final ReceiverResponse.Status status;
  public final String reason;

  @JsonCreator
  public ReceiverResponse(
      @JsonProperty("requestId") int requestId,
      @JsonProperty("type") String type,
      @JsonProperty("status") ReceiverResponse.Status status,
      @JsonProperty("reason") String reason) {
    this.requestId = requestId;
    this.type = type;
    this.status = status;
    this.reason = reason;
  }

  static final class Status {
    public final List<ReceiverResponse.Application> applications;

    @JsonCreator
    public Status(@JsonProperty("applications") List<ReceiverResponse.Application> applications) {
      if (applications == null) {
        this.applications = Collections.emptyList();
      } else {
        this.applications = Collections.unmodifiableList(applications);
      }
    }
  }

  static final class Application {
    public final String appId;
    public final boolean isIdleScreen;
    public final String transportId;

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
    return TYPE_RECEIVER_STATUS.equals(type) && status != null && !status.applications.isEmpty();
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
