package com.cmsc818g;
import com.cmsc818g.StressContextEngine.StressContextEngine;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressManagementController extends AbstractBehavior<StressManagementController.Command> {
  

    public StressManagementController(ActorContext<Command> context) {
        super(context);
        System.out.println("controller actor created");
    }

    public static Behavior<StressManagementController.Command> create() {
        return Behaviors.setup(StressManagementController::new);
    }

    public interface Command {}
    public static final class controllerCommunication implements Command {
      public final ActorRef<controllerResponse> respondTo;
  
      public controllerCommunication(ActorRef<controllerResponse> respondTo) {
        this.respondTo = respondTo;
      }
    }//end of class controllerCommunication

    public static final class controllerResponse {
      public final String message;
  
      public controllerResponse(String message) {
        this.message = message;
      }
    }//end of class controllerResponse

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().onMessage(controllerCommunication.class, this::controllerCommunication).build();
    } 

    private Behavior<Command> controllerCommunication(controllerCommunication message) {
        message.respondTo.tell(new controllerResponse("I'm sorry. I'm afraid I can't do that."));
        System.out.println(message);
        return this;
    }
    public static void main(String[] args)
    {
        ActorRef<StressManagementController.Command> pda = ActorSystem.create(StressManagementController.create(), "pda-system-controller");
        System.out.println("pda-system started, controller working");
        ActorSystem.create(StressContextEngine.create(pda), "pda-system-controller");

        //ActorRef<StressManagementController.Command> pda = ActorSystem.create(StressManagementController.create(),"controller");
        
        //pda.tell(new StressManagementController.Command(" controller message whatever comes here"));

        
    }

}
