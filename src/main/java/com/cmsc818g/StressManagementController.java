package com.cmsc818g;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;

import com.cmsc818g.StressContextEngine.StressContextEngine;
import com.cmsc818g.StressContextEngine.Reporters.Reporter;
import com.cmsc818g.StressDetectionEngine.StressDetectionEngine;
import com.cmsc818g.StressDetectionEngine.StressDetectionEngine.DetectionData;
import com.cmsc818g.StressRecommendationEngine.StressRecommendationEngine;
import com.cmsc818g.StressRecommendationEngine.StressRecommendationEngine.RecommendationData;
import com.cmsc818g.StressUIManager.StressUIManager;
import com.cmsc818g.StressUIManager.StressWebHandler;
import com.cmsc818g.StressUIManager.StressWebHandler.FrontEndData;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.javadsl.TimerScheduler;

public class StressManagementController extends AbstractBehavior<StressManagementController.Command>
{
  public interface Command { }
    public static ArrayList<String> entityList = new ArrayList<String>();
    public int currentStressLevel = 0;
    
    private final ControllerConfig cfg;
    public  ActorRef<StressContextEngine.Command> child_ContextEngine;
    public  ActorRef<StressDetectionEngine.Command> child_DetectionEngine;
    public  ActorRef<StressRecommendationEngine.Command> child_RecommendEngine;
    public  ActorRef<StressUIManager.Command> child_UIManager;
    private TimerScheduler<Command> detect_timer;
    private final int readRate = 5;
    private DetectionData latestDetectionData = null;

    protected static enum TellToDetect implements Command {
      INSTANCE
    };

    public StressManagementController(ActorContext<Command> context, String configFilename, 
                                      TimerScheduler<Command> timer) throws StreamReadException, DatabindException, IOException {
        super(context); 
        getContext().getLog().info("Controller Actor created");

        String databaseURI = "jdbc:sqlite:src/main/resources/DemoScenario.db";
        String tableName = "ScenarioForDemo";

        InputStream is = getClass().getClassLoader().getResourceAsStream(configFilename);
        ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
        this.cfg = yamlReader.readValue(is, ControllerConfig.class);
        this.detect_timer = timer;
        /* Entity Manager gets/spawns Entities 
          child_EntityManager = context.spawn(StressEntityManager.create(), "StressEntityManager");
          context.watch(child_EntityManager);
          child_EntityManager.tell(new StressEntityManager.entityManagerGreet(getContext().getSelf()));
        */
        child_ContextEngine = context.spawn(StressContextEngine.create(databaseURI, tableName, cfg.contextEngineCfgName), "StressContextEngine");
        context.watch(child_ContextEngine);
        child_ContextEngine.tell(new StressContextEngine.contextEngineGreet(getContext().getSelf(), entityList));

        // Tell Reporters to start reading
        child_ContextEngine.tell(StressContextEngine.TellAllReportersToPeriodicallyRead.INSTANCE);

        child_DetectionEngine = context.spawn(StressDetectionEngine.create(cfg.detectionEngineCfgName), "spawn");
        context.watch(child_DetectionEngine);

        child_RecommendEngine = context.spawn(StressRecommendationEngine.create(cfg.recommendationEngineCfgName), "StressRecommendEngine");
        context.watch(child_RecommendEngine);

        child_UIManager = context.spawn(StressUIManager.create(), "StressUIManager");
        context.watch(child_UIManager);

    }

    public static Behavior<StressManagementController.Command> create(String configFilename) {
      return Behaviors.setup(context -> Behaviors.withTimers(
               detect_timer -> new StressManagementController(context, configFilename, detect_timer)));
    }

  /* 
  ------------------------------------------------------------------------
      Response/Reply Message between controller and Entity Manager
  ------------------------------------------------------------------------
  */

    public static final class controllerProcess implements Command{
      public final String message;
    
      public controllerProcess(String message) {
        this.message = message;
      }
    }
    public static class EntityManagerToController implements Command {
    public final String message;
    public final ArrayList<String> list;

    public EntityManagerToController(String message, ArrayList<String> list) {
      this.message = message;
      this.list = list;
    }
  }//end of EntityManagerToController
    public static class ContextEngineToController implements Command {
      public final String message;
      public final HashMap<String, ActorRef<Reporter.Command>> reporterRefs;

      public ContextEngineToController(String message, HashMap<String, ActorRef<Reporter.Command>> reporterRefs) {
        this.message = message;
        this.reporterRefs = reporterRefs;
      }
    }//end of ControllerToContextEngine

    public static class DetectionEngineToController implements Command {
      public final String message;
      public final DetectionData detectionData;

      public DetectionEngineToController(String message, DetectionData detectionData) {
        this.message = message;
        this.detectionData = detectionData;
      }
    }//end of DetectionEngineToController

