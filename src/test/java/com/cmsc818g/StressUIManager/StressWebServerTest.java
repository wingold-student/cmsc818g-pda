package com.cmsc818g.StressUIManager;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.server.Route;
import akka.http.javadsl.testkit.TestRoute;


import java.util.concurrent.TimeUnit;

public class StressWebServerTest extends JUnitRouteTest {
    @ClassRule public static final TestKitJunitResource testkit = new TestKitJunitResource();

    private static ActorRef<StressWebServer.Command> webServerActor;
    private TestRoute appRoute;

    @Before
    public void before() {
        webServerActor = testkit.spawn(StressWebServer.create());
    }

    @After
    public void after() {
        webServerActor.tell(new StressWebServer.StopServer());
    }

    @Test
    public void testGetRootPage() {
        Route route = concat(
            path("", () ->
                get(() ->
                    complete("Testing")
                )
            )
        );
        appRoute = testRoute(route);

        webServerActor.tell(new StressWebServer.StartServer(route));
        try {
            TimeUnit.SECONDS.sleep(2);
            appRoute.run(HttpRequest.GET("http://localhost:8080/"))
                    .assertStatusCode(StatusCodes.OK)
                    .assertMediaType("text/plain");
        } catch (InterruptedException e) {
            appRoute.run(HttpRequest.GET("http://localhost:8080/"))
                    .assertStatusCode(StatusCodes.NOT_FOUND);
        }
    }
}
