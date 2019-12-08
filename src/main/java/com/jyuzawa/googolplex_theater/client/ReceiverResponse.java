package com.jyuzawa.googolplex_theater.client;

import java.util.List;

public final class ReceiverResponse {
  private static final String TYPE_RECEIVER_STATUS = "RECEIVER_STATUS";

  public int requestId;
  public String type;
  public ReceiverResponse.Status status;
  public String reason;

  private static final class Status {
    public List<ReceiverResponse.Application> applications;
  }

  private static final class Application {
    public String appId;
    public boolean isIdleScreen;
    public String transportId;
  }

  public boolean isApplicationStatus() {
    return TYPE_RECEIVER_STATUS.equals(type)
        && status != null
        && status.applications != null
        && !status.applications.isEmpty();
  }

  public boolean isIdleScreen() {
    for (ReceiverResponse.Application application : status.applications) {
      if (application.isIdleScreen) {
        return true;
      }
    }
    return false;
  }

  public String getApplicationTransportId(String appId) {
    for (ReceiverResponse.Application application : status.applications) {
      if (appId.equals(application.appId)) {
        return application.transportId;
      }
    }
    return null;
  }
}
