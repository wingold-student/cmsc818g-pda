package com.cmsc818g.StressDetectionEngine;

import java.util.ArrayList;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
public class StressDetectionEngine extends AbstractBehavior<StressDetectionEngine.Command> {

    public interface Command {}
    public static class detectionEngineGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;
        public final StressManagementController.HealthInformation healthInfo; //send health info to detection engine
        public final ArrayList<String> list;

        public detectionEngineGreet(ActorRef<StressManagementController.Command> ref, 
                StressManagementController.HealthInformation info, ArrayList<String> list) {
          this.replyTo = ref;
          this.healthInfo = info;
          this.list = list;
        }
      }//end of class detectionEngineGreet
 
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressDetectionEngine(context));
    }

    public StressDetectionEngine(ActorContext<Command> context) {
        super(context);
        getContext().getLog().info("context engine actor created");
    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
      .onMessage(detectionEngineGreet.class, this::onEngineResponse)
      .onMessage(SleepReporterToDetection.class, this::onSleepReporterResponse)      
      .onMessage(LocationReporterToDetection.class, this::onLocationReporterResponse)
      .onMessage(ScheduleReporterToDetection.class, this::onScheduleReporterResponse)
      .build();
    }
  
    private Behavior<Command> onEngineResponse(detectionEngineGreet message) { //when receive message
        //get information of connected entities
        //StressManagementController.HealthInformation personalData = new StressManagementController.HealthInformation();
        int level = 100 ; //stress level
        message.replyTo.tell(new StressManagementController.DetectionEngineToController("stressLevel", level));       
      return this;
    }

    private Behavior<Command> onSleepReporterResponse(SleepReporterToDetection response) {
      getContext().getLog().info("Detector Got response from Sleep Reporter: {}", response.sleepHours); 
      return this;
    }

    private Behavior<Command> onLocationReporterResponse(LocationReporterToDetection response) {
      getContext().getLog().info("Detector Got response from Location Reporter: {}", response.location); 
      return this;
    }

    private Behavior<Command> onScheduleReporterResponse(ScheduleReporterToDetection response) {
      getContext().getLog().info("Detector Got response from Schedule Reporter: {}", response.message); 
      return this;
    }


    public static class SleepReporterToDetection implements Command {
      public final int sleepHours; //get sleep hours from sleep reporter

      public SleepReporterToDetection(int sleepHours) {
        this.sleepHours = sleepHours;
      }
    }

    public static class LocationReporterToDetection implements Command {
      public final String location; //get location from location reporter
      public LocationReporterToDetection(String location) {
        this.location = location;
      }
    }

    public static class ScheduleReporterToDetection implements Command {
      public final String message; //get schedule from schedule reporter

      public ScheduleReporterToDetection(String message) {
        this.message = message;
      }
    }
}
