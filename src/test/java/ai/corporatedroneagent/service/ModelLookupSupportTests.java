package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.model.ApplicationSettings;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

class ModelLookupSupportTests {

    @Test
    void usesSubmittedApiKeyBeforeSavedSettings() {
        SettingsService settingsService = mock(SettingsService.class);
        ModelLookupSupport support = new ModelLookupSupport(settingsService);

        String apiKey = support.apiKey("  submitted-key  ", true, settings -> "saved-key");

        assertThat(apiKey).isEqualTo("submitted-key");
        verifyNoInteractions(settingsService);
    }

    @Test
    void returnsBlankApiKeyWhenSavedKeyIsDisabled() {
        SettingsService settingsService = mock(SettingsService.class);
        ModelLookupSupport support = new ModelLookupSupport(settingsService);

        String apiKey = support.apiKey("", false, settings -> "saved-key");

        assertThat(apiKey).isEmpty();
        verifyNoInteractions(settingsService);
    }

    @Test
    void readsSavedApiKeyOnlyWhenNeeded() {
        SettingsService settingsService = mock(SettingsService.class);
        ApplicationSettings settings = new ApplicationSettings();
        settings.getOpenAi().setApiKey("saved-key");
        when(settingsService.getWithSecrets()).thenReturn(settings);
        ModelLookupSupport support = new ModelLookupSupport(settingsService);

        String apiKey = support.apiKey("", true, savedSettings -> savedSettings.getOpenAi().getApiKey());

        assertThat(apiKey).isEqualTo("saved-key");
    }

    @Test
    void filtersSortsAndDeduplicatesModelNames() {
        SettingsService settingsService = mock(SettingsService.class);
        ModelLookupSupport support = new ModelLookupSupport(settingsService);

        assertThat(support.sortedDistinct(Stream.of("zeta", "", null, "alpha", "zeta")))
                .containsExactly("alpha", "zeta");
    }

    @Test
    void translatesRestClientResponseFailures() {
        SettingsService settingsService = mock(SettingsService.class);
        ModelLookupSupport support = new ModelLookupSupport(settingsService);
        RestClientResponseException exception = new RestClientResponseException(
                "Unauthorized",
                401,
                "Unauthorized",
                HttpHeaders.EMPTY,
                new byte[0],
                StandardCharsets.UTF_8
        );

        assertThatThrownBy(() -> support.request("OpenAI models", () -> {
                    throw exception;
                }))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("OpenAI models request failed: 401 Unauthorized");
    }
}
