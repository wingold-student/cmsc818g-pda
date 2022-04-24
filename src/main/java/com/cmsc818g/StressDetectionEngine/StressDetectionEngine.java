package com.cmsc818g.StressDetectionEngine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter;
import com.cmsc818g.StressContextEngine.Reporters.Reporter;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter;
import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter.BloodPressure;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
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

    /**
     * A wrapper class around the `BloodPressureReading` so you can still 'receive' it through the ask.
     * Since the `StressDetectionEngine` cannot directly receive a `BloodPressureReading`.
     */
    public static final class AdaptedBloodPressure implements Command {
      final BloodPressureReporter.BloodPressureReading response;

      public AdaptedBloodPressure(BloodPressureReporter.BloodPressureReading response) {
        this.response = response;
      }
    }

    public static final class AdaptedListing implements Command {
      final Receptionist.Listing response;

      public AdaptedListing(Receptionist.Listing response) {
        this.response = response;
      }
    }
 
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressDetectionEngine(context));
    }

    private final ActorRef<BloodPressureReporter.BloodPressureReading> bpAdapter;
    private final ServiceKey<Reporter.Command> bpKey;
    private ActorRef<Reporter.Command> bpReporter;

    
    public StressDetectionEngine(ActorContext<Command> context) {
        super(context);
        context.getLog().info("context engine actor created");

        /**
         * Could we have been handed the ActorRef for BloodPressure in a message? Sure...
         * but this is another way
         */
        this.bpKey = ServiceKey.create(Reporter.Command.class, "BloodPressure");
        this.bpAdapter = context.messageAdapter(BloodPressureReporter.BloodPressureReading.class, AdaptedBloodPressure::new);
        this.bpReporter = null;

        context.ask(
          Receptionist.Listing.class,
          context.getSystem().receptionist(),
          Duration.ofSeconds(3L),
          (ActorRef<Receptionist.Listing> ref) -> Receptionist.find(bpKey, ref),
          (response, throwable) -> {
            return new AdaptedListing(response);
          }
        );

        /**
         * This is an example, do not actually spawn the blood pressure reporter here.
         * 
         * Comment out if you want to run without errors
         */
        //ActorRef<Reporter.Command> bpReporter = context.spawn(BloodPressureReporter.create("", ""), "FOR_EXAMPLE_ONLY");

        /**
         * Nor should you ask it within the constructor. This is just for ease.
         * 
         * This is the format of 'asking' the BloodPressureReporter for the last reading.
         * Comment out if you want to run without errors
         */
        /*
        context.ask(
          BloodPressureReporter.BloodPressureReading.class, // What type of message am I expecting back?
          bpReporter, // Who am I asking?
          Duration.ofSeconds(3L), // How long do I wait before throwing in the towel
          (ActorRef<BloodPressureReporter.BloodPressureReading> ref) -> new BloodPressureReporter.ReadBloodPressure(ref), // Sending the request with the appropriate ActorRef
           // This is a callback of sorts, which will be run when/if you get a response.
           // Response will == null if no response. throwable will have an exception if something bad
           // happened when asking it
          (response, throwable) -> {
            // Annoyingly, you kinda have to wrap the response into your (StressDetectorEngine) message protocol
            return new AdaptedBloodPressure(response);
          }
        );
        */
    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
      .onMessage(detectionEngineGreet.class, this::onEngineResponse)
      .onMessage(SleepReporterToDetection.class, this::onSleepReporterResponse)      
      .onMessage(LocationReporterToDetection.class, this::onLocationReporterResponse)
      .onMessage(ScheduleReporterToDetection.class, this::onScheduleReporterResponse)
      .onMessage(AdaptedBloodPressure.class, this::onAdaptedBloodPressure)
      .onMessage(AdaptedListing.class, this::onAdaptedListing)
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

    /**
     * This is just an example of how you might access the data from the reading
     * @param wrapped
     * @return
     */
    private Behavior<Command> onAdaptedBloodPressure(AdaptedBloodPressure wrapped) {

      if (wrapped.response == null) {
        getContext().getLog().error("Failed to get blood pressure reading");
      } else {
        BloodPressureReporter.BloodPressureReading response = wrapped.response;
        Optional<BloodPressureReporter.BloodPressure> optBp = response.value;

        if (optBp.isPresent()) {
          BloodPressure bp = optBp.get();
          getContext().getLog().info("Got BP reading: " + bp);
        }
      }

      return this;
    }

    private Behavior<Command> onAdaptedListing(AdaptedListing wrapped) {
      Receptionist.Listing response = wrapped.response;

      if (response.isForKey(bpKey)) {
        getContext().getLog().info("Got blood pressure reporter");
        Set<ActorRef<Reporter.Command>> services = response.getServiceInstances(this.bpKey);
        services.forEach((service) -> this.bpReporter = service);
        this.bpReporter.tell(new BloodPressureReporter.Subscribe(this.bpAdapter));
      }

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
