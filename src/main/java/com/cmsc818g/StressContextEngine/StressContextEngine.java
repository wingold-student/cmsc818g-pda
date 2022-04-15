package com.cmsc818g.StressContextEngine;
import java.time.Duration;

import com.cmsc818g.StressManagementController;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressContextEngine extends AbstractBehavior<StressContextEngine.Command> {

    public interface Command {}
    private static final class engineResponse implements Command {
      public final String message;
  
      public engineResponse(String message) {
        this.message = message;
      }
    }//end of engineResponse
  
    public static Behavior<Command> create(ActorRef<StressManagementController.Command> controller) {
      return Behaviors.setup(context -> new StressContextEngine(context, controller));
    }

    public StressContextEngine(ActorContext<Command> context) {
        super(context);
        System.out.println("StressContextEngine actor created");
    }

    private StressContextEngine(ActorContext<Command> context, ActorRef<StressManagementController.Command> controller) {
      super(context);
      System.out.println("StressContextEngine actor created");
  
      // asking someone requires a timeout, if the timeout hits without response
      // the ask is failed with a TimeoutException
        final Duration timeout = Duration.ofSeconds(3);
  
      context.ask(
        StressManagementController.controllerResponse.class,
        controller,
          timeout,
          // construct the outgoing message
          (ActorRef<StressManagementController.controllerResponse> ref) -> new StressManagementController.controllerCommunication(ref),
          // adapt the response (or failure to respond)
          (response, throwable) -> {
            if (response != null) {
              return new engineResponse(response.message);
            } else {
              return new engineResponse("Request failed");
            }
          });
  
      // we can also tie in request context into an interaction, it is safe to look at
      // actor internal state from the transformation function, but remember that it may have
      // changed at the time the response arrives and the transformation is done, best is to
      // use immutable state we have closed over like here.
      final int requestId = 1;
      context.ask(
        StressManagementController.controllerResponse.class,
          controller,
          timeout,
          // construct the outgoing message
          (ActorRef<StressManagementController.controllerResponse> ref) -> new StressManagementController.controllerCommunication(ref),
          // adapt the response (or failure to respond)
          (response, throwable) -> {
            if (response != null) {
              return new engineResponse(requestId + ": " + response.message);
            } else {
              return new engineResponse(requestId + ": Request failed");
            }
          });
    }
  
    @Override
    public Receive<Command> createReceive() {
      return newReceiveBuilder()
          // the adapted message ends up being processed like any other
          // message sent to the actor
          .onMessage(engineResponse.class, this::onAdaptedResponse)
          .build();
    }
  
    private Behavior<Command> onAdaptedResponse(engineResponse response) {
      getContext().getLog().info("Got response from StressManagementController: {}", response.message);
      return this;
    }
}
