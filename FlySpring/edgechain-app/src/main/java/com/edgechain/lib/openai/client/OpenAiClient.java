package com.edgechain.lib.openai.client;

import com.edgechain.lib.constants.EndpointConstants;
import com.edgechain.lib.embeddings.request.OpenAiEmbeddingRequest;
import com.edgechain.lib.embeddings.response.OpenAiEmbeddingResponse;
import com.edgechain.lib.endpoint.Endpoint;
import com.edgechain.lib.openai.request.ChatCompletionRequest;
import com.edgechain.lib.openai.request.CompletionRequest;
import com.edgechain.lib.openai.response.ChatCompletionResponse;
import com.edgechain.lib.openai.response.CompletionResponse;
import com.edgechain.lib.rxjava.transformer.observable.EdgeChain;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Observable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.adapter.rxjava.RxJava3Adapter;

import java.util.Objects;

@Service
public class OpenAiClient {

  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final RestTemplate restTemplate = new RestTemplate();

  public EdgeChain<ChatCompletionResponse> createChatCompletion(
      Endpoint endpoint, ChatCompletionRequest request) {

    return new EdgeChain<>(
        Observable.create(
            emitter -> {
              try {

                logger.info("Logging ChatCompletion....");

                // Create headers
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(endpoint.getApiKey());

                HttpEntity<ChatCompletionRequest> entity = new HttpEntity<>(request, headers);

                logger.info(String.valueOf(entity.getBody()));

                // Send the POST request
                ResponseEntity<ChatCompletionResponse> response =
                    restTemplate.exchange(
                        endpoint.getUrl(), HttpMethod.POST, entity, ChatCompletionResponse.class);

                emitter.onNext(Objects.requireNonNull(response.getBody()));
                emitter.onComplete();

              } catch (final Exception e) {
                emitter.onError(e);
              }
            }));
  }

  public EdgeChain<ChatCompletionResponse> createChatCompletionStream(
      Endpoint endpoint, ChatCompletionRequest request) {

    try {
      return new EdgeChain<>(
          RxJava3Adapter.fluxToObservable(
              WebClient.builder()
                  .build()
                  .post()
                  .uri(EndpointConstants.OPENAI_CHAT_COMPLETION_API)
                  .accept(MediaType.TEXT_EVENT_STREAM)
                  .headers(
                      httpHeaders -> {
                        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
                        httpHeaders.setBearerAuth(endpoint.getApiKey());
                      })
                  .bodyValue(new ObjectMapper().writeValueAsString(request))
                  .retrieve()
                  .bodyToFlux(ChatCompletionResponse.class)));
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  public EdgeChain<CompletionResponse> createCompletion(
      Endpoint endpoint, CompletionRequest request) {
    return new EdgeChain<>(
        Observable.create(
            emitter -> {
              try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(endpoint.getApiKey());

                HttpEntity<CompletionRequest> entity = new HttpEntity<>(request, headers);

                ResponseEntity<CompletionResponse> response =
                    this.restTemplate.exchange(
                        endpoint.getUrl(), HttpMethod.POST, entity, CompletionResponse.class);
                emitter.onNext(Objects.requireNonNull(response.getBody()));
                emitter.onComplete();

              } catch (final Exception e) {
                emitter.onError(e);
              }
            }));
  }

  public EdgeChain<OpenAiEmbeddingResponse> createEmbeddings(
      Endpoint endpoint, OpenAiEmbeddingRequest request) {
    return new EdgeChain<>(
        Observable.create(
            emitter -> {
              try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(endpoint.getApiKey());
                HttpEntity<OpenAiEmbeddingRequest> entity = new HttpEntity<>(request, headers);

                ResponseEntity<OpenAiEmbeddingResponse> response =
                    this.restTemplate.exchange(
                        endpoint.getUrl(), HttpMethod.POST, entity, OpenAiEmbeddingResponse.class);

                emitter.onNext(Objects.requireNonNull(response.getBody()));
                emitter.onComplete();

              } catch (final Exception e) {
                emitter.onError(e);
              }
            }));
  }
}