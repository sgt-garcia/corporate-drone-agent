package ai.corporatedroneagent.model;

public abstract class ApiKeyModelSettings extends ApiKeySettingsSupport {

    private String model = "";

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
