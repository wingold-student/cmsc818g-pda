package com.cmsc818g;
import java.time.Duration;

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
        getContext().getLog().info("controller actor created");
    }
    public static class controllerResponse implements Command {
        public final String message;
    
        public controllerResponse(String message) {
          this.message = message;
        }
      }//end of controllerResponse

    public interface Command {
      //void execute();
    }

    public static final class controllerGreet implements Command{
      public final String whom;
      public final ActorRef<controllerResponse> replyTo;
  
      public controllerGreet(String whom, ActorRef<controllerResponse> replyTo) {
        this.whom = whom;
        this.replyTo = replyTo;
      }
    }//end of controllerGreet

    
    public static Behavior<StressManagementController.Command> create() {
        return Behaviors.setup(StressManagementController::new);
    }

    public static Behavior<Command> create(ActorRef<StressContextEngine.Command> engine) {
        return Behaviors.setup(context -> new StressManagementController(context, engine));
    }
    
    private StressManagementController(ActorContext<Command> context, ActorRef<StressContextEngine.Command> contextEngine) {
        super(context);
        getContext().getLog().info("StressManagementController ask ContextEngine");
    
        // asking someone requires a timeout, if the timeout hits without response
        // the ask is failed with a TimeoutException
        final Duration timeout = Duration.ofSeconds(10);
 
        context.ask(
            StressContextEngine.engineResponse.class,
            contextEngine,
            timeout,
            // construct the outgoing message
            (ActorRef<StressContextEngine.engineResponse> ref) -> new StressContextEngine.engineGreet(ref),
            // adapt the response (or failure to respond)
            (response, throwable) -> {
              if (response != null) {
                return new controllerResponse(response.message);
              } else {
                return new controllerResponse("Request failed");
              }
            });
          
        final int requestId = 1;
        context.ask(
            StressContextEngine.engineResponse.class,
            contextEngine,
            timeout,
            // construct the outgoing message
            (ActorRef<StressContextEngine.engineResponse> ref) -> new StressContextEngine.engineGreet(ref),
            // adapt the response (or failure to respond)
            (response, throwable) -> {
              if (response != null) {
                return new controllerResponse(requestId + ": " + response.message);
              } else {
                return new controllerResponse(requestId + ": Request failed");
              }
            });
 
      }
    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder().onMessage(controllerResponse.class, this::onAdaptedResponse).build();
    } 

    private Behavior<Command> onAdaptedResponse(controllerResponse response) {
        getContext().getLog().info("Got response from StressContextEngine: {}", response.message);
        return this;
    }


    public static void main(String[] args)
    {   
      //Actor system
      ActorRef<com.cmsc818g.StressContextEngine.StressContextEngine.Command> engine = ActorSystem.create(StressContextEngine.create(), "context-engine");
      ActorSystem.create(StressManagementController.create((engine)), "pda-system-controller");
      //System.out.println("[My] PDA system started");

    }

}
