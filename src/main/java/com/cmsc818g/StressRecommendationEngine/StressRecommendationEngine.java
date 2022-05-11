package com.cmsc818g.StressRecommendationEngine;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import java.sql.Connection;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import com.cmsc818g.Utilities.SQLiteHandler;
import org.slf4j.Logger;
import akka.actor.ActorPath;

import com.cmsc818g.StressManagementController;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter;
import com.cmsc818g.StressContextEngine.Reporters.Reporter;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter;
import com.cmsc818g.StressContextEngine.Reporters.LocationReporter.UserLocation;
import com.cmsc818g.StressContextEngine.Reporters.SleepReporter.SleepHours;
import com.cmsc818g.StressRecommendationEngine.RecommendationMetricsAggregator.AggregatedRecommendationMetrics;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.typesafe.config.ConfigException.Null;

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
import ch.qos.logback.classic.db.names.TableName;

public class StressRecommendationEngine extends AbstractBehavior<StressRecommendationEngine.Command> {

    /************************************* 
     * MESSAGES IT RECEIVES 
     *************************************/

    int sleepReadingResults = 0;
    String locReadingResults = "";
    String sleepCondition;
    String locationCondition;
    int stressLevelReceived = 0;
    int oldStressLevelReceived = 0;
    ActorRef<StressManagementController.Command> replyToSMC;


    
    public interface Command {}

    public static class ScheduleReporterToRecommendation implements Command {
      public final String message; //get schedule from schedule reporter

      public ScheduleReporterToRecommendation(String message) {
        this.message = message;
      }
    }
    public static class recommendEngineGreet implements Command {
        public final String message ;
        public final ActorRef<StressManagementController.Command> replyTo;
        public final int pastStressLevel;
        public final int currentStressLevel;
        
        public recommendEngineGreet(String message,
                                   ActorRef<StressManagementController.Command> replyTo,
                                   int past,
                                   int curr)
        {
          this.message = message;
          this.replyTo = replyTo;
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
      return Behaviors.setup(context -> new StressRecommendationEngine(context, configFilename));
    }

    private ActorRef<RecommendationMetricsAggregator.Command> aggregator;
    private final ActorRef<RecommendationMetricsAggregator.AggregatedRecommendationMetrics> aggregatorAdapter;

    private final RecommendationConfig cfg;
    private HashMap<String, ActorRef<Reporter.Command>> reporterRefs;
    private Optional<SleepHours> sleepReading;
    private Optional<UserLocation> locReading;
    private boolean haveMetrics = false;

    public StressRecommendationEngine(ActorContext<Command> context, String configFilename) throws StreamReadException, DatabindException, IOException {
        super(context);
        getContext().getLog().info("Recommendation Engine actor created"); 

        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        InputStream cfgFilestream = getClass().getClassLoader().getResourceAsStream(configFilename);
        cfg = yamlReader.readValue(cfgFilestream, RecommendationConfig.class);

        this.aggregatorAdapter = context.messageAdapter(RecommendationMetricsAggregator.AggregatedRecommendationMetrics.class, AdaptedAggreatedMetrics::new);
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
      .onMessage(ReporterRefs.class, this::onReporterRefs)
      .onSignal(PostStop.class, signal -> onPostStop())
      .build();
    }
  
    private Behavior<Command> onEngineResponse(recommendEngineGreet response) { //when receive message
      if(response.message == "recommend"){
          //recommend treatment
          oldStressLevelReceived = stressLevelReceived;     
          stressLevelReceived = response.currentStressLevel;
          replyToSMC = response.replyTo;

          RecommendationMetricsConfig config = new RecommendationMetricsConfig(
            cfg.recommendationMetricsCounts,
            reporterRefs.get("Sleep"),
            reporterRefs.get("Location") 
          );
          
          if(stressLevelReceived > oldStressLevelReceived){
          // TODO: Note this starts it immediately
            getContext().spawnAnonymous(RecommendationMetricsAggregator.create(config, this.aggregatorAdapter));
          } else {
            RecommendationData noRecommendation = new RecommendationData("", locReadingResults, sleepCondition, "");
            replyToSMC.tell(new StressManagementController.RecommendEngineToController("recommendation", noRecommendation)); 
          }

          // TODO: Would want to move this to when results are actually ready
          //response.replyTo.tell(new StressManagementController.RecommendEngineToController("recommendation")); 
          //this.tell(new SleepReporter.AskSleepHours(getContext().getSelf()))
      }
      return this;
    }

