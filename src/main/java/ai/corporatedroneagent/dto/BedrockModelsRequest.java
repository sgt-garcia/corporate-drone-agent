package ai.corporatedroneagent.dto;

public class BedrockModelsRequest {

    private String region = "";
    private String accessKey = "";
    private String secretKey = "";
    private boolean useSavedAccessKey = true;
    private boolean useSavedSecretKey = true;

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isUseSavedAccessKey() {
        return useSavedAccessKey;
    }

    public void setUseSavedAccessKey(boolean useSavedAccessKey) {
        this.useSavedAccessKey = useSavedAccessKey;
    }

    public boolean isUseSavedSecretKey() {
        return useSavedSecretKey;
    }

    public void setUseSavedSecretKey(boolean useSavedSecretKey) {
        this.useSavedSecretKey = useSavedSecretKey;
    }
}
