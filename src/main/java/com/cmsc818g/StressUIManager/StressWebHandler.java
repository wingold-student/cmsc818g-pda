package com.cmsc818g.StressUIManager;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * StressWebHandler will be the middle-man for asking around for desired data.
 * 
 * This is the actor the WebRoutes will use when the user requests data from the
 * PDA. It will know the necessary actors(systems, managers, etc.) to query for
 * desired data. It can also serve it in a JSON format.
 */
public class StressWebHandler extends AbstractBehavior<StressWebHandler.Command> {

    public interface Command {}

    /** Currently just a test request message for some JSON data. */
    public final static class GetTestJSON implements Command {
        public final ActorRef<GetTestJSONResponse> replyTo;
        public GetTestJSON(ActorRef<GetTestJSONResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    /** Hands back the data. Note it will be part of the 'testData' field
     * in the json.
     */
    public final static class GetTestJSONResponse {
        public final TestData testData;
        public GetTestJSONResponse(TestData testData) {
            this.testData = testData;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressWebHandler(context));
    }


    public StressWebHandler(ActorContext<Command> context) {
        super(context);
    }

    /** TestData is just an example class for holding JSON data. */
    public final static class TestData {
        public final String field;

        @JsonCreator
        public TestData(@JsonProperty("field") String field) {
            this.field = field;
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(GetTestJSON.class, this::onGetTestJSON)
            .build();
    }

    private Behavior<Command> onGetTestJSON(GetTestJSON msg) {
        GetTestJSONResponse response = new GetTestJSONResponse(new TestData("fieldData"));
        msg.replyTo.tell(response);
        return this;
    }
}
