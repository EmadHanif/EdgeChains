package com.edgechain;

import com.edgechain.lib.chains.PineconeRetrieval;
import com.edgechain.lib.chains.retrieval.Retrieval;
import com.edgechain.lib.constants.WebConstants;
import com.edgechain.lib.configuration.EdgeChainAutoConfiguration;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.edgechain.lib.context.domain.HistoryContext;
import com.edgechain.lib.context.services.HistoryContextService;
import com.edgechain.lib.context.services.impl.RedisHistoryContextService;
import com.edgechain.lib.feign.EmbeddingService;
import com.edgechain.lib.feign.OpenAiService;
import com.edgechain.lib.feign.OpenAiStreamService;
import com.edgechain.lib.feign.WikiService;
import com.edgechain.lib.feign.index.PineconeService;
import com.edgechain.lib.jsonnet.JsonnetArgs;
import com.edgechain.lib.jsonnet.JsonnetLoader;
import com.edgechain.lib.jsonnet.enums.DataType;
import com.edgechain.lib.jsonnet.impl.FileJsonnetLoader;
import com.edgechain.lib.jsonnet.mapper.ServiceMapper;
import com.edgechain.lib.jsonnet.schemas.Schema;
import com.edgechain.lib.jsonnet.schemas.ChatSchema;
import com.edgechain.lib.openai.endpoint.Endpoint;
import com.edgechain.lib.reader.impl.PdfReader;
import com.edgechain.lib.request.OpenAiChatRequest;
import com.edgechain.lib.request.OpenAiEmbeddingsRequest;
import com.edgechain.lib.request.PineconeRequest;
import com.edgechain.lib.response.ArkResponse;
import com.edgechain.lib.response.ArkEmitter;
import com.edgechain.lib.rxjava.response.ChainResponse;
import com.edgechain.lib.rxjava.retry.impl.ExponentialDelay;
import com.edgechain.lib.rxjava.transformer.observable.EdgeChain;
import com.edgechain.lib.rxjava.utils.AtomInteger;
import io.reactivex.rxjava3.core.Observable;
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import scala.Tuple2;
import scala.Tuple3;

import static com.edgechain.lib.constants.WebConstants.*;
import static com.edgechain.lib.rxjava.transformer.observable.EdgeChain.create;

@SpringBootApplication
@ImportAutoConfiguration({FeignAutoConfiguration.class})
@Import(EdgeChainAutoConfiguration.class)
public class EdgeChainApplication implements CommandLineRunner {

  public static final Logger logger = LoggerFactory.getLogger(EdgeChainApplication.class);

  public static void main(String[] args) throws Exception {

    System.setProperty("server.port", "8080");
    System.setProperty("server.url", "http://localhost:8080/v2");

    System.setProperty("OPENAI_AUTH_KEY", "");

    System.setProperty("PINECONE_AUTH_KEY", "");
    System.setProperty(
            "PINECONE_QUERY_API", "");
    System.setProperty(
            "PINECONE_UPSERT_API",
            "");
    System.setProperty(
            "PINECONE_DELETE_API",
            "");

    System.setProperty(
            "spring.data.redis.host", "");
    System.setProperty("spring.data.redis.port", "6379");
    System.setProperty("spring.data.redis.username", "default");
    System.setProperty("spring.data.redis.password", "");
    System.setProperty("spring.data.redis.connect-timeout", "120000");
    System.setProperty("spring.redis.ttl", "3600");
    System.setProperty("doc2vec.filepath", "R:\\doc2vec.bin");

    loadSentenceModel();
    readDoc2Vec();

    SpringApplication.run(EdgeChainApplication.class, args);
  }

  public static void loadSentenceModel() throws IOException {
    WebConstants.sentenceModel = EdgeChainApplication.class.getResourceAsStream("/en-sent.zip");
    if (Objects.isNull(WebConstants.sentenceModel)) {
      logger.error("en-sent.zip file isn't loaded from the resources.'");
    }
  }

