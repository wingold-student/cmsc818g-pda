package com.cmsc818g.StressUIManager;

import com.cmsc818g.StressContextEngine.Reporters.Reporter;
import com.cmsc818g.StressUIManager.StressWebHandler.CombinedEngineData;
import com.cmsc818g.StressUIManager.StressWebHandler.FrontEndData;
import com.cmsc818g.StressUIManager.StressWebHandler.ReceiveCombinedData;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressUIManager extends AbstractBehavior<StressUIManager.Command> {
    public interface Command {}

    public final static class ReceiveCombinedData implements Command {
        public final CombinedEngineData combinedData;

        public ReceiveCombinedData(CombinedEngineData combinedData) {
            this.combinedData = combinedData;
        }
    }

    public final static class ReceiveSchedulerRef implements Command {
        public final ActorRef<Reporter.Command> scheduler;

        public ReceiveSchedulerRef(ActorRef<Reporter.Command> scheduler) {
            this.scheduler = scheduler;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressUIManager(context));
    }

    private ActorRef<StressWebServer.Command> webServerActor;
    private ActorRef<StressWebHandler.Command> webHandlerActor;
    private WebRoutes webRoutes;

    public StressUIManager(ActorContext<Command> context) {
        super(context);
        context.getLog().info("StressUIManager started");

        webHandlerActor = context.spawn(StressWebHandler.create(), "WebHandler");
        webRoutes = new WebRoutes(context.getSystem(), webHandlerActor);

        // Start the server with routing.
        webServerActor = context.spawn(StressWebServer.create(), "WebServer");
        webServerActor.tell(new StressWebServer.StartServer(webRoutes.webRoutes()));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ReceiveCombinedData.class, this::onReceiveCombinedData)
            .onMessage(ReceiveSchedulerRef.class, this::onReceiveSchedulerRef)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Command> onReceiveCombinedData(ReceiveCombinedData msg) {
        webHandlerActor.tell(new StressWebHandler.ReceiveCombinedData(msg.combinedData));
        return this;
    }

    private Behavior<Command> onReceiveSchedulerRef(ReceiveSchedulerRef msg) {
        webHandlerActor.tell(new StressWebHandler.ReceiveSchedulerRef(msg.scheduler));
        return this;
    }

    private StressUIManager onPostStop() {
        getContext().getLog().info("UI Manager shutting down");
        return this;
    }
}
