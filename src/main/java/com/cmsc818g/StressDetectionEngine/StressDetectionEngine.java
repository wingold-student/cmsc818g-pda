package com.cmsc818g.StressDetectionEngine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter;
import com.cmsc818g.StressContextEngine.Reporters.Reporter;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
/*
message from controller:
    Detection engine start
Collect data from:
    reporters  
Send to controller: 
    Collected Health Information
    Stress level
*/

public class StressDetectionEngine extends AbstractBehavior<StressDetectionEngine.Command> {

    public interface Command {}
    public static int stressLevel;
    public static int diastolicBP;
    public static int systolicBP;
    public static int heartRate;

    public static class detectionEngineGreet implements Command {
        public final ActorRef<StressManagementController.Command> replyTo;
        public final ArrayList<String> list;
        public String message; 

        public detectionEngineGreet(String message, ActorRef<StressManagementController.Command> ref,  ArrayList<String> list) {
          this.message = message;
          this.replyTo = ref;
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
 
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressDetectionEngine(context));
    }

    ActorRef<BloodPressureReporter.Response> bpAdapter;
    public StressDetectionEngine(ActorContext<Command> context) {
        super(context);
        context.getLog().info("context engine actor created");

        /**
         * This is an example, do not actually spawn the blood pressure reporter here.
         * 
         * Comment out if you want to run without errors
         */
        ActorRef<Reporter.Command> bpReporter = context.spawn(BloodPressureReporter.create("", ""), "FOR_EXAMPLE_ONLY");

        /**
         * Nor should you ask it within the constructor. This is just for ease.
         * 
         * This is the format of 'asking' the BloodPressureReporter for the last reading.
         * Comment out if you want to run without errors
         */
        context.ask(
          BloodPressureReporter.BloodPressureReading.class, // What type of message am I expecting back?
          bpReporter, // Who am I asking?
          Duration.ofSeconds(3L), // How long do I wait before throwing in the towel
          (ActorRef<BloodPressureReporter.BloodPressureReading> ref) -> new BloodPressureReporter.ReadBloodPressure(ref), // Sending the request with the appropriate ActorRef
          /**
           * This is a callback of sorts, which will be run when/if you get a response.
           * 
           * Response will == null if no response. throwable will have an exception if something bad
           * happened when asking it
           */
          (response, throwable) -> {
            // Annoyingly, you kinda have to wrap the response into your (StressDetectorEngine) message protocol
            return new AdaptedBloodPressure(response);
          }
        );
    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
      .onMessage(detectionEngineGreet.class, this::onEngineResponse)
      .onMessage(SleepReporterToDetection.class, this::onSleepReporterResponse)      
      .onMessage(LocationReporterToDetection.class, this::onLocationReporterResponse)
      .onMessage(ScheduleReporterToDetection.class, this::onScheduleReporterResponse)
      .onMessage(AdaptedBloodPressure.class, this::onAdaptedBloodPressure)
      .onSignal(PostStop.class, signal -> onPostStop())
      .build();
    }
  
    private Behavior<Command> onEngineResponse(detectionEngineGreet response) { 
        //query reporters 
      if(response.message == "detect"){
        int detected_level = knnPrediction(); //stress detection + measurement process
        getContext().getLog().info("Detection engine's stress level: "+ detected_level); 
        response.replyTo.tell(new StressManagementController.DetectionEngineToController("healthInfo", detected_level));       
      }
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

    private StressDetectionEngine onPostStop() {
      getContext().getLog().info("Detection Engine stopped");
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
        Optional<BloodPressureReporter.BloodPressure> bp = response.value;
        if(bp.isPresent()){
          diastolicBP = bp.get().getDiastolicBP();
          systolicBP = bp.get().getSystolicBP();
          getContext().getLog().info("DiastolicBP:" , diastolicBP, "SystolicBP:" , systolicBP);
        }
      }//end of else
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

    private Behavior<Command> stressMeasurementProcess(){ 
      //stress detection process
      getContext().getLog().info("BloodPressure: diastolicBP: "+ diastolicBP + ",  systolicBP " + systolicBP);
      //Blood Pressure 
      if((diastolicBP > 120 || systolicBP > 180)||
              (diastolicBP > 120 && systolicBP > 180)) { //emergency detected 
                stressLevel = 5;
      }
      else if((diastolicBP >= 90 && diastolicBP <= 120)||
              (systolicBP >= 140 && systolicBP <= 180)) { // Hypertension stage 2
                stressLevel = 4;
      }
      else if((diastolicBP >= 80 && diastolicBP < 90)||
              (systolicBP >= 130&& systolicBP < 140)) { // Hypertension stage 1
                stressLevel = 3;
      }
      else if(diastolicBP < 80 &&
             (systolicBP >= 120 && systolicBP < 130)) {  // Elevated
              stressLevel = 2;
      }
      else if(diastolicBP < 80 && systolicBP < 120) { // Normal 
        stressLevel = 1;
      }
      return this;
    }

    private int knnPrediction(){
        getContext().getLog().info("knn measure started ");

        try{
          //ProcessBuilder pb = new ProcessBuilder(Arrays.asList("<Absolute Path to Python>/python", pythonPath));
          String path = "/Users/yoonie/Desktop/test/cmsc818g-pda/src/main/java/com/cmsc818g/KNN.py";
          ProcessBuilder pb = new ProcessBuilder("python3", path, "3", "2", "132", "80", "80"); // sleep-hour, busyness, bp-systolic, bp-diastolic, heart-rate
          Process process = pb.start();

          BufferedReader bfr = new BufferedReader(new InputStreamReader(process.getInputStream()));
          String line = "";
     
          process.waitFor(); 
          int len;
          if ((len = process.getErrorStream().available()) > 0) {
              byte[] buf = new byte[len];
              process.getErrorStream().read(buf);
              System.err.println("Command error:\""+new String(buf)+"\"");
          }
          line = bfr.readLine();
          System.out.println("KNN output : " + line); // [2.]
          String python_output = line;

          return Integer.parseInt(String.valueOf(python_output.charAt(1)));

          //String parser example
          // char[] char_arr = new char[python_output.length()];
          // for(int i=0 ; i<char_arr.length ; i++ ){
          //   char_arr[i] = python_output.charAt(i);
          //   System.out.println("char_arr[" +i +"]: " + char_arr[i]);
          // }
          // return char_arr[1];

          // while ((line = bfr.readLine()) != null){
          //     System.out.println("KNN Output: " + line);
          // }
      }catch(Exception e){
          System.out.println(e);
      }
      return 0;
    }
}
