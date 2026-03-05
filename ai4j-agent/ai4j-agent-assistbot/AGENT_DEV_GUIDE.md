# Agent Development Guide

This guide provides an overview of developing AI agents using Spring AI Alibaba in the `ai4j-agent-assistbot` project.

## Core Components

The project uses **Spring AI Alibaba** to interact with LLMs (Large Language Models) like Qwen (Tongyi Qianwen).

### 1. ChatClient

`ChatClient` is the primary interface for interacting with the AI model. It provides a fluent API for constructing prompts and handling responses.

- **Injection**: You can inject `ChatClient.Builder` and build a `ChatClient` instance.
- **Usage**: Use the `prompt()` method to start a conversation.

### 2. Configuration

The application is configured in `src/main/resources/application.yml`.
Key configurations include:
- `spring.ai.dashscope.api-key`: Your DashScope API Key.
- `spring.ai.dashscope.chat.options.model`: The model to use (e.g., `qwen-turbo`, `qwen-max`).

## usage Method

### Creating a Simple Agent

To create a simple agent that responds to user input:

1.  **Create a Controller or Service**:
    Inject `ChatClient.Builder` via the constructor.

    ```java
    @RestController
    public class MyAgentController {

        private final ChatClient chatClient;

        public MyAgentController(ChatClient.Builder builder) {
            this.chatClient = builder.build();
        }

        @GetMapping("/chat")
        public String chat(@RequestParam String message) {
            return chatClient.prompt()
                    .user(message)
                    .call()
                    .content();
        }
    }
    ```

2.  **Run the Application**:
    Start the Spring Boot application (`AssistBotApplication`).

3.  **Test**:
    Access `http://localhost:8080/chat?message=Hello` to see the agent's response.

### Creating a Tool/Function Calling Agent (Demo)

This project includes a Flight Booking Agent demo that demonstrates Function Calling capabilities.

1.  **Define the Tool**:
    Create a class implementing `Function<Request, Response>`.
    See `org.ai4j.agent.assistbot.tool.FlightBookingService`.

2.  **Register the Tool**:
    Register the function as a Bean with `@Description`.
    See `org.ai4j.agent.assistbot.config.AgentToolsConfig`.

    ```java
    @Configuration
    public class AgentToolsConfig {
        @Bean
        @Description("Book a flight for a user given origin, destination and date")
        public Function<FlightBookingService.BookingRequest, FlightBookingService.BookingResponse> flightBookingService() {
            return new FlightBookingService();
        }
    }
    ```

    ```java
    @GetMapping("/book-flight")
    public String bookFlight(@RequestParam(value = "request", defaultValue = "Book a flight from Beijing to Shanghai for tomorrow") String request) {
        return chatClient.prompt()
                .user(request)
                .tools("flightBookingService")
                .call()
                .content();
    }
    ```

3.  **Enable the Tool in ChatClient**:
    Use `.tools("functionName")` when creating the prompt.

    ```java
    chatClient.prompt()
            .user(message)
            .tools("flightBookingService")
            .call()
    ```

4.  **Test the Agent**:
    Access `http://localhost:8080/agent/demo/book-flight`
    The agent will automatically call the `flightBookingService` when you ask it to book a flight.

### Advanced Features

- **Prompt Templating**: Use `PromptTemplate` for dynamic prompts.
- **Output Parsing**: Use `BeanOutputParser` to get structured Java objects from the AI response.
- **RAG (Retrieval Augmented Generation)**: Integrate with `VectorStore` to provide context from documents.
- **Function Calling**: Register Java functions as tools that the AI can call.

## Prerequisites

- **Java 17+**
- **Maven 3.x**
- **DashScope API Key**: Set the `DASHSCOPE_API_KEY` environment variable.

## References

- [Spring AI Documentation](https://docs.spring.io/spring-ai/reference/)
- [Spring AI Alibaba GitHub](https://github.com/alibaba/spring-ai-alibaba)