    public static class RecommendEngineToController implements Command {
      // recommendation engine tells controller the treatment method
      // or it directly talks to the UI Manager
      public final String message;
      public final RecommendationData recommendationData;

      public RecommendEngineToController(String message, RecommendationData recommendationData) {
        this.message = message;
        this.recommendationData = recommendationData;
      }
    }//end of ControllerToRecommendEngine

    public static class UIManagerToController implements Command {
      public final String message;
      public UIManagerToController(String message) {
        this.message = message;
      }
    }//end of UIManagerToController

 /* 
  ------------------------------------------------------------------------
       Actions when Controller Received Message 
  ------------------------------------------------------------------------
*/     
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
        .onMessage(controllerProcess.class, this::onControllerProcess)
        .onMessage(EntityManagerToController.class, this::onEntityManagerResponse)
        .onMessage(ContextEngineToController.class, this::onContextEnginedResponse)
        .onMessage(DetectionEngineToController.class, this::onDetectionEngineResponse)
        .onMessage(RecommendEngineToController.class, this::onRecommendEnginedResponse)
        .onMessage(UIManagerToController.class, this::onUIManagerResponse)
        .onMessage(TellToDetect.class, this::onTellSelfToDetect)
        .onSignal(Terminated.class , sig -> Behaviors.stopped())
        .onSignal(PostStop.class, signal -> onPostStop())
        .build();
        
    } //end of createReceive

    private Behavior<Command> onControllerProcess(controllerProcess response) {
      getContext().getLog().info("Got response from Main: {}", response.message);
      // if there is additional controller process to be implemented
      return this;
  }

    private Behavior<Command> onEntityManagerResponse(EntityManagerToController response) {
      getContext().getLog().info("Got response from Entity Manager: {}", response.message);
      //get entity list and save data in controller
      if(response.message != "entityList") return null;
      if(entityList != null){
        getContext().getLog().info("entity list setting");
        entityList = response.list;
      }
      return this;
    }//end of onEntityManagerResponse
 
    private Behavior<Command> onContextEnginedResponse(ContextEngineToController response) {
        getContext().getLog().info("Got response from Context Engine: {}", response.message);
        if(response.message != "contextEngine")
          return null;
        else {
          child_UIManager.tell(new StressUIManager.ReceiveSchedulerRef(response.reporterRefs.get("Scheduler")));

          // Tell both engines about the reporters
          child_DetectionEngine.tell(new StressDetectionEngine.ReporterRefs(response.reporterRefs));
          child_RecommendEngine.tell(new StressRecommendationEngine.ReporterRefs(response.reporterRefs));

         // Start periodic detection
          getContext().getLog().info("Start periodic detection");

          // Initially tell self in 1 second, then start 2 second intervals
          // detect_timer.startTimerWithFixedDelay("detection-timer", TellToDetect.INSTANCE, Duration.ofSeconds(1), Duration.ofSeconds(readRate));
          detect_timer.startTimerAtFixedRate("detection-timer",
                                              TellToDetect.INSTANCE,
                                      Duration.ofSeconds(readRate));
          
        }//end of else
        return this;
    }

      protected Behavior<Command> onTellSelfToDetect(TellToDetect msg) {
        getContext().getLog().info("tell detection engine to read periodically");
        child_DetectionEngine.tell(new StressDetectionEngine.detectionEngineGreet("detect",getContext().getSelf()));
        return this;
      }

    private Behavior<Command> onDetectionEngineResponse(DetectionEngineToController response) {
      getContext().getLog().info("Got response from Detection Engine: {}", response.message);
      this.latestDetectionData = response.detectionData;

      if(response.message != "healthInfo") return null;

      getContext().getLog().info("Estimated Stress Level: "+ response.detectionData.currentStressLevel);

      child_RecommendEngine.tell(
        new StressRecommendationEngine.recommendEngineGreet("recommend", 
                                                            getContext().getSelf(),
                                                            response.detectionData.previousStressLevel,
                                                            response.detectionData.currentStressLevel));
        return this;
    }

    private Behavior<Command> onRecommendEnginedResponse(RecommendEngineToController response) {
      getContext().getLog().info("Got response from Recommendation Engine: {}", response.message);
      if(response.message != "recommendation") return null;

      // TODO: Send message to UI Manager
      child_UIManager.tell(new StressUIManager.ReceiveCombinedData(
        new StressWebHandler.CombinedEngineData(response.recommendationData, this.latestDetectionData)
      ));
      return this;
  }
  
    private Behavior<Command> onUIManagerResponse(UIManagerToController response) {
      getContext().getLog().info("Got response from UI Manager: {}", response.message);
      return this;
  }

    private StressManagementController onPostStop() {
      getContext().getLog().info("Controller shutting down");
      return this;
  }

  public static class ControllerConfig {
    public String contextEngineCfgName;
    public String detectionEngineCfgName;
    public String recommendationEngineCfgName;
  }

}

