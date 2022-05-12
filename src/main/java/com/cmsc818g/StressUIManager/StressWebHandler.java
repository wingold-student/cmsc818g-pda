package com.cmsc818g.StressUIManager;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Optional;

import com.cmsc818g.StressContextEngine.Reporters.Reporter;
import com.cmsc818g.StressContextEngine.Reporters.SchedulerReporter;
import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter.BloodPressure;
import com.cmsc818g.StressContextEngine.Reporters.BusynessReporter.BusynessReading;
import com.cmsc818g.StressContextEngine.Reporters.HeartRateReporter.HeartRate;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter.UserLocation;
import com.cmsc818g.StressContextEngine.Reporters.SchedulerReporter.CalendarEvent;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter.SleepHours;
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
import akka.http.javadsl.model.DateTime;

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

    public final static class GetJSONDataNullTreatment implements Command {
        public final ActorRef<GetJSONDataResponse> replyTo;
        public GetJSONDataNullTreatment(ActorRef<GetJSONDataResponse> replyTo) {
            this.replyTo = replyTo;
        }
    }

    public final static class ReceiveCombinedData implements Command {
        public final CombinedEngineData combinedData;

        public ReceiveCombinedData(CombinedEngineData combinedData) {
            this.combinedData = combinedData;
        }
    }

    public final static class ReceiveSchedulerRef implements Command {
        public final ActorRef<Reporter.Command> scheduler;

        public ReceiveSchedulerRef(ActorRef<Reporter.Command> scheduler) {
            this.scheduler = scheduler;
        }
    }

    public final static class AdaptedCurrentEventResponse implements Command {
        public final SchedulerReporter.CurrentEventResponse response;

        public AdaptedCurrentEventResponse(SchedulerReporter.CurrentEventResponse response) {
            this.response = response;
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
    private String date;
    private String time;
    private ActorRef<Reporter.Command> schedulerReporter;
    private final ActorRef<SchedulerReporter.CurrentEventResponse> curEventAdapter;
    private String eventName;

    private HashMap<String, Treatment> treatmentData = new HashMap<String, Treatment>();

    public StressWebHandler(ActorContext<Command> context) {
        super(context);
        replyId = 0;

        treatmentData.put("deep breathing exercise",
                        new Treatment(
                            1,
                            "Breathing exercise",
                            "A deep breathing exercise",
                            "img/deep_breathing_exercise.gif"));


        treatmentData.put("meditation",
                        new Treatment(
                            2,
                            "Meditation Guide",
                            "Guide to Meditation",
                            "img/meditation.jpg"));

        treatmentData.put("work out",
                        new Treatment(
                            3,
                            "Try Working Out",
                            "Working out",
                            "img/workout.jpg"));

        treatmentData.put("take a walk",
                        new Treatment(
                            4,
                            "Try Taking a Walk",
                            "Taking a walk can help",
                            "img/take_a_walk.jpg"));

        treatmentData.put("mindfulness meditation",
                        new Treatment(
                            5,
                            "Mindfullness Meditation",
                            "Take some time to inspect your mind",
                            "img/mindfulness_meditation.jpg"));


        treatmentData.put("cancel plans",
                        new Treatment(
                            6,
                            "Cancel Plans",
                            "You may want to cancel some plans to give you some time to breathe",
                            "img/cancel_plans.jpg"));

        treatmentData.put("cancel plans and get some sleep",
                        new Treatment(
                            7,
                            "Cancel Plans, You Need Sleep",
                            "You may want to cancel some plans and get some sleep",
                            "img/cancel_plans_and_get_some_sleep.jpg"));

        treatmentData.put("relaxation to music",
                        new Treatment(
                            8,
                            "Relaxing Music",
                            "Try some relaxing music to soothe your mind",
                            "img/relaxation_to_music.jpg"));

        treatmentData.put("contact therapist or close friends and family",
                        new Treatment(
                            9,
                            "Give your Therapist or Close Friend/Family a Call",
                            "Try giving a call to someone for support",
                            "img/contact.jpg"));

        RecommendationData tmpRecommendation = new RecommendationData("meeting", "work", "bad", ""); 
        DetectionData tmpDetection = new DetectionData(new BloodPressure(Optional.of(""), 120, 80),
                                                        new HeartRate(Optional.of(""), 0),
                                                        new SleepHours(Optional.of(""), 0),
                                                        new UserLocation(Optional.of(""), ""),
                                                        new BusynessReading(Optional.of(0)),
                                                        0,
                                                        0);
        this.data = new CombinedEngineData(tmpRecommendation, tmpDetection);
        this.time = "8:00";
        this.date = "05/12/22";

        curEventAdapter = context.messageAdapter(SchedulerReporter.CurrentEventResponse.class, AdaptedCurrentEventResponse::new);
    }

    /** TestData is just an example class for holding JSON data. */
    public final static class Data {
        public final int id;
        public final int heartRate;
        public final int systolicBP;
        public final int diastolicBP;
        public final int sleepHours;
        public final int previousStressLevel;
        public final int currentStressLevel;
        public final int busynessLevel;
        public final String date;
        public final String time;
        public final String calendar;
        public final String location;
        public final Treatment treatment;
        public final String treatmentExists;

        @JsonCreator
        public Data(int id,
                    int heartRate,
                    int systolicBP,
                    int diastolicBP,
                    int sleepHours,
                    int busynessLevel,
                    String date,
                    String time,
                    int previousStressLevel,
                    int currentStressLevel,
                    String calendar,
                    String location,
                    Treatment treatment,
                    String treatmentExists) {
            this.id = id;
            this.heartRate = heartRate;
            this.systolicBP = systolicBP;
            this.diastolicBP = diastolicBP;
            this.sleepHours = sleepHours;
            this.busynessLevel = busynessLevel;
            this.date = date;
            this.time = time;
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
            .onMessage(GetJSONDataNullTreatment.class, this::onGetJSONDataNullTreatment)
            .onMessage(ReceiveCombinedData.class, this::onReceiveCombinedData)
            .onMessage(ReceiveSchedulerRef.class, this::onReceiveSchedulerRef)
            .onMessage(AdaptedCurrentEventResponse.class, this::onAdaptedCurrentEventResponse)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Command> onGetTestJSON(GetTestJSON msg) {
        Treatment exampleTreatment = new Treatment(0,"title", "summary", "url");
        Data exampleData = new Data(0,
                                    0,
                                   0,
                                    0,
                                    0,
                                    0,
                                    "",
                                    "",
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

        Treatment treatment = this.treatmentData.getOrDefault(recommendationData.treatmentDescription, null);
        String treatmentExists = (treatment == null ? "no" : "yes");

        Data information = new Data(
            this.replyId++,
            detectionData.hr.heartrate,
            detectionData.bp.systolic,
            detectionData.bp.diastolic,
            detectionData.sleep.sleep,
            detectionData.busy.level.get(),
            this.date,
            this.time,
            detectionData.previousStressLevel,
            detectionData.currentStressLevel,
            this.eventName,
            recommendationData.location,
            treatment,
            treatmentExists);

        msg.replyTo.tell(new GetJSONDataResponse(information));
        return this;
    }


    private Behavior<Command> onGetJSONDataNullTreatment(GetJSONDataNullTreatment msg) {
        DetectionData detectionData = this.data.detectionData;
        RecommendationData recommendationData = this.data.recommendationData;

        Treatment treatment = null;
        String treatmentExists = (treatment == null ? "no" : "yes");

        Data information = new Data(
            this.replyId++,
            detectionData.hr.heartrate,
            detectionData.bp.systolic,
            detectionData.bp.diastolic,
            detectionData.sleep.sleep,
            detectionData.busy.level.get(),
            this.date,
            this.time,
            detectionData.previousStressLevel,
            detectionData.currentStressLevel,
            this.eventName,
            recommendationData.location,
            treatment,
            treatmentExists);

        msg.replyTo.tell(new GetJSONDataResponse(information));
        return this;
    }

    private Behavior<Command> onReceiveSchedulerRef(ReceiveSchedulerRef msg) {
        getContext().getLog().debug("Telling scheduler I am subscribing");
        this.schedulerReporter = msg.scheduler;
        this.schedulerReporter.tell(new SchedulerReporter.SubscribeForCurrentEvent(this.curEventAdapter));
        return this;
    }

    private Behavior<Command> onReceiveCombinedData(ReceiveCombinedData msg) {
        this.data = msg.combinedData;
        return this;
    }

    private Behavior<Command> onAdaptedCurrentEventResponse(AdaptedCurrentEventResponse wrapped) {
        SchedulerReporter.CurrentEventResponse response = wrapped.response;
        CalendarEvent event = response.event.orElse(new CalendarEvent("", DateTime.now(), Duration.ofMinutes(30L), "personal"));
        getContext().getLog().debug("Got current event in UI");

        this.date = String.format("%02d/%02d/%02d", event.datetime.month(), event.datetime.day(), event.datetime.year());
        this.time = String.format("%02d:%02d", event.datetime.hour(), event.datetime.minute());
        this.eventName = event.eventName;

        return this;
    }

    private StressWebHandler onPostStop() {
        getContext().getLog().info("Web Handler shutting down");
        return this;
    }

    public final static class Treatment {
        public final int id;
        public final String title;
        public final String summary;
        public final String url;

        public Treatment(int id,
                         String title,
                         String summary,
                         String url) {
            this.title = title;
            this.id = id;
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
