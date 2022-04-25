package com.cmsc818g.StressRecommendationEngine;

import java.util.ArrayList;

import com.cmsc818g.StressManagementController;
import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
/*
message from controller:
    current stress level (in healthInfo structure)
    past stress level
Reporters to send msg to:
    Sleep Reporter
    Location Reporter
    Schedule Reporter
    Media Player(?)
Fetch recommendation from Policy DB
*/

public class StressRecommendationEngine extends AbstractBehavior<StressRecommendationEngine.Command> {

    public interface Command {}
    public static class recommendEngineGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;
        public final StressManagementController.HealthInformation healthInfo; 
        public final ArrayList<String> list;
        public final int pastStressLevel;
        String message ;
        
        public recommendEngineGreet(String message, ActorRef<StressManagementController.Command> replyTo,
        StressManagementController.HealthInformation info, ArrayList<String> list, int level) {
          this.message = message;
          this.replyTo = replyTo;
          this.healthInfo = info;
          this.list = list;
          this.pastStressLevel = level;
        }
      }//end of class recommendEngineGreet

    public static Behavior<Command> create() {
      return Behaviors.setup(context -> new StressRecommendationEngine(context));
    }

    public StressRecommendationEngine(ActorContext<Command> context) {
        super(context);
        getContext().getLog().info("Recommendation Engine actor created"); 
    }
  
    /*
    * receiving responses from reporters
    */
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
      .onMessage(recommendEngineGreet.class, this::onEngineResponse)
      .onMessage(SleepReporterToRecommendation.class, this::onSleepReporterResponse)      
      .onMessage(LocationReporterToRecommendation.class, this::onLocationReporterResponse)
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

    private Behavior<Command> onSleepReporterResponse(SleepReporterToRecommendation response) {
      getContext().getLog().info("Got response from Sleep Reporter: {}", response.sleepHours); 
      return this;
    }

    private Behavior<Command> onLocationReporterResponse(LocationReporterToRecommendation response) {
      getContext().getLog().info("Got response from Location Reporter: {}", response.location); 
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

    public static class SleepReporterToRecommendation implements Command {
      public final int sleepHours; //get sleep hours from sleep reporter

      public SleepReporterToRecommendation(int sleepHours) {
        this.sleepHours = sleepHours;
      }
    }

    public static class LocationReporterToRecommendation implements Command {
      public final String location; //get location from location reporter
      public LocationReporterToRecommendation(String location) {
        this.location = location;
      }
    }

    public static class ScheduleReporterToRecommendation implements Command {
      public final String message; //get schedule from schedule reporter

      public ScheduleReporterToRecommendation(String message) {
        this.message = message;
      }
    }


}
