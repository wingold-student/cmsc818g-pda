package com.cmsc818g.StressDetectionEngine;

import java.util.ArrayList;
import java.util.HashMap;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressContextEngine.Reporters.Reporter;
import com.cmsc818g.StressContextEngine.Reporters.BloodPressureReporter.BloodPressure;
import com.cmsc818g.StressContextEngine.Reporters.BusynessReporter.BusynessReading;
import com.cmsc818g.StressContextEngine.Reporters.HeartRateReporter.HeartRate;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter.LocationReading;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter.UserLocation;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter.SleepHours;
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
import akka.actor.typed.javadsl.TimerScheduler;
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
        public String message; 

        public detectionEngineGreet(String message, ActorRef<StressManagementController.Command> ref) {
          this.message = message;
          this.replyTo = ref;
        }
    }//end of class detectionEngineGreet

    public static final class AdaptedAggreatedMetrics implements Command {
        public final AggregatedStressMetrics response;

        public AdaptedAggreatedMetrics(AggregatedStressMetrics response) {
            this.response = response;
        }
    }

    public static final class ReporterRefs implements Command {
      public final HashMap<String, ActorRef<Reporter.Command>> reporterRefs;

      public ReporterRefs(HashMap<String, ActorRef<Reporter.Command>> reporterRefs) {
        this.reporterRefs = reporterRefs;
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

    private final DetectionConfig cfg;
    private ActorRef<DetectionMetricsAggregator.Command> aggregator;
    private final ActorRef<DetectionMetricsAggregator.AggregatedStressMetrics> aggregatorAdapter;
    private ActorRef<StressManagementController.Command> controller;

    private HashMap<String, ActorRef<Reporter.Command>> reporterRefs; 
    private int previousStressLevel = 0, currentStressLevel = 0;

    public StressDetectionEngine(ActorContext<Command> context, String configFilename) throws StreamReadException, DatabindException, IOException {
        super(context);
        context.getLog().info("context engine actor created");

        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        InputStream cfgFilestream = getClass().getClassLoader().getResourceAsStream(configFilename);
        cfg = yamlReader.readValue(cfgFilestream, DetectionConfig.class);
        context.getLog().info("HR Count: {}", cfg.detectionMetricsCounts.hrCount);

        this.aggregatorAdapter = context.messageAdapter(DetectionMetricsAggregator.AggregatedStressMetrics.class, AdaptedAggreatedMetrics::new);
    }
  

    /************************************* 
     * MESSAGE HANDLING 
     *************************************/
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
      .onMessage(detectionEngineGreet.class, this::onEngineResponse)
      .onMessage(AdaptedAggreatedMetrics.class, this::StressMeasurementProcess)
      .onMessage(ReporterRefs.class, this::onReporterRefs)
      .onSignal(PostStop.class, signal -> onPostStop())
      .build();
    }
  
    private Behavior<Command> onEngineResponse(detectionEngineGreet response) { 
        //query reporters 
      if(response.message == "detect"){

        DetectionMetricsConfig metricsConfig = new DetectionMetricsConfig(
            cfg.detectionMetricsCounts,
            reporterRefs.get("BloodPressure"),
            reporterRefs.get("HeartRate"),
            reporterRefs.get("Sleep"),
            reporterRefs.get("Location"),
            reporterRefs.get("Busyness"),
            reporterRefs.get("Medical") 
        );

        this.aggregator = getContext().spawn(DetectionMetricsAggregator.create(this.aggregatorAdapter, metricsConfig), "DetectionAggregator");
        controller = response.replyTo ; 
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


        getContext().getLog().info(" ML measure started ");

        try{
          String path = "src/main/java/com/cmsc818g/LOGISTIC_REGRESSION.py";
          ProcessBuilder pb = new ProcessBuilder("python3", path, 
              "3", "2", "132", "80", "80"); // sleep-hour, busyness, bp-systolic, bp-diastolic, heart-rate
          Process process = pb.start();

          BufferedReader bfr = new BufferedReader(new InputStreamReader(process.getInputStream()));
          String line = "";
     
          process.waitFor(); 
          int len;
          if ((len = process.getErrorStream().available()) > 0) {
              byte[] buf = new byte[len];
              process.getErrorStream().read(buf);
              //System.err.println("Command error:\""+new String(buf)+"\"");
          }
          line = bfr.readLine();
          //System.out.println("Python output : " + line); // [2.]
          String python_output = line;

          stressLevel = Integer.parseInt(String.valueOf(python_output.charAt(1)));
          previousStressLevel = currentStressLevel;
          currentStressLevel = stressLevel;

        }catch(Exception e){
            System.out.println(e);
        }

        // TODO: Check if any of these are empty?
        DetectionData detectionData = new DetectionData(metrics.bpReading.get(),
                                                        metrics.hrReading.get(),
                                                        metrics.sleepReading.get(),
                                                        metrics.locReading.get(),
                                                        metrics.busyReading.get(),
                                                        previousStressLevel,
                                                        currentStressLevel);

        getContext().getLog().info("Detection engine's stress level: "+ stressLevel); 
        //controller.tell(new StressManagementController.DetectionEngineToController("healthInfo", detectionData));       
        return this;
    }

    private Behavior<Command> onReporterRefs(ReporterRefs msg) {
      this.reporterRefs = msg.reporterRefs;
      return this;
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
        public final ActorRef<Reporter.Command> bpReporter;
        public final ActorRef<Reporter.Command> hrReporter;
        public final ActorRef<Reporter.Command> sleepReporter;
        public final ActorRef<Reporter.Command> locReporter;
        public final ActorRef<Reporter.Command> busyReporter;
        public final ActorRef<Reporter.Command> medicalReporter;

        public DetectionMetricsConfig(
            DetectionMetricsCounts countCfg,
            ActorRef<Reporter.Command> bpReporter,
            ActorRef<Reporter.Command> hrReporter,
            ActorRef<Reporter.Command> sleepReporter,
            ActorRef<Reporter.Command> locReporter,
            ActorRef<Reporter.Command> busyReporter,
            ActorRef<Reporter.Command> medicalReporter
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

    public static class DetectionData {
      public final BloodPressure bp;
      public final HeartRate hr;
      public final SleepHours sleep;
      public final UserLocation loc;
      public final BusynessReading busy;
      public final int previousStressLevel;
      public final int currentStressLevel;

      public DetectionData(
              BloodPressure bp,
              HeartRate hr,
              SleepHours sleep,
              UserLocation loc,
              BusynessReading busy,
              int previousStressLevel,
              int currentStressLevel)
      {
        this.bp = bp;
        this.hr = hr;
        this.sleep = sleep;
        this.loc = loc;
        this.busy = busy;
        this.previousStressLevel = previousStressLevel;
        this.currentStressLevel = currentStressLevel;
      }
    }
}
