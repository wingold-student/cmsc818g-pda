package com.cmsc818g.StressUIManager;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Scheduler;
import akka.http.javadsl.server.Route;
import static akka.http.javadsl.server.Directives.*;

public class WebRoutes {
    private final static Logger log = LoggerFactory.getLogger(WebRoutes.class);
    //private final ActorRef<StressWebHandler.Command> webHandlerActor;
    public final Duration askTimeout;
    public final Scheduler scheduler;

    public WebRoutes(ActorSystem<?> system) {
        // this.webHandlerActor = webHandlerActor;
        this.scheduler = system.scheduler();
        this.askTimeout = system.settings().config().getDuration("my-app.routes.ask-timeout");
    }

    public Route webRoutes() {
        return concat(
            path("hello", () ->
                get(() ->
                    complete("Hello world!")
                )
            ),
            path("", () ->
                getFromFile("C:/Users/willi/School/UMD/SPRING_2022/818G/GROUP PROJECT/PDA/pda/src/main/web/test.html")
            )
        );
    }
}
