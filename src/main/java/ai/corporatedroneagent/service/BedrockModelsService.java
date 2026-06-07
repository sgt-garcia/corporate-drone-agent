package ai.corporatedroneagent.service;

import ai.corporatedroneagent.dto.BedrockModelsRequest;
import ai.corporatedroneagent.model.ApplicationSettings;
import ai.corporatedroneagent.util.Strings;
import java.util.List;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.model.FoundationModelSummary;
import software.amazon.awssdk.services.bedrock.model.InferenceType;
import software.amazon.awssdk.services.bedrock.model.ListFoundationModelsRequest;
import software.amazon.awssdk.services.bedrock.model.ModelModality;

@Service
public class BedrockModelsService {

    private final SettingsService settingsService;
    private final ModelLookupSupport modelLookupSupport;

    public BedrockModelsService(SettingsService settingsService, ModelLookupSupport modelLookupSupport) {
        this.settingsService = settingsService;
        this.modelLookupSupport = modelLookupSupport;
    }

    public List<String> listRegions() {
        return modelLookupSupport.sortedDistinct(BedrockClient.serviceMetadata().regions().stream().map(Region::id));
    }

    public List<String> listModels(BedrockModelsRequest request) {
        String region = Strings.defaultIfBlank(request == null ? "" : request.getRegion(), "");
        String accessKey = credential(
                request == null ? "" : request.getAccessKey(),
                request == null || request.isUseSavedAccessKey(),
                settings -> settings.getBedrock().getAccessKey()
        );
        String secretKey = credential(
                request == null ? "" : request.getSecretKey(),
                request == null || request.isUseSavedSecretKey(),
                settings -> settings.getBedrock().getSecretKey()
        );

        if (region.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            return List.of();
        }

        try (BedrockClient client = BedrockClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider(accessKey, secretKey))
                .build()) {
            return modelLookupSupport.sortedDistinct(client.listFoundationModels(ListFoundationModelsRequest.builder()
                            .byOutputModality(ModelModality.TEXT)
                            .byInferenceType(InferenceType.ON_DEMAND)
                            .build())
                    .modelSummaries()
                    .stream()
                    .filter(BedrockModelsService::isChatModel)
                    .map(FoundationModelSummary::modelId));
        } catch (AwsServiceException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Bedrock models request failed: " + exception.statusCode() + " " + exception.awsErrorDetails().errorMessage()
            );
        } catch (SdkClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Bedrock models request failed.");
        }
    }

    static boolean isChatModel(FoundationModelSummary model) {
        return model != null
                && hasModality(model.inputModalities(), ModelModality.TEXT)
                && hasModality(model.outputModalities(), ModelModality.TEXT)
                && hasInferenceType(model.inferenceTypesSupported(), InferenceType.ON_DEMAND)
                && !Strings.defaultIfBlank(model.modelId(), "").toLowerCase().contains("embed");
    }

    private static boolean hasModality(List<ModelModality> modalities, ModelModality expected) {
        return modalities != null && modalities.contains(expected);
    }

    private static boolean hasInferenceType(List<InferenceType> inferenceTypes, InferenceType expected) {
        return inferenceTypes != null && inferenceTypes.contains(expected);
    }

    private String credential(
            String submittedCredential,
            boolean useSavedCredential,
            Function<ApplicationSettings, String> savedCredential
    ) {
        if (submittedCredential != null && !submittedCredential.isBlank()) {
            return submittedCredential.trim();
        }
        if (!useSavedCredential) {
            return "";
        }
        return Strings.defaultIfBlank(savedCredential.apply(settingsService.getWithSecrets()), "");
    }

    static AwsCredentialsProvider credentialsProvider(String accessKey, String secretKey) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }
}
