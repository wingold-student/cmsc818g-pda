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
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressManagementController extends AbstractBehavior<StressManagementController.Command>
{
    public static ArrayList<String> entityList = new ArrayList<String>();
    public final ActorRef<StressEntityManager.Command> child_EntityManager;
    public final ActorRef<StressContextEngine.Command> child_ContextEngine;
    public final ActorRef<StressDetectionEngine.Command> child_DetectionEngine;
    public final ActorRef<StressRecommendationEngine.Command> child_RecommendEngine;
    public final ActorRef<StressUIManager.Command> child_UIManager;
    public static class HealthInformation {
      public int bloodPressure = 98;
      public int heartRate = 65;
      public int sleepLevel = 0;
      public String location = "home";
      public int BusynessLevel = 0;
      public int stressLevel = 0;
      //scheduler
      //medical history
    }
    public HealthInformation PersonalHealthInfo = new HealthInformation();

    public StressManagementController(ActorContext<Command> context) {
        super(context); 
        getContext().getLog().info("Controller Actor created");

        // TODO: THESE ARE TEMPORARY
        String databaseURI = "jdbc:sqlite:src/main/resources/DemoScenario.db";
        String tableName = "ScenarioForDemo";

        child_EntityManager = context.spawn(StressEntityManager.create(databaseURI, tableName), "StressEntityManager");
        child_EntityManager.tell(new StressEntityManager.entityManagerGreet(getContext().getSelf()));

        child_ContextEngine = context.spawn(StressContextEngine.create(), "StressContextEngine");
        child_ContextEngine.tell(new StressContextEngine.contextEngineGreet(getContext().getSelf(), PersonalHealthInfo, entityList));

        child_DetectionEngine = context.spawn(StressDetectionEngine.create(), "StressDetectionEngine");
        child_DetectionEngine.tell(new StressDetectionEngine.detectionEngineGreet(getContext().getSelf(), PersonalHealthInfo, entityList));

        child_RecommendEngine = context.spawn(StressRecommendationEngine.create(), "StressRecommendEngine");
        child_RecommendEngine.tell(new StressRecommendationEngine.recommendEngineGreet(getContext().getSelf(), PersonalHealthInfo, entityList));

        child_UIManager = context.spawn(StressUIManager.create(), "StressUIManager");
        // Should I add UI Manager tell?
  
        child_EntityManager.tell(new StressEntityManager.StartPeriodicDatabaseReading(Duration.ofSeconds(1L)));
    }

    public static void controllerProcess() {
  
      return;
    }//end of controllerProcess
    public interface Command {
      //void execute();
    }

    public static Behavior<StressManagementController.Command> create() {
      return Behaviors.setup(StressManagementController::new);
    }

  /* 
  ------------------------------------------------------------------------------------------------------
      Response/Reply Message between controller and Entity Manager
  ------------------------------------------------------------------------------------------------------
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
      public final HealthInformation healthInfo;

      public ContextEngineToController(String message, HealthInformation healthInfo) {
        this.message = message;
        this.healthInfo = healthInfo;
      }
    }//end of ControllerToContextEngine
    public static class DetectionEngineToController implements Command {
      public final String message;
      public final int level; //get stress level from detection engine

      public DetectionEngineToController(String message, int level) {
        this.message = message;
        this.level = level;
      }
    }//end of DetectionEngineToController
    public static class RecommendEngineToController implements Command {
      //recommendation engines tells controller the treatment method
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
  ------------------------------------------------------------------------------------------------------
      ask between controller and Entity Manager example
  ------------------------------------------------------------------------------------------------------

    private StressManagementController(ActorContext<Command> context, ActorRef<StressEntityManager.Command> enManager) {
        super(context);
        getContext().getLog().info("StressManagementController ask StressEntityManager");
    
        // asking someone requires a timeout, if the timeout hits without response
        // the ask is failed with a TimeoutException
        final Duration timeout = Duration.ofSeconds(10);

        context.ask(
          StressEntityManager.entityManagerResponse_controller.class,
          enManager,
            timeout,
            // construct the outgoing message
            (ActorRef<StressEntityManager.entityManagerResponse_controller> ref) -> new entityManagerGreet(ref),
            // adapt the response (or failure to respond)
            (response, throwable) -> {
              if (response != null) {
                return new ControllerToEntityManager(response.message);
              } else {
                return new ControllerToEntityManager("Request failed");
              }
            });
          
        final int requestId = 1;
        context.ask(
          StressEntityManager.entityManagerResponse_controller.class,
          enManager,
            timeout,
            // construct the outgoing message
            (ActorRef<StressEntityManager.entityManagerResponse_controller> ref) -> new entityManagerGreet(ref),
            // adapt the response (or failure to respond)
            (response, throwable) -> {
              if (response != null) {
                return new ControllerToEntityManager(requestId + ": " + response.message);
              } else {
                return new ControllerToEntityManager(requestId + ": Request failed");
              }
            });
 
      }
    */

 /* 
  ------------------------------------------------------------------------------------------------------
    Actions when Controller Received Message 
  ------------------------------------------------------------------------------------------------------
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
        .build();
        
    } //end of createReceive

    //controller
    //start, stop
    //give device list to the reporters


    private Behavior<Command> onControllerProcess(controllerProcess response) {
      getContext().getLog().info("Got response from Main: {}", response.message);
      // if there is additional controller process to be implemented

      return this;
  }

    private Behavior<Command> onEntityManagerResponse(EntityManagerToController response) {
      getContext().getLog().info("Got response from Entity Manager: {}", response.message);
      //get entity list and save data in controller
      if(response.message != "entityList") return this;
      if(entityList != null){
        getContext().getLog().info("entity list setting");
        entityList = response.list;

        for(int i=0; i<entityList.size();i++)
          System.out.println("entityList["+i+"] = "+ entityList.get(i));
      }
 
      return this;
    }//end of onEntityManagerResponse
 
    private Behavior<Command> onContextEnginedResponse(ContextEngineToController response) {
        getContext().getLog().info("Got response from Context Engine: {}", response.message);
        if(response.message != "healthData") 
          return this;
   
          PersonalHealthInfo = response.healthInfo;
          System.out.println("PersonalHealthInfo: " + PersonalHealthInfo);
        return this;
    }

    private Behavior<Command> onDetectionEngineResponse(DetectionEngineToController response) {
        getContext().getLog().info("Got response from Detection Engine: {}", response.message);
       //detection engine send entity list
       if(response.message != "stressLevel") return this;
       PersonalHealthInfo.stressLevel = response.level;
       System.out.println("stress level: " + PersonalHealthInfo.stressLevel);

        return this;
    }

    private Behavior<Command> onRecommendEnginedResponse(RecommendEngineToController response) {
      getContext().getLog().info("Got response from Recommendation Engine: {}", response.message);
      if(response.message != "recommendation") return this;

      return this;
  }
  
    private Behavior<Command> onUIManagerResponse(UIManagerToController response) {
      getContext().getLog().info("Got response from UI Manager: {}", response.message);
     //send data for UI
      return this;
  }


}
