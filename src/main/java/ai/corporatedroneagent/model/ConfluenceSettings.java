package ai.corporatedroneagent.model;

import ai.corporatedroneagent.dto.ConfluenceSpaceDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Confluence (API) knowledge source configuration. Sibling of {@link JiraSettings}:
 * only enough state is persisted for the Settings UI to work across reloads — the
 * validated connection details, the secret API token (held in the secret store, like
 * provider keys), and the chosen spaces. The {@code token}/{@code clearToken} fields
 * are write-only so the secret never leaves the device in a settings response.
 * Confluence Cloud exposes a single REST API, so there is no apiVersion to track.
 */
public class ConfluenceSettings {

    private String instanceUrl = "";
    private String email = "";
    private boolean connected;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String token = "";
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean clearToken;
    private boolean tokenConfigured;
    private String tokenLastFour = "";
    private Integer tokenExpiresDays;
    private List<ConfluenceSpaceDto> spaces = new ArrayList<>();

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

    public List<ConfluenceSpaceDto> getSpaces() {
        return spaces;
    }

    public void setSpaces(List<ConfluenceSpaceDto> spaces) {
        this.spaces = spaces == null ? new ArrayList<>() : spaces;
    }
}
