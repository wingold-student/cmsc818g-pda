package com.cmsc818g.StressUIManager;

import java.time.Duration;
import java.util.concurrent.CompletionStage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.actor.typed.javadsl.AskPattern;
import akka.http.javadsl.marshallers.jackson.Jackson;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import static akka.http.javadsl.server.Directives.*;

/** WebRoutes just maintains what routes the server will serve.
 * 
 * It will supply the data back to the client depending on the endpoint hit.
 * This could be HTML data or an API endpoint to request data from the PDA.
 * 
 * Thus it can serve HTML, JSON, etc.
 */
public class WebRoutes {
    private final static Logger log = LoggerFactory.getLogger(WebRoutes.class);
    private final ActorRef<StressWebHandler.Command> webHandlerActor;
    public final Duration askTimeout;
    public final Scheduler scheduler;

    public WebRoutes(ActorSystem<?> system, ActorRef<StressWebHandler.Command> webHandlerActor) {
        this.webHandlerActor = webHandlerActor;
        this.scheduler = system.scheduler();
        this.askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    // Just a sample endpoint that will ask the StressWebHandler for test JSON data
    private CompletionStage<StressWebHandler.GetTestJSONResponse> getTestJSON() {
        return AskPattern.ask(webHandlerActor, ref -> new StressWebHandler.GetTestJSON(ref), askTimeout, scheduler);
    }

    // Manages the routes the server will handle and what to do
    public Route webRoutes() {
        return concat(
            path("", () ->
                getFromResource("web/test.html")
            ),
            path("hello", () ->
                get(() ->
                    complete("Hello world!")
                )
            ),
            path("actor", () ->
                get(() ->
                    onSuccess(getTestJSON(),
                        data -> complete(StatusCodes.OK, data, Jackson.marshaller()))
                )
            )
        );
    }
}