    private Behavior<Command> onAggregatedMetrics(AdaptedAggreatedMetrics wrapped) throws ClassNotFoundException, SQLException {
      AggregatedRecommendationMetrics metrics = wrapped.response;
      
      sleepReading = metrics.sleepReading;
      locReading = metrics.locReading;
      haveMetrics = true;
      
      locReadingResults = metrics.locReading.get().location;
      sleepReadingResults = metrics.sleepReading.get().sleep;


      // TODO: Somewhat temporary. Could instead now call the actual recommendation algorithm
      switch((0 <= sleepReadingResults && sleepReadingResults <= 5 ) ? 0 : 1){
        case 0:
          sleepCondition = "bad";
          break;
        case 1:
          sleepCondition = "good";
          break;
      }

      switch(locReadingResults){
        case "classroom":
        case "restaurant":
        case "gym":
        case "conferenceroom":
          locationCondition = "public";
          break;
        case "office":
        case "home":
          locationCondition = "personal";
          break;
      }
      
       try{
        //String sql = String.format("SELECT treatment FROM %s WHERE `stress-level` = %d AND `sleep-condition` = %s AND `location-condition` = %s", cfg.table, stressLevelReceived, sleepCondition, locationCondition);
        String sql2 = "SELECT treatment FROM treatment WHERE `stress-level` = ? AND `sleep-condition` = ? AND `location-condition` = ?";

        ActorPath myPath = getContext().getSelf().path();
        
        // Forms a connection to the database
        Connection conn = SQLiteHandler.connectToDB(cfg.databaseURI, null, myPath);

        PreparedStatement statement;
        statement = conn.prepareStatement(sql2);
        statement.setInt(1, stressLevelReceived);
        statement.setString(2, sleepCondition);
        statement.setString(3, locationCondition);

        ResultSet results = SQLiteHandler.queryDB(cfg.databaseURI, statement, null, myPath);

        if (results.next()) {
          String treatmentToSend = results.getString("treatment");
          results.close();

          // TODO: Ask scheduler current event occurring
          // this.reporterRefs["Scheduler"]
          RecommendationData recommendationData = new RecommendationData("", locReadingResults, sleepCondition, treatmentToSend);
          // Tell the Context Engine we've successfully read
          //msg.replyTo.tell(new Reporter.StatusOfRead(true, "Succesfully read row " + msg.rowNumber, myPath));
          replyToSMC.tell(new StressManagementController.RecommendEngineToController("recommendation", recommendationData)); 

          } else {

          RecommendationData noRecommendation = new RecommendationData("", locReadingResults, sleepCondition, "");
          replyToSMC.tell(new StressManagementController.RecommendEngineToController("recommendation", noRecommendation)); 
          // Tell the Context Engine we had a problem
        }
      
        statement.close();
        conn.close();

        //response.replyTo.tell(new StressManagementController.RecommendEngineToController("recommendation")); 
        //put response here?
      }
      catch (ClassNotFoundException e) {
        String errorStr = "Failed find the SQLite drivers";
        getContext().getLog().error(errorStr, e);
        //who to tell when failed?
        throw e;
    } catch(SQLException e) {
        String errorStr = String.format("Failed to execute SQL query ");
        getContext().getLog().error(errorStr, e);
        throw e;
    }

      
      return this;
    }

    private Behavior<Command> onReporterRefs(ReporterRefs msg) {
      this.reporterRefs = msg.reporterRefs;
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
      public RecommendationMetricsCounts recommendationMetricsCounts;
      public String databaseURI;
      public String table;
    }

    public static class RecommendationMetricsConfig {
        public final ActorRef<Reporter.Command> sleepReporter;
        public final ActorRef<Reporter.Command> locReporter;

        public final RecommendationMetricsCounts countCfg;

        public RecommendationMetricsConfig(
            RecommendationMetricsCounts countCfg,
            ActorRef<Reporter.Command> sleepReporter,
            ActorRef<Reporter.Command> locReporter
        ) {
            this.countCfg = countCfg;
            this.sleepReporter = sleepReporter;
            this.locReporter = locReporter;
        }
    }
    

    public final static class RecommendationData {
      public final String event;
      public final String location;
      public final String sleepQuality;
      public final String treatmentDescription;

      public RecommendationData(String event,
                                String location,
                                String sleepQuality,
                                String treatmentDescription)
      {
        this.event = event;
        this.location = location;
        this.sleepQuality = sleepQuality;
        this.treatmentDescription = treatmentDescription;
      }
    }
}
