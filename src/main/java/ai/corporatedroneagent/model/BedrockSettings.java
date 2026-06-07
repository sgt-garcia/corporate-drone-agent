package ai.corporatedroneagent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class BedrockSettings {

    private String region = "us-east-1";
    private String model = "";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String accessKey = "";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean clearAccessKey;
    private boolean accessKeyConfigured;
    private String accessKeyLastFour = "";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String secretKey = "";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean clearSecretKey;
    private boolean secretKeyConfigured;

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public boolean isClearAccessKey() {
        return clearAccessKey;
    }

    public void setClearAccessKey(boolean clearAccessKey) {
        this.clearAccessKey = clearAccessKey;
    }

    public boolean isAccessKeyConfigured() {
        return accessKeyConfigured;
    }

    public void setAccessKeyConfigured(boolean accessKeyConfigured) {
        this.accessKeyConfigured = accessKeyConfigured;
    }

    public String getAccessKeyLastFour() {
        return accessKeyLastFour;
    }

    public void setAccessKeyLastFour(String accessKeyLastFour) {
        this.accessKeyLastFour = accessKeyLastFour;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isClearSecretKey() {
        return clearSecretKey;
    }

    public void setClearSecretKey(boolean clearSecretKey) {
        this.clearSecretKey = clearSecretKey;
    }

    public boolean isSecretKeyConfigured() {
        return secretKeyConfigured;
    }

    public void setSecretKeyConfigured(boolean secretKeyConfigured) {
        this.secretKeyConfigured = secretKeyConfigured;
    }
}
