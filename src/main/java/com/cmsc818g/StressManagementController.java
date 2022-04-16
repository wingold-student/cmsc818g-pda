package com.cmsc818g;
import java.time.Duration;

import com.cmsc818g.StressContextEngine.StressContextEngine;
import com.cmsc818g.StressUIManager.StressUIManager;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;


public class StressManagementController extends AbstractBehavior<StressManagementController.Command> {

    public interface Command {
      //void execute();
    }
  
    public static class controllerResponse implements Command {
        public final String message;
    
        public controllerResponse(String message) {
          this.message = message;
        }
      }//end of controllerResponse

    public static final class controllerGreet implements Command{
      public final String whom;
      public final ActorRef<controllerResponse> replyTo;
  
      public controllerGreet(String whom, ActorRef<controllerResponse> replyTo) {
        this.whom = whom;
        this.replyTo = replyTo;
      }
    }//end of controllerGreet
    
    public static Behavior<Command> create() {
        System.out.println("[My] Controller actor created");
        return Behaviors.setup(context -> new StressManagementController(context));
    }
    
    private ActorRef<StressUIManager.Command> uiManagerActor;
    private ActorRef<StressContextEngine.Command> contextEngineActor;

    private StressManagementController(ActorContext<Command> context) {
        super(context);
        System.out.println("[My] StressManagementController ask ContextEngine");

        uiManagerActor = context.spawn(StressUIManager.create(), "UIManager");
        contextEngineActor = context.spawn(StressContextEngine.create(), "ContextEngine");
    
        // asking someone requires a timeout, if the timeout hits without response
        // the ask is failed with a TimeoutException
        final Duration timeout = Duration.ofSeconds(5);
 
        context.ask(
            StressContextEngine.engineResponse.class,
            contextEngineActor,
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
            contextEngineActor,
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

}
