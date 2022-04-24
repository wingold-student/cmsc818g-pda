package com.cmsc818g;

import java.time.Duration;
import java.util.ArrayList;
import com.cmsc818g.StressContextEngine.StressContextEngine;
import com.cmsc818g.StressDetectionEngine.StressDetectionEngine;
import com.cmsc818g.StressEntityManager.StressEntityManager;
import com.cmsc818g.StressRecommendationEngine.StressRecommendationEngine;
import com.cmsc818g.StressUIManager.StressUIManager;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.Terminated;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressManagementController extends AbstractBehavior<StressManagementController.Command>
{
  public interface Command { }
    public static ArrayList<String> entityList = new ArrayList<String>();
    //public final ActorRef<StressEntityManager.Command> child_EntityManager;
    public final ActorRef<StressContextEngine.Command> child_ContextEngine;
    public final ActorRef<StressDetectionEngine.Command> child_DetectionEngine;
    public final ActorRef<StressRecommendationEngine.Command> child_RecommendEngine;
    public final ActorRef<StressUIManager.Command> child_UIManager;
    public static class HealthInformation {
      public int bloodPressure = 98;
      public int heartRate = 65;
      public int sleepHour = 0;
      public String location = null;
      public int busyness = 0;
      public int stressLevel = 0;
      public String event = null;
      public String schedule = null;
    }
    public HealthInformation PersonalHealthInfo = new HealthInformation();
    public int pastStressLevel;
    public StressManagementController(ActorContext<Command> context) {
        super(context); 
        getContext().getLog().info("Controller Actor created");

        // TODO: THESE ARE TEMPORARY
        String databaseURI = "jdbc:sqlite:src/main/resources/DemoScenario.db";
        String tableName = "ScenarioForDemo";

        /* Entity Manager gets/spawns Entities 
          child_EntityManager = context.spawn(StressEntityManager.create(), "StressEntityManager");
          context.watch(child_EntityManager);
          child_EntityManager.tell(new StressEntityManager.entityManagerGreet(getContext().getSelf()));
        */
        child_ContextEngine = context.spawn(StressContextEngine.create(databaseURI, tableName), "StressContextEngine");
        context.watch(child_ContextEngine);
        child_ContextEngine.tell(new StressContextEngine.contextEngineGreet(getContext().getSelf(), PersonalHealthInfo, entityList));

        child_DetectionEngine = context.spawn(StressDetectionEngine.create(), "StressDetectionEngine");
        context.watch(child_DetectionEngine);
        child_ContextEngine.tell(new StressContextEngine.StartPeriodicDatabaseReading(Duration.ofSeconds(1L)));

        child_RecommendEngine = context.spawn(StressRecommendationEngine.create(), "StressRecommendEngine");
        context.watch(child_RecommendEngine);

        child_UIManager = context.spawn(StressUIManager.create(), "StressUIManager");
        context.watch(child_UIManager);
    }

    public static void controllerProcess() {
      return;
    }//end of controllerProcess

    public static Behavior<StressManagementController.Command> create() {
      return Behaviors.setup(StressManagementController::new);
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
      public ContextEngineToController(String message) {
        this.message = message;
      }
    }//end of ControllerToContextEngine
    public static class DetectionEngineToController implements Command {
      public final String message;
      public final HealthInformation info; //get stress level from detection engine

      public DetectionEngineToController(String message, HealthInformation info) {
        this.message = message;
        this.info = info;
      }
    }//end of DetectionEngineToController
    public static class RecommendEngineToController implements Command {
      // recommendation engine tells controller the treatment method
      // or it directly talks to the UI Manager
      public final String message;
      public RecommendEngineToController(String message) {
        this.message = message;
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
        if(response.message != "contextEngine") return null;
        else
          child_DetectionEngine.tell(new StressDetectionEngine.detectionEngineGreet(getContext().getSelf(), entityList));
        return this;
    }

    private Behavior<Command> onDetectionEngineResponse(DetectionEngineToController response) {
        getContext().getLog().info("Got response from Detection Engine: {}", response.message);
       if(response.message != "healthInfo") return null;

       //save past stress level and update with the new one
       pastStressLevel = PersonalHealthInfo.stressLevel; 
       PersonalHealthInfo = response.info;
       getContext().getLog().info("Estimated Stress Level: ", PersonalHealthInfo.stressLevel);

       //Recommendation process start
       if(PersonalHealthInfo.stressLevel != 100)
          child_RecommendEngine.tell(new StressRecommendationEngine.recommendEngineGreet(getContext().getSelf(), PersonalHealthInfo, entityList, pastStressLevel));
        return this;
    }

    private Behavior<Command> onRecommendEnginedResponse(RecommendEngineToController response) {
      getContext().getLog().info("Got response from Recommendation Engine: {}", response.message);
      if(response.message != "recommendation") return null;
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

}

