package com.cmsc818g.StressUIManager;

import com.cmsc818g.StressUIManager.StressWebHandler.FrontEndData;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

public class StressUIManager extends AbstractBehavior<StressUIManager.Command> {
    public interface Command {}

    public final static class ReceiveRecommendationData implements Command {
        public final FrontEndData recommendationData;

        public ReceiveRecommendationData(FrontEndData recommendationData) {
            this.recommendationData = recommendationData;
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
            .onMessage(ReceiveRecommendationData.class, this::onReceiveRecommendationData)
            .onSignal(PostStop.class, signal -> onPostStop())
            .build();
    }

    private Behavior<Command> onReceiveRecommendationData(ReceiveRecommendationData msg) {
        webHandlerActor.tell(new StressWebHandler.ReceiveRecommendationData(msg.recommendationData));
        return this;
    }

    private StressUIManager onPostStop() {
        getContext().getLog().info("UI Manager shutting down");
        return this;
    }
}
