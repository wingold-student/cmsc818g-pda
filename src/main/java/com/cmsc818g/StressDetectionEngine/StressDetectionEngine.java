package com.cmsc818g.StressDetectionEngine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressContextEngine.Reporters.*;
import com.cmsc818g.StressContextEngine.Reporters.Reporter;
import com.cmsc818g.StressDetectionEngine.DetectionMetricsAggregator.*;
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
    Detection engine start
Collect data from:
    reporters  
Send to controller: 
    Collected Health Information
    Stress level
*/

public class StressDetectionEngine extends AbstractBehavior<StressDetectionEngine.Command> {

    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/
    public interface Command {}

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

    public static final class AdaptedAggreatedMetrics implements Command {
        public final AggregatedStressMetrics response;

        public AdaptedAggreatedMetrics(AggregatedStressMetrics response) {
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
        return Behaviors.setup(context -> new StressDetectionEngine(context, configFilename));
    }

    private final ActorRef<DetectionMetricsAggregator.Command> aggregator;
    private final ActorRef<DetectionMetricsAggregator.AggregatedStressMetrics> aggregatorAdapter;

    public StressDetectionEngine(ActorContext<Command> context, String configFilename) throws StreamReadException, DatabindException, IOException {
        super(context);
        context.getLog().info("context engine actor created");

        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        InputStream cfgFilestream = getClass().getClassLoader().getResourceAsStream(configFilename);
        DetectionConfig cfg = yamlReader.readValue(cfgFilestream, DetectionConfig.class);
        context.getLog().info("HR Count: {}", cfg.detectionMetricsCounts.hrCount);

        // TODO: Temporary
        DetectionMetricsConfig metricsConfig = new DetectionMetricsConfig(
            cfg.detectionMetricsCounts,
            null,
            null,
            null,
            null,
            null,
            null
        );

        this.aggregatorAdapter = context.messageAdapter(DetectionMetricsAggregator.AggregatedStressMetrics.class, AdaptedAggreatedMetrics::new);

        // TODO: Note this starts it immediately
        this.aggregator = context.spawn(DetectionMetricsAggregator.create(this.aggregatorAdapter, metricsConfig), "DetectionAggregator");
    }
  

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
      .onMessage(detectionEngineGreet.class, this::onEngineResponse)
      .onMessage(AdaptedAggreatedMetrics.class, this::StressMeasurementProcess)
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
    
    private StressDetectionEngine onPostStop() {
      getContext().getLog().info("Detection Engine stopped");
      return this;
    }


    /************************************* 
     * HELPER FUNCTIONS
     *************************************/
    private Behavior<Command> StressMeasurementProcess(AdaptedAggreatedMetrics wrapped){ 
        AggregatedStressMetrics metrics = wrapped.response;
        int stressLevel = 0;

        int diastolicBP = metrics.bpReading.get().getDiastolicBP();
        int systolicBP = metrics.bpReading.get().getSystolicBP();
        int heartRate = metrics.hrReading.get().getheartrate();

        // TODO: Get the rest of the metrics out as well to use

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

    /************************************* 
     * HELPER CLASSES
     *************************************/
    public static class DetectionMetricsCounts {
        public int bpCount;
        public int hrCount;
        public int sleepCount;
        public int locCount;
        public int busyCount;
        public int medicalCount;
    }
    public static class DetectionConfig {
      public DetectionMetricsCounts detectionMetricsCounts;
    }
    public static class DetectionMetricsConfig {
        public final DetectionMetricsCounts countCfg;
        public final ActorRef<BloodPressureReporter.Command> bpReporter;
        public final ActorRef<HeartRateReporter.Command> hrReporter;
        public final ActorRef<SleepReporter.Command> sleepReporter;
        public final ActorRef<LocationReporter.Command> locReporter;
        public final ActorRef<BusynessReporter.Command> busyReporter;
        public final ActorRef<MedicalHistoryReporter.Command> medicalReporter;

        public DetectionMetricsConfig(
            DetectionMetricsCounts countCfg,
            ActorRef<BloodPressureReporter.Command> bpReporter,
            ActorRef<HeartRateReporter.Command> hrReporter,
            ActorRef<SleepReporter.Command> sleepReporter,
            ActorRef<LocationReporter.Command> locReporter,
            ActorRef<BusynessReporter.Command> busyReporter,
            ActorRef<MedicalHistoryReporter.Command> medicalReporter
        ) {
            this.countCfg = countCfg;
            this.bpReporter = bpReporter;
            this.hrReporter = hrReporter;
            this.sleepReporter = sleepReporter;
            this.locReporter = locReporter;
            this.busyReporter = busyReporter;
            this.medicalReporter = medicalReporter;
        }
    }
}
