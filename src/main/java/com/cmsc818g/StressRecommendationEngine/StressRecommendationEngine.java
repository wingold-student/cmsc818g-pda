package com.cmsc818g.StressRecommendationEngine;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Optional;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter.UserLocation;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter.SleepHours;
import com.cmsc818g.StressRecommendationEngine.RecommendationMetricsAggregator.AggregatedRecommendationMetrics;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
/*
message from controller:
    current stress level 
    past stress level
Reporters to send msg to:
    Sleep Reporter
    Location Reporter
    Schedule Reporter
    Media Player(?)
Fetch recommendation from Policy DB
*/

public class StressRecommendationEngine extends AbstractBehavior<StressRecommendationEngine.Command> {

    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}

    public static class ScheduleReporterToRecommendation implements Command {
      public final String message; //get schedule from schedule reporter

      public ScheduleReporterToRecommendation(String message) {
        this.message = message;
      }
    }
    public static class recommendEngineGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;
        public final ArrayList<String> list;
        public final int pastStressLevel;
        public final int currentStressLevel;
        String message ;
        
        public recommendEngineGreet(String message, ActorRef<StressManagementController.Command> replyTo,
        ArrayList<String> list, int past, int curr) {
          this.message = message;
          this.replyTo = replyTo;
          this.list = list;
          this.pastStressLevel = past;
          this.currentStressLevel = curr;
        }
      }//end of class recommendEngineGreet

    public static final class AdaptedAggreatedMetrics implements Command {
        public final AggregatedRecommendationMetrics response;

        public AdaptedAggreatedMetrics(AggregatedRecommendationMetrics response) {
            this.response = response;
        }
    }

    /************************************* 
     * MESSAGES IT SENDS
     *************************************/

    /************************************* 
     * CREATION 
     *************************************/
    public static Behavior<Command> create(String configFilename) {
      return Behaviors.setup(context -> new StressRecommendationEngine(context, configFilename));
    }

    private final ActorRef<RecommendationMetricsAggregator.Command> aggregator;
    private final ActorRef<RecommendationMetricsAggregator.AggregatedRecommendationMetrics> aggregatorAdapter;

    private Optional<SleepHours> sleepReading;
    private Optional<UserLocation> locReading;
    private boolean haveMetrics = false;

    public StressRecommendationEngine(ActorContext<Command> context, String configFilename) throws StreamReadException, DatabindException, IOException {
        super(context);
        getContext().getLog().info("Recommendation Engine actor created"); 

        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        InputStream cfgFilestream = getClass().getClassLoader().getResourceAsStream(configFilename);
        RecommendationConfig cfg = yamlReader.readValue(cfgFilestream, RecommendationConfig.class);

        // TODO: Temporary
        RecommendationMetricsConfig config = new RecommendationMetricsConfig(
          cfg.detectionMetricsCounts,
          null,
          null
          );
        
        this.aggregatorAdapter = context.messageAdapter(RecommendationMetricsAggregator.AggregatedRecommendationMetrics.class, AdaptedAggreatedMetrics::new);

        // TODO: Note this starts it immediately
        this.aggregator = context.spawn(RecommendationMetricsAggregator.create(config, this.aggregatorAdapter), "RecommednationAggregator");
    }
  
    /************************************* 
     * MESSAGE HANDLING 
     *************************************/

    /*
    * receiving responses from reporters
    */
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
      .onMessage(recommendEngineGreet.class, this::onEngineResponse)
      .onMessage(AdaptedAggreatedMetrics.class, this::onAggregatedMetrics)
      .onMessage(ScheduleReporterToRecommendation.class, this::onScheduleReporterResponse)
      .onSignal(PostStop.class, signal -> onPostStop())
      .build();
    }
  
    private Behavior<Command> onEngineResponse(recommendEngineGreet response) { //when receive message
      if(response.message == "recommend"){
          //recommend treatment     
          response.replyTo.tell(new StressManagementController.RecommendEngineToController("recommendation")); 
          //this.tell(new SleepReporter.AskSleepHours(getContext().getSelf()))
      }
      return this;
    }

    private Behavior<Command> onAggregatedMetrics(AdaptedAggreatedMetrics wrapped) {
      AggregatedRecommendationMetrics metrics = wrapped.response;

      sleepReading = metrics.sleepReading;
      locReading = metrics.locReading;

      // TODO: Somewhat temporary. Could instead now call the actual recommendation algorithm
      haveMetrics = true;
      return this;
    }

    private Behavior<Command> onScheduleReporterResponse(ScheduleReporterToRecommendation response) {
      getContext().getLog().info("Got response from Schedule Reporter: {}", response.message); 
      return this;
    }

    private StressRecommendationEngine onPostStop() {
      getContext().getLog().info("Recommendation Engine stopped");
      return this;
  }

    /************************************* 
     * HELPER FUNCTIONS
     *************************************/

    /************************************* 
     * HELPER CLASSES
     *************************************/
    public static class RecommendationMetricsCounts {
        public int sleepCount;
        public int locCount;
    }
    public static class RecommendationConfig {
      public RecommendationMetricsCounts detectionMetricsCounts;
    }
    public static class RecommendationMetricsConfig {
        public final ActorRef<SleepReporter.Command> sleepReporter;
        public final ActorRef<LocationReporter.Command> locReporter;

        public final RecommendationMetricsCounts countCfg;

        public RecommendationMetricsConfig(
            RecommendationMetricsCounts countCfg,
            ActorRef<SleepReporter.Command> sleepReporter,
            ActorRef<LocationReporter.Command> locReporter
        ) {
            this.countCfg = countCfg;
            this.sleepReporter = sleepReporter;
            this.locReporter = locReporter;
        }
    }

}
