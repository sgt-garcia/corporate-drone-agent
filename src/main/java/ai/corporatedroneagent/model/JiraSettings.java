package ai.corporatedroneagent.model;

import ai.corporatedroneagent.dto.JiraProjectDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Jira (API) knowledge source configuration. Only enough state is persisted for
 * the Settings UI to work across reloads: the validated connection details, the
 * secret API token (held in the secret store, like provider keys), and the
 * chosen projects. The {@code token}/{@code clearToken} fields are write-only
 * so the secret never leaves the device in a settings response.
 */
public class JiraSettings {

    private String instanceUrl = "";
    private String email = "";
    private boolean connected;
    private String apiVersion = "3";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String token = "";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean clearToken;
    private boolean tokenConfigured;
    private String tokenLastFour = "";
    private Integer tokenExpiresDays;
    private List<JiraProjectDto> projects = new ArrayList<>();

    public String getInstanceUrl() {
        return instanceUrl;
    }

    public void setInstanceUrl(String instanceUrl) {
        this.instanceUrl = instanceUrl == null ? "" : instanceUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? "" : email;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion == null || apiVersion.isBlank() ? "3" : apiVersion;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token == null ? "" : token;
    }

    public boolean isClearToken() {
        return clearToken;
    }

    public void setClearToken(boolean clearToken) {
        this.clearToken = clearToken;
    }

    public boolean isTokenConfigured() {
        return tokenConfigured;
    }

    public void setTokenConfigured(boolean tokenConfigured) {
        this.tokenConfigured = tokenConfigured;
    }

    public String getTokenLastFour() {
        return tokenLastFour;
    }

    public void setTokenLastFour(String tokenLastFour) {
        this.tokenLastFour = tokenLastFour == null ? "" : tokenLastFour;
    }

    public Integer getTokenExpiresDays() {
        return tokenExpiresDays;
    }

    public void setTokenExpiresDays(Integer tokenExpiresDays) {
        this.tokenExpiresDays = tokenExpiresDays;
    }

    public List<JiraProjectDto> getProjects() {
        return projects;
    }

    public void setProjects(List<JiraProjectDto> projects) {
        this.projects = projects == null ? new ArrayList<>() : projects;
    }
}
