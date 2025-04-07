package ai;


import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import org.bsc.langgraph4j.langchain4j.serializer.std.ChatMesssageSerializer;
import org.bsc.langgraph4j.langchain4j.serializer.std.ToolExecutionRequestSerializer;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.serializer.std.ObjectStreamStateSerializer;

import java.util.Map;
import java.util.Optional;

public class State extends MessagesState<ChatMessage>{

    public Optional<String> next() {
        return this.value("next");
    }

    public Optional<String> code() {
        return this.value("code");
    }

    public Optional<String> dependencyAnalysis() {
        return this.value("dependency_analysis");
    }

    public Optional<String> instruction() {
        return this.value("instruction");
    }

    public State(Map<String, Object> initData) {
        super( initData  );
    }
}

class StateSerializer extends ObjectStreamStateSerializer<State> {

    public StateSerializer() {
        super(State::new);

        mapper().register(ToolExecutionRequest.class, new ToolExecutionRequestSerializer());
        mapper().register(ChatMessage.class, new ChatMesssageSerializer());
    }
}