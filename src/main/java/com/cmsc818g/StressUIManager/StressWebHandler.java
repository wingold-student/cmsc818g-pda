package com.cmsc818g.StressUIManager;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonAppend;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
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
        public final Data data;
        public GetTestJSONResponse(Data data) {
            this.data = data;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressWebHandler(context));
    }


    public StressWebHandler(ActorContext<Command> context) {
        super(context);
    }

    public final static class Treatment {
        public final String title;
        public final String summary;
        public final String url;

        @JsonCreator
        public Treatment(@JsonProperty("title") String title,
                         @JsonProperty("summary") String summary,
                         @JsonProperty("url") String url) {

            this.title = title;
            this.summary = summary;
            this.url = url;
        }
    }

    /** TestData is just an example class for holding JSON data. */
    public final static class Data {
        public final int id;
        public final int heartRate;
        public final int sleepHours;
        public final int previousStressLevel;
        public final int currentStressLevel;
        public final String calendar;
        public final String location;
        public final Treatment treatment;

        @JsonCreator
        public Data(int id,
                        int heartRate,
                        int sleepHours,
                        int previousStressLevel,
                        int currentStressLevel,
                        String calendar,
                        String location,
                        Treatment treatment) {
            this.id = id;
            this.heartRate = heartRate;
            this.sleepHours = sleepHours;
            this.previousStressLevel = previousStressLevel;
            this.currentStressLevel = currentStressLevel;
            this.calendar = calendar;
            this.location = location;
            this.treatment = treatment;
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(GetTestJSON.class, this::onGetTestJSON)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Command> onGetTestJSON(GetTestJSON msg) {
        Treatment exampleTreatment = new Treatment("title", "summary", "url");
        Data exampleData = new Data(0,
                                    0,
                                    0,
                                    0,
                                    0,
                                    "meeting",
                                    "work",
                                    exampleTreatment);
        GetTestJSONResponse response = new GetTestJSONResponse(exampleData);
        msg.replyTo.tell(response);
        return this;
    }

    private StressWebHandler onPostStop() {
        getContext().getLog().info("Web Handler shutting down");
        return this;
    }
}
