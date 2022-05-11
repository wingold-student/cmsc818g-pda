package com.cmsc818g.StressUIManager;

import com.cmsc818g.StressDetectionEngine.StressDetectionEngine.DetectionData;
import com.cmsc818g.StressRecommendationEngine.StressRecommendationEngine.RecommendationData;
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

    public final static class GetJSONData implements Command {
        public final ActorRef<GetJSONDataResponse> replyTo;
        public GetJSONData(ActorRef<GetJSONDataResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public final static class ReceiveCombinedData implements Command {
        public final CombinedEngineData combinedData;

        public ReceiveCombinedData(CombinedEngineData combinedData) {
            this.combinedData = combinedData;
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

    public final static class GetJSONDataResponse {
        public final Data data;
        public GetJSONDataResponse(Data data) {
            this.data = data;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressWebHandler(context));
    }

    private int replyId;
    private CombinedEngineData data;

    public StressWebHandler(ActorContext<Command> context) {
        super(context);
        replyId = 0;
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
        public final String treatmentExists;

        @JsonCreator
        public Data(int id,
                    int heartRate,
                    int sleepHours,
                    int previousStressLevel,
                    int currentStressLevel,
                    String calendar,
                    String location,
                    Treatment treatment,
                    String treatmentExists) {
            this.id = id;
            this.heartRate = heartRate;
            this.sleepHours = sleepHours;
            this.previousStressLevel = previousStressLevel;
            this.currentStressLevel = currentStressLevel;
            this.calendar = calendar;
            this.location = location;
            this.treatment = treatment;
            this.treatmentExists = treatmentExists;
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(GetTestJSON.class, this::onGetTestJSON)
            .onMessage(GetJSONData.class, this::onGetJSONData)
            .onMessage(ReceiveCombinedData.class, this::onReceiveCombinedData)
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
                                    exampleTreatment,
                                    "yes");
        GetTestJSONResponse response = new GetTestJSONResponse(exampleData);
        msg.replyTo.tell(response);
        return this;
    }

    private Behavior<Command> onGetJSONData(GetJSONData msg) {
        DetectionData detectionData = this.data.detectionData;
        RecommendationData recommendationData = this.data.recommendationData;
        String treatmentExists = (recommendationData.treatmentDescription != null ? "no" : "yes");

        Treatment treatment = null; //getTreatmentData(recommendationData.treatmentDescription);

        Data information = new Data(
            this.replyId++,
            detectionData.hr.heartrate,
            detectionData.sleep.sleep,
            detectionData.previousStressLevel,
            detectionData.currentStressLevel,
            recommendationData.event,
            recommendationData.location,
            treatment,
            treatmentExists);

        msg.replyTo.tell(new GetJSONDataResponse(information));
        return this;
    }

    private Behavior<Command> onReceiveCombinedData(ReceiveCombinedData msg) {
        //this.data = msg.recommendationData;
        return this;
    }

    private StressWebHandler onPostStop() {
        getContext().getLog().info("Web Handler shutting down");
        return this;
    }

    public final static class Treatment {
        public final String title;
        public final String summary;
        public final String url;

        public Treatment(String title,
                         String summary,
                         String url) {
            this.title = title;
            this.summary = summary;
            this.url = url;
        }
    }

    public static class CombinedEngineData {
        RecommendationData recommendationData;
        DetectionData detectionData;
        public CombinedEngineData(RecommendationData recommendationData, DetectionData detectionData) {
            this.recommendationData = recommendationData;
            this.detectionData = detectionData;
        }
    }

    public static class FrontEndData {
        CombinedEngineData combinedData;
        Treatment treatment;
    }
}
