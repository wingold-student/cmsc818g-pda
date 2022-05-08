package com.cmsc818g.StressDetectionEngine;

import akka.actor.typed.SupervisorStrategy;

import java.util.Optional;

import com.cmsc818g.StressContextEngine.Reporters.*;
import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter.BloodPressure;
import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter.BloodPressureReading;
import com.cmsc818g.StressContextEngine.Reporters.BusynessReporter.BusynessLevelResponse;
import com.cmsc818g.StressContextEngine.Reporters.BusynessReporter.BusynessReading;
import com.cmsc818g.StressContextEngine.Reporters.HeartRateReporter.HeartRate;
import com.cmsc818g.StressContextEngine.Reporters.HeartRateReporter.HeartRateReading;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter.LocationReading;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter.UserLocation;
import com.cmsc818g.StressContextEngine.Reporters.MedicalHistoryReporter.QueryResponse;
import com.cmsc818g.StressContextEngine.Reporters.SchedulerReporter.CurrentEventResponse;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter.SleepHours;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter.SleepHoursReading;
import com.cmsc818g.StressDetectionEngine.StressDetectionEngine.DetectionMetricsConfig;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class DetectionMetricsAggregator extends AbstractBehavior<DetectionMetricsAggregator.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}

    public static final class AdaptedBloodPressureResponse implements Command {
        final BloodPressureReading response;

        public AdaptedBloodPressureResponse(BloodPressureReporter.BloodPressureReading response) {
            this.response = response;
        }
    }

    public static final class AdaptedHeartRateResponse implements Command {
        final HeartRateReading response;

        public AdaptedHeartRateResponse(HeartRateReading response) {
            this.response = response;
        }
    }

    public static final class AdaptedSchedulerCurrentEvent implements Command {
        final CurrentEventResponse response;
        
        public AdaptedSchedulerCurrentEvent(CurrentEventResponse response) {
            this.response = response;
        }
    }

    public static final class AdaptedSleepResponse implements Command {
        final SleepHoursReading response;

        public AdaptedSleepResponse(SleepHoursReading response) {
            this.response = response;
        }
    }

    public static final class AdaptedLocationResponse implements Command {
        final LocationReading response;

        public AdaptedLocationResponse(LocationReading response) {
            this.response = response;
        }
    }

    public static final class AdaptedBusynessResponse implements Command {
        final BusynessLevelResponse response;

        public AdaptedBusynessResponse(BusynessLevelResponse response) {
            this.response = response;
        }
    }


    public static final class AdaptedMedicalHistoryResponse implements Command {
        final QueryResponse response;

        public AdaptedMedicalHistoryResponse(QueryResponse response) {
            this.response = response;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response {}

    public static final class AggregatedStressMetrics implements Response {
        public final Optional<BloodPressure> bpReading;
        public final Optional<HeartRate> hrReading;
        public final Optional<SleepHours> sleepReading;
        public final Optional<UserLocation> locReading;
        public final Optional<BusynessReading> busyReading;
        public final Optional<String> medicalReading;

        public AggregatedStressMetrics(
                Optional<BloodPressure> bpReading,
                Optional<HeartRate> hrReading,
                Optional<SleepHours> sleepReading,
                Optional<UserLocation> locReading,
                Optional<BusynessReading> busyReading,
                Optional<String> medicalReading
        ) {
            this.bpReading = bpReading;
            this.hrReading = hrReading;
            this.sleepReading = sleepReading;
            this.locReading = locReading;
            this.busyReading = busyReading;
            this.medicalReading = medicalReading;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/
    public static Behavior<Command> create(ActorRef<AggregatedStressMetrics> replyTo, DetectionMetricsConfig config) {
        return Behaviors.<Command>supervise(
            Behaviors.setup(
                context -> new DetectionMetricsAggregator(context, replyTo, config)
            )
        ).onFailure(Exception.class, SupervisorStrategy.restart());
    }

    private final ActorRef<AggregatedStressMetrics> replyTo;
    private final ActorRef<BloodPressureReporter.BloodPressureReading> bpAdapter;
    private final ActorRef<HeartRateReporter.HeartRateReading> hrAdapter;
    private final ActorRef<SleepReporter.SleepHoursReading> sleepAdapter;
    private final ActorRef<LocationReporter.LocationReading> locAdapter;
    private final ActorRef<BusynessReporter.BusynessLevelResponse> busyAdapter;
    private final ActorRef<MedicalHistoryReporter.QueryResponse> medicalAdapter;
    private final DetectionMetricsConfig config;

    private int bpCount = 0, hrCount = 0, sleepCount = 0, locCount = 0, busyCount = 0, medicalCount = 0;
    private Optional<BloodPressure> bpReading;
    private Optional<HeartRate> hrReading;
    private Optional<SleepHours> sleepReading;
    private Optional<UserLocation> locReading;
    private Optional<BusynessReading> busyReading;
    private Optional<String> medicalReading;

    public DetectionMetricsAggregator(ActorContext<Command> context, ActorRef<AggregatedStressMetrics> replyTo, DetectionMetricsConfig config) {
        super(context);
        this.replyTo = replyTo;
        this.config = config;

        bpAdapter = context.messageAdapter(BloodPressureReporter.BloodPressureReading.class, AdaptedBloodPressureResponse::new);
        hrAdapter = context.messageAdapter(HeartRateReporter.HeartRateReading.class, AdaptedHeartRateResponse::new);
        sleepAdapter = context.messageAdapter(SleepReporter.SleepHoursReading.class, AdaptedSleepResponse::new);
        locAdapter = context.messageAdapter(LocationReporter.LocationReading.class, AdaptedLocationResponse::new);
        busyAdapter = context.messageAdapter(BusynessReporter.BusynessLevelResponse.class, AdaptedBusynessResponse::new);
        medicalAdapter = context.messageAdapter(MedicalHistoryReporter.QueryResponse.class, AdaptedMedicalHistoryResponse::new);

        bpReading = Optional.empty();
        hrReading = Optional.empty();
        sleepReading = Optional.empty();
        locReading = Optional.empty();
        busyReading = Optional.empty();
        medicalReading = Optional.empty();

        config.bpReporter.tell(new BloodPressureReporter.Subscribe(this.bpAdapter));       
        config.hrReporter.tell(new HeartRateReporter.Subscribe(this.hrAdapter));
        config.sleepReporter.tell(new SleepReporter.Subscribe(this.sleepAdapter));
        config.locReporter.tell(new LocationReporter.Subscribe(this.locAdapter));

        // TODO: Query medical for some sort of data?
        config.medicalReporter.tell(new MedicalHistoryReporter.SendQuery("", this.medicalAdapter));
        config.busyReporter.tell(new BusynessReporter.GetCurrentBusynessLevel(this.busyAdapter));
    }


    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AdaptedBloodPressureResponse.class, this::onAdaptedBloodPressureResponse)
            .onMessage(AdaptedHeartRateResponse.class, this::onAdaptedHeartRateResponse)
            .onMessage(AdaptedSleepResponse.class, this::onAdaptedSleepResponse)
            .onMessage(AdaptedLocationResponse.class, this::onAdaptedLocationResponse)
            .onMessage(AdaptedBusynessResponse.class, this::onAdaptedBusynessResponse)
            .onMessage(AdaptedMedicalHistoryResponse.class, this::onAdaptedMedicalHistoryResponse)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    public Behavior<Command> onAdaptedBloodPressureResponse(AdaptedBloodPressureResponse response) {
        bpReading = response.response.value;
        bpCount++;
        sendDataIfComplete();
        return this;
    }

    public Behavior<Command> onAdaptedHeartRateResponse(AdaptedHeartRateResponse response) {
        hrReading = response.response.value;
        hrCount++;
        sendDataIfComplete();
        return this;
    }

    public Behavior<Command> onAdaptedSleepResponse(AdaptedSleepResponse response) {
        sleepReading = response.response.value;
        sleepCount++;
        sendDataIfComplete();
        return this;
    }
    
    public Behavior<Command> onAdaptedLocationResponse(AdaptedLocationResponse response) {
        locReading = response.response.value;
        locCount++;
        sendDataIfComplete();
        return this;
    }

    public Behavior<Command> onAdaptedBusynessResponse(AdaptedBusynessResponse response) {
        busyReading = response.response.busynessLevel;
        busyCount++;
        sendDataIfComplete();
        return this;
    }

    public Behavior<Command> onAdaptedMedicalHistoryResponse(AdaptedMedicalHistoryResponse response) {
        medicalReading = response.response.results;
        medicalCount++;
        sendDataIfComplete();
        return this;
    }

    public DetectionMetricsAggregator onPostStop() {
        getContext().getLog().info("Shutting down");
        return this;

    }

    /************************************* 
     * HELPER FUNCTIONS
     *************************************/
    private void sendDataIfComplete() {
        if (bpCount >= config.countCfg.bpCount &&
                hrCount >= config.countCfg.hrCount &&
                sleepCount >= config.countCfg.sleepCount &&
                busyCount >= config.countCfg.busyCount &&
                locCount >= config.countCfg.locCount &&
                medicalCount >= config.countCfg.medicalCount) {

            // TODO: Could just stop the aggregator and respawn on need
            bpCount = 0;
            hrCount = 0;
            sleepCount = 0;
            busyCount = 0;
            locCount = 0;
            medicalCount = 0;

            replyTo.tell(new AggregatedStressMetrics(bpReading, hrReading, sleepReading, locReading, busyReading, medicalReading));
        }
    }
    /************************************* 
     * HELPER CLASSES
     *************************************/

}
