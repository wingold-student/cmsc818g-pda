package com.cmsc818g.StressRecommendationEngine;

import akka.actor.typed.SupervisorStrategy;

import java.util.Optional;

import com.cmsc818g.StressContextEngine.Reporters.LocationReporter;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter.LocationReading;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter.UserLocation;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter.SleepHours;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter.SleepHoursReading;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class RecommendationMetricsAggregator extends AbstractBehavior<RecommendationMetricsAggregator.Command> {
    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}

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

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/
    public interface Response {}

    public static final class AggregatedRecommendationMetrics implements Response {
        public final Optional<SleepHours> sleepReading;
        public final Optional<UserLocation> locReading;

        public AggregatedRecommendationMetrics(
                Optional<SleepHours> sleepReading,
                Optional<UserLocation> locReading
        ) {
            this.sleepReading = sleepReading;
            this.locReading = locReading;
        }
    }

    /************************************* 
     * CREATION 
     *************************************/
    public static Behavior<Command> create(RecommendationMetricsConfig config, ActorRef<AggregatedRecommendationMetrics> replyTo) {
        return Behaviors.<Command>supervise(
            Behaviors.setup(
                context -> new RecommendationMetricsAggregator(context, config, replyTo)
            )
        ).onFailure(Exception.class, SupervisorStrategy.restart());
    }

    private final RecommendationMetricsConfig config;
    private final ActorRef<AggregatedRecommendationMetrics> replyTo;

    private final ActorRef<SleepReporter.SleepHoursReading> sleepAdapter;
    private final ActorRef<LocationReporter.LocationReading> locAdapter;

    private Optional<SleepHours> sleepReading;
    private Optional<UserLocation> locReading;

    private int sleepCount = 0, locCount = 0;

    public RecommendationMetricsAggregator(ActorContext<Command> context, RecommendationMetricsConfig config, ActorRef<AggregatedRecommendationMetrics> replyTo) {
        super(context);
        this.config = config;
        this.replyTo = replyTo;

        sleepAdapter = context.messageAdapter(SleepReporter.SleepHoursReading.class, AdaptedSleepResponse::new);
        locAdapter = context.messageAdapter(LocationReporter.LocationReading.class, AdaptedLocationResponse::new);

        sleepReading = Optional.empty();
        locReading = Optional.empty();

        config.sleepReporter.tell(new SleepReporter.Subscribe(this.sleepAdapter));
        config.locReporter.tell(new LocationReporter.Subscribe(this.locAdapter));
    }


    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(AdaptedSleepResponse.class, this::onAdaptedSleepResponse)
            .onMessage(AdaptedLocationResponse.class, this::onAdaptedLocationResponse)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
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

    public RecommendationMetricsAggregator onPostStop() {
        getContext().getLog().info("Shutting down");
        return this;

    }

    /************************************* 
     * HELPER FUNCTIONS
     *************************************/
    private void sendDataIfComplete() {
        if (sleepCount >= config.sleepCount &&
                locCount >= config.locCount) {

            // TODO: Could just stop the aggregator and respawn on need
            sleepCount = 0;
            locCount = 0;

            replyTo.tell(new AggregatedRecommendationMetrics(sleepReading, locReading));
        }
    }

    /************************************* 
     * HELPER CLASSES
     *************************************/
    public static class RecommendationMetricsConfig {
        public final ActorRef<SleepReporter.Command> sleepReporter;
        public final ActorRef<LocationReporter.Command> locReporter;

        public final int sleepCount;
        public final int locCount;

        public RecommendationMetricsConfig(
            ActorRef<SleepReporter.Command> sleepReporter,
            ActorRef<LocationReporter.Command> locReporter,
            int sleepCount,
            int locCount
        ) {
            this.sleepReporter = sleepReporter;
            this.locReporter = locReporter;
            this.sleepCount = sleepCount;
            this.locCount = locCount;
        }
    }
}