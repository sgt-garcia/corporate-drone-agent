package ai.corporatedroneagent.model;

public class AzureOpenAiSettings extends ApiKeySettingsSupport {

    private String endpoint = "";
    private String deploymentName = "";

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getDeploymentName() {
        return deploymentName;
    }

    public void setDeploymentName(String deploymentName) {
        this.deploymentName = deploymentName;
    }
}
