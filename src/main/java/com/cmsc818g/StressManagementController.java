package com.cmsc818g;

import java.time.Duration;
import java.util.ArrayList;

import com.cmsc818g.StressContextEngine.StressContextEngine;
import com.cmsc818g.StressDetectionEngine.StressDetectionEngine;
import com.cmsc818g.StressEntityManager.StressEntityManager;
import com.cmsc818g.StressEntityManager.StressEntityManager.entityManagerGreet;
import com.cmsc818g.StressEntityManager.StressEntityManager.entityManagerResponse_controller;
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
    private final ActorRef<StressEntityManager.Command> childEntityManager;
    private final ActorRef<StressContextEngine.Command> childContextEngine;
    public static class HealthInformation {
      public final int bloodPressure = 98;
      public final int heartRate = 65;
      public final int sleepLevel = 0;
      public final String location = "home";
      public final int BusynessLevel = 0;
      public final int stressLevel = 0;
      //scheduler
      //medical history
    }

    public StressManagementController(ActorContext<Command> context) {
        super(context); 
        getContext().getLog().info("Controller Actor created");

        childEntityManager = context.spawn(StressEntityManager.create(), "StressEntityManager");
        getContext().getLog().info("Entity Manager Actor spawn");

        childContextEngine = context.spawn(StressContextEngine.create(), "StressContextEngine");
        getContext().getLog().info("Context Engine Actor spawn");
        //context.spawn(StressDetectionEngine.create(), "StressDetectionEngine");
        //context.spawn(StressRecommendEngine.create(), "StressRecommendEngine");
        //context.spawn(StressUIManager.create(), "StressUIManager");
    }
  
    /*public static Behavior<Command> create(ActorRef<StressEntityManager.Command> engine) {
      System.out.println("works");;
      return Behaviors.setup(context -> new StressManagementController(context, engine));
    }*/
  
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
    public static class EntityManagerToController implements Command {
    public final String message;
    public final ArrayList<String> list;

    public EntityManagerToController(String message, ArrayList<String> list) {
      this.message = message;
      this.list = list;
    }
  }//end of EntityManagerToController
  public static final class ControllerToEntityManager implements Command{
    public final String whom;

    public ControllerToEntityManager(String whom) {
      this.whom = whom;
    }
  }//end of ControllerToEntityManager
 
  /* 
  ------------------------------------------------------------------------------------------------------
      Response/Reply Message between controller and Context Engine
  ------------------------------------------------------------------------------------------------------
  */
      public static class ContextEngineToController implements Command {
        public final String message;
        public final HealthInformation healthInfo;

        public ContextEngineToController(String message, HealthInformation healthInfo) {
          this.message = message;
          this.healthInfo = healthInfo;
        }
      }//end of ControllerToContextEngine
      public static final class ControllerToContextEngine implements Command{
        public final String message;
        public final ArrayList<String> list;
    
        public ControllerToContextEngine(String message, ArrayList<String> list ) {
          this.message = message;
          this.list = list;
        
        }
      }//end of ControllerToContextEngine

    /* 
    ------------------------------------------------------------------------------------------------------
        Response/Reply Message between controller and Detection Engine
    ------------------------------------------------------------------------------------------------------
    */
    public static class DetectionEngineToController implements Command {
      public final String message;
      public final int stressLevel; //get stress level from detection engine

      public DetectionEngineToController(String message, int stressLevel) {
        this.message = message;
        this.stressLevel = stressLevel;
      }
    }//end of DetectionEngineToController
    public static final class ControllerToDetectionEngine implements Command{ 
      public final String message;
      public final HealthInformation healthInfo; //send health info to detection engine
    
      public ControllerToDetectionEngine(String message, HealthInformation healthInfo) {
        this.message = message;
        this.healthInfo = healthInfo;
      }
    }//end of ControllerToDetectionEngine
        
    /* 
    ------------------------------------------------------------------------------------------------------
        Response/Reply Message between controller and Recommendation Engine
    ------------------------------------------------------------------------------------------------------
    */
    public static class RecommendEngineToController implements Command {
      //recommendation engines tells controller the treatment method
      // or it directly talks to the UI Manager
      public final String message;
      public RecommendEngineToController(String message) {
        this.message = message;
      }
    }//end of ControllerToRecommendEngine
    public static final class ControllerToRecommendEngine implements Command{
      //controller sends current stress level, previous stress level
      public final String whom;
      public final int currentStressLevel;
      public final int pastStressLevel;

      public ControllerToRecommendEngine(String whom, int currentStressLevel, int pastStressLevel) {
        this.whom = whom;
        this.currentStressLevel = currentStressLevel;
        this.pastStressLevel = pastStressLevel;
      }
    }//end of RecommendEngineToController
    /* 
    ------------------------------------------------------------------------------------------------------
        Response/Reply Message between controller and UI Manager
    ------------------------------------------------------------------------------------------------------
    */
    public static class UIManagerToController implements Command {
      public final String message;
      public UIManagerToController(String message) {
        this.message = message;
      }
    }//end of UIManagerToController
    public static final class ControllerToUIManager implements Command{
      public final String whom;
      public ControllerToUIManager(String whom, ActorRef<UIManagerToController> replyTo) {
        this.whom = whom;
      }
    }//end of ControllerToUIManager


 
/*
  ------------------------------------------------------------------------------------------------------
      ask between controller and Entity Manager ->  will change to tell function
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
    When Controller Received Message it does below actions
  ------------------------------------------------------------------------------------------------------
*/     
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
        .onMessage(EntityManagerToController.class, this::onEntityManagerResponse)
        .onMessage(ContextEngineToController.class, this::onContextEnginedResponse)
        .onMessage(DetectionEngineToController.class, this::onDetectionEngineResponse)
        .onMessage(RecommendEngineToController.class, this::onRecommendEnginedResponse)
        .onMessage(UIManagerToController.class, this::onUIManagerResponse)
        .build();
        
    } //end of createReceive

    private Behavior<Command> onEntityManagerResponse(EntityManagerToController response) {
      getContext().getLog().info("Got response from Entity Manager: {}", response.message);
      //get entity list and save data in controller
      if(entityList != null){
        getContext().getLog().info("entity list setting");
        entityList = null;
        entityList.addAll(response.list);

        for(int i=0; i<entityList.size();i++)
          System.out.println("entityList["+i+"] = "+ entityList.get(i));
        
        sendMsgToContextEngine(response);
      }
      getContext().getLog().info("entity list null");
      return this;
    }//end of onEntityManagerResponse

    private Behavior<Command> sendMsgToContextEngine(EntityManagerToController response){
      ActorRef<Command> controller = getContext().getSelf();

      //connect with contextEngine
      controller.tell(new ControllerToContextEngine("controller", entityList));      
      return this;
    }

    private Behavior<Command> onContextEnginedResponse(ContextEngineToController response) {
        getContext().getLog().info("Got response from Context Engine: {}", response.message);


        
        return this;
    }

    private Behavior<Command> onDetectionEngineResponse(DetectionEngineToController response) {
        getContext().getLog().info("Got response from Detection Engine: {}", response.message);
      
        return this;
    }

    private Behavior<Command> onRecommendEnginedResponse(RecommendEngineToController response) {
      getContext().getLog().info("Got response from Recommendation Engine: {}", response.message);

      //recommendation
      return this;
  }

  
    private Behavior<Command> onUIManagerResponse(UIManagerToController response) {
      getContext().getLog().info("Got response from UI Manager: {}", response.message);
     //send data for UI
      return this;
  }


}