  public static void readDoc2Vec() throws Exception {

    String modelPath = System.getProperty("doc2vec.filepath");

    File file = new File(modelPath);

    if (!file.exists()) {
      logger.warn(
              "It seems like, you haven't trained the model or correctly specified Doc2Vec model"
                      + " path.");
    } else {
      logger.info("Loading...");
      WebConstants.embeddingDoc2VecModel =
              WordVectorSerializer.readParagraphVectors(new FileInputStream(modelPath));
      logger.info("Doc2Vec model is successfully loaded...");
    }
  }

  @Override
  public void run(String... args) throws Exception {

  }

  @RestController
  @RequestMapping("/v1/wiki")
  public class WikiController {

    @GetMapping(
            value = "/summary",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object wikiSummary(@RequestParam String query, @RequestParam Boolean stream) {

      HashMap<String, JsonnetArgs> parameters = new HashMap<>();
      parameters.put("keepMaxTokens", new JsonnetArgs(DataType.BOOLEAN, "true"));
      parameters.put("maxTokens", new JsonnetArgs(DataType.INTEGER, "4096"));

      // Step 1: Create JsonnetLoader
      JsonnetLoader loader = new FileJsonnetLoader("R:\\wiki.jsonnet");
      Schema schema = loader.loadOrReload(parameters, Schema.class);

      // Step 2: Create Endpoint For ChatCompletion;
      Endpoint chatEndpoint =
              new Endpoint(
                      OPENAI_CHAT_COMPLETION_API,
                      OPENAI_AUTH_KEY,
                      "gpt-3.5-turbo",
                      "user",
                      0.7,
                      stream,
                      new ExponentialDelay(3, 5, 2, TimeUnit.SECONDS));

      // Step 3: Fetch WikiService Defined In Jsonnet
      WikiService wikiService = new ServiceMapper().map(schema, "wikiService", WikiService.class);

      if (chatEndpoint.getStream()) {

        // Fetch OpenAIStream Service Defined In Jsonnet
        OpenAiStreamService openAiStreamService =
                new ServiceMapper().map(schema, "openAiStreamService", OpenAiStreamService.class);

        return new ArkResponse<>(
                create(wikiService.getPageContent(query).getResponse())
                        .transform(
                                wikiOutput -> {
                                  parameters.put("keepContext", new JsonnetArgs(DataType.BOOLEAN, "true"));
                                  parameters.put("context", new JsonnetArgs(DataType.STRING, wikiOutput));

                                  Schema schema_ = loader.loadOrReload(parameters, Schema.class);
                                  return schema_.getPrompt();
                                })
                        .transform(
                                prompt ->
                                        openAiStreamService.chatCompletion(
                                                new OpenAiChatRequest(chatEndpoint, prompt)))
                        .getScheduledObservableWithoutRetry());
      } else {

        // Fetch OpenAI Service Defined In Jsonnet
        OpenAiService openAiService =
                new ServiceMapper().map(schema, "openAiService", OpenAiService.class);

        return new ArkResponse<>(
                create(wikiService.getPageContent(query).getResponse())
                        .transform(
                                wikiOutput -> {
                                  parameters.put("keepContext", new JsonnetArgs(DataType.BOOLEAN, "true"));
                                  parameters.put("context", new JsonnetArgs(DataType.STRING, wikiOutput));

                                  Schema schema_ = loader.loadOrReload(parameters, Schema.class);
                                  return schema_.getPrompt();
                                })
                        .transform(
                                prompt ->
                                        openAiService.chatCompletion(new OpenAiChatRequest(chatEndpoint, prompt)))
                        .getScheduledObservableWithoutRetry());
      }
    }
  }

  @RestController
  @RequestMapping("/v1/pinecone")
  public class PineconeController {

    @Autowired private PineconeService pineconeService;

    @DeleteMapping("/deleteAll")
    public ChainResponse delete() {

      Endpoint pineconeEndpoint =
              new Endpoint(
                      PINECONE_DELETE_API,
                      PINECONE_AUTH_KEY,
                      new ExponentialDelay(3, 3, 2, TimeUnit.SECONDS));

      return this.pineconeService.deleteAll(new PineconeRequest(pineconeEndpoint));
    }
  }

  @RestController
  @RequestMapping("/v1/history-context")
  public class RedisHistoryContextController {

    @Autowired private RedisHistoryContextService historyContextService;

    @GetMapping("/create")
    public ArkResponse<?> create() {
      return new ArkResponse<>(
              historyContextService.create().getScheduledObservableWithRetry()
      );
    }

    @GetMapping("/{id}")
    public ArkResponse<?> findById(@PathVariable String id) {
      return new ArkResponse<>(historyContextService.get(id).getScheduledObservableWithRetry());
    }

    @DeleteMapping("/{id}")
    public ArkResponse<?> delete(@PathVariable String id) {
      return new ArkResponse<>(historyContextService.delete(id).getScheduledObservableWithRetry());
    }
  }

  @RestController
  @RequestMapping("/v1/pinecone/openai")
  public class PineconeOpenAiController {

    @Autowired private PdfReader pdfReader;
    @Autowired private EmbeddingService embeddingService_;
    @Autowired private PineconeService pineconeService_;

    @PostMapping(value = "/upsert", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void upsertByChunk(@RequestParam(value = "file") MultipartFile file) throws IOException {

      String[] arr = pdfReader.readByChunkSize(file.getInputStream(), 512);

      Endpoint embeddingEndpoint =
              new Endpoint(
                      OPENAI_EMBEDDINGS_API,
                      OPENAI_AUTH_KEY,
                      "text-embedding-ada-002",
                      new ExponentialDelay(3, 3, 2, TimeUnit.SECONDS));

      Endpoint pineconeEndpoint =
              new Endpoint(
                      PINECONE_UPSERT_API,
                      PINECONE_AUTH_KEY,
                      new ExponentialDelay(3, 3, 2, TimeUnit.SECONDS));

      Retrieval retrieval =
              new PineconeRetrieval(
                      pineconeEndpoint, embeddingEndpoint, embeddingService_, pineconeService_);

      IntStream.range(0, arr.length).parallel().forEach(i -> retrieval.upsert(arr[i]));
    }

    @GetMapping(
            value = "/query",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public Object query(
            @RequestParam Integer topK, @RequestParam Boolean stream, @RequestParam String query) {

      HashMap<String, JsonnetArgs> parameters = new HashMap<>();
      parameters.put("keepMaxTokens", new JsonnetArgs(DataType.BOOLEAN, "true"));
      parameters.put("maxTokens", new JsonnetArgs(DataType.INTEGER, "4096"));

      JsonnetLoader loader = new FileJsonnetLoader("R:\\pinecone-query.jsonnet");
      Schema schema = loader.loadOrReload(parameters, Schema.class);

      Endpoint chatEndpoint =
              new Endpoint(
                      OPENAI_CHAT_COMPLETION_API,
                      OPENAI_AUTH_KEY,
                      "gpt-3.5-turbo",
                      "user",
                      0.7,
                      stream,
                      new ExponentialDelay(3, 5, 2, TimeUnit.SECONDS));

      Endpoint embeddingEndpoint =
              new Endpoint(
                      OPENAI_EMBEDDINGS_API,
                      OPENAI_AUTH_KEY,
                      "text-embedding-ada-002",
                      new ExponentialDelay(3, 3, 2, TimeUnit.SECONDS));

      Endpoint pineconeEndpoint =
              new Endpoint(
                      PINECONE_QUERY_API,
                      PINECONE_AUTH_KEY,
                      new ExponentialDelay(3, 3, 2, TimeUnit.SECONDS));

      PineconeService pineconeService =
              new ServiceMapper().map(schema, "pineconeService", PineconeService.class);
      EmbeddingService embeddingService =
              new ServiceMapper().map(schema, "embeddingService", EmbeddingService.class);
      OpenAiService openAiService =
              new ServiceMapper().map(schema, "openAiService", OpenAiService.class);

      if (chatEndpoint.getStream()) {

        System.out.println("Using Stream");

        String[] pineconeQueries =
                create(
                        embeddingService
                                .openAi(new OpenAiEmbeddingsRequest(embeddingEndpoint, query))
                                .getResponse())
                        .transform(
                                embeddingOutput ->
                                        pineconeService
                                                .query(new PineconeRequest(pineconeEndpoint, embeddingOutput, topK))
                                                .getResponse())
                        .transform(pineconeOutput -> pineconeOutput.split("\n"))
                        .getWithOutRetry();

        AtomInteger currentTopK = AtomInteger.of(0);

        return new ArkEmitter<>(
                new EdgeChain<>(
                        Observable.create(
                                emitter -> {
                                  try {
                                    String input = pineconeQueries[currentTopK.getAndIncrement()];

                                    parameters.put(
                                            "keepContext", new JsonnetArgs(DataType.BOOLEAN, "true"));
                                    parameters.put("context", new JsonnetArgs(DataType.STRING, input));

                                    Schema schema_ = loader.loadOrReload(parameters, Schema.class);

                                    emitter.onNext(
                                            openAiService.chatCompletion(
                                                    new OpenAiChatRequest(chatEndpoint, schema_.getPrompt())));
                                    emitter.onComplete();

                                  } catch (final Exception e) {
                                    emitter.onError(e);
                                  }
                                }))
                        .doWhileLoop(() -> currentTopK.get() == ((int) topK))
                        .getScheduledObservableWithoutRetry());
      } else {
        // Creation of Chains
        return new ArkResponse<>(
                create(
                        embeddingService
                                .openAi(new OpenAiEmbeddingsRequest(embeddingEndpoint, query))
                                .getResponse())
                        .transform(
                                embeddingOutput ->
                                        pineconeService
                                                .query(new PineconeRequest(pineconeEndpoint, embeddingOutput, topK))
                                                .getResponse())
                        .transform(
                                pineconeOutput -> {
                                  List<ChainResponse> output = new ArrayList<>();

                                  StringTokenizer tokenizer = new StringTokenizer(pineconeOutput, "\n");
                                  while (tokenizer.hasMoreTokens()) {

                                    String response = tokenizer.nextToken();
                                    // Use jsonnet loader
                                    parameters.put("keepContext", new JsonnetArgs(DataType.BOOLEAN, "true"));
                                    parameters.put("context", new JsonnetArgs(DataType.STRING, response));

                                    Schema schema_ = loader.loadOrReload(parameters, Schema.class);
                                    output.add(
                                            openAiService.chatCompletion(
                                                    new OpenAiChatRequest(chatEndpoint, schema_.getPrompt())));
                                  }

                                  return output;
                                })
                        .getScheduledObservableWithoutRetry());
      }
    }

    /**
     * Fixing the issue....
     * @param contextId
     * @param stream
     * @param query
     * @return
     */
    @GetMapping(
            value = "/query/context",
            produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_EVENT_STREAM_VALUE})
    public ArkResponse<?> queryWithChatHistory(
            @RequestParam String contextId, @RequestParam Boolean stream, @RequestParam String query) {

      HashMap<String, JsonnetArgs> parameters = new HashMap<>();

      parameters.put("keepMaxTokens", new JsonnetArgs(DataType.BOOLEAN, "true"));
      parameters.put("maxTokens", new JsonnetArgs(DataType.INTEGER, "4096"));
      parameters.put("query", new JsonnetArgs(DataType.STRING, query));
      parameters.put("history", new JsonnetArgs(DataType.STRING, ""));
      parameters.put("context", new JsonnetArgs(DataType.STRING,""));

      JsonnetLoader loader = new FileJsonnetLoader("R:\\pinecone-chat.jsonnet");
      ChatSchema schema = loader.loadOrReload(parameters, ChatSchema.class);

      Endpoint embeddingEndpoint =
              new Endpoint(
                      OPENAI_EMBEDDINGS_API,
                      OPENAI_AUTH_KEY,
                      "text-embedding-ada-002",
                      new ExponentialDelay(3, 3, 2, TimeUnit.SECONDS));

      Endpoint pineconeEndpoint =
              new Endpoint(
                      PINECONE_QUERY_API,
                      PINECONE_AUTH_KEY,
                      new ExponentialDelay(3, 3, 2, TimeUnit.SECONDS));

      Endpoint chatEndpoint =
              new Endpoint(
                      OPENAI_CHAT_COMPLETION_API,
                      OPENAI_AUTH_KEY,
                      "gpt-3.5-turbo",
                      "user",
                      0.7,
                      stream,
                      new ExponentialDelay(3, 3, 2, TimeUnit.SECONDS));

      PineconeService pineconeService = new ServiceMapper().map(schema, "pineconeService", PineconeService.class);
      EmbeddingService embeddingService = new ServiceMapper().map(schema, "embeddingService", EmbeddingService.class);
      OpenAiService openAiService = new ServiceMapper().map(schema, "openAiService", OpenAiService.class);
      HistoryContextService contextService = new ServiceMapper().map(schema, "historyContextService", HistoryContextService.class);

      if (chatEndpoint.getStream()) return null;
      else {

        // Creating Chains
        return new ArkResponse<>(
                create(
                        embeddingService
                                .openAi(new OpenAiEmbeddingsRequest(embeddingEndpoint, query))
                                .getResponse())
                        .transform(
                                embeddingOutput ->
                                        pineconeService
                                                .query(
                                                        new PineconeRequest(
                                                                pineconeEndpoint, embeddingOutput, schema.getTopK()))
                                                .getResponse())
                        .transform(
                                pineconeOutput -> {
                                  System.out.printf("Query-%s-%s", schema.getTopK(), pineconeOutput);

                                  // Get RedisHistory Context
                                  String chatHistory =
                                          contextService.get(contextId).getWithRetry().getResponse();

                                  if(chatHistory.length() > 0 ){
                                    return new Tuple3<>(query, pineconeOutput + "\n", "Chat History\n"+chatHistory);
                                  } else {
                                    return new Tuple3<>(query, pineconeOutput + "\n", "");
                                  }

                                })
                        .transform(
                                tuple3 -> {

                                  // Lessening the History
                                  int totalTokens =
                                          schema.getQuery().length()
                                                  + schema.getPreset().length()
                                                  + tuple3._2().length()
                                                  + tuple3._3().length();

                                  String modifiedHistory = "";

                                  if (tuple3._3().length() > 0) {
                                    if(totalTokens > schema.getMaxTokens()) {
                                      int diff = Math.abs(schema.getMaxTokens() - totalTokens);
                                      System.out.println("Difference Value: " + diff);
                                      modifiedHistory =  tuple3._3().substring(diff + 1);
                                      parameters.put("history", new JsonnetArgs(DataType.STRING, "Chat History"+"\n"+modifiedHistory));
                                    }
                                  }
                                  else {
                                    modifiedHistory = tuple3._3();
                                    parameters.put("history", new JsonnetArgs(DataType.STRING, "Chat History"+"\n"+modifiedHistory));
                                  }


                                  // Use jsonnet loader
                                  parameters.put("keepContext", new JsonnetArgs(DataType.BOOLEAN, "true"));
                                  parameters.put("context", new JsonnetArgs(DataType.STRING, tuple3._2()));

                                  ChatSchema schema_ = loader.loadOrReload(parameters, ChatSchema.class);

                                  System.out.println("Prompt:\n" + schema_.getPrompt());

                                  String openAiResponse =
                                          openAiService
                                                  .chatCompletion(
                                                          new OpenAiChatRequest(chatEndpoint, schema_.getPrompt()))
                                                  .getResponse();

                                  // Save to Redis
                                  contextService
                                          .put(contextId, tuple3._3() + tuple3._1() + openAiResponse)
                                          .getWithRetry();

                                  return openAiResponse;
                                })
                        .getScheduledObservableWithoutRetry());
      }
    }
  }
}
