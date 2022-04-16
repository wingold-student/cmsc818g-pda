package com.cmsc818g.StressUIManager;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import akka.http.javadsl.testkit.JUnitRouteTest;
import akka.http.javadsl.testkit.TestRoute;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.StatusCodes;
import akka.actor.testkit.typed.javadsl.TestKitJunitResource;
import akka.actor.typed.ActorRef;


public class WebRoutesTest extends JUnitRouteTest {
    
    @ClassRule
    public static TestKitJunitResource testkit = new TestKitJunitResource();

    private static ActorRef<StressWebHandler.Command> webHandlerActor;
    private TestRoute appRoute;

    @BeforeClass
    public static void beforeClass() {
        webHandlerActor = testkit.spawn(StressWebHandler.create());
    }

    @Before
    public void before() {
        WebRoutes webRoutes = new WebRoutes(testkit.system(), webHandlerActor);
        appRoute = testRoute(webRoutes.webRoutes());
    }

    @AfterClass
    public static void afterClass() {
        testkit.stop(webHandlerActor);
    }

    @Test
    public void testGetRootPage() {
        appRoute.run(HttpRequest.GET("/"))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("text/html");
    }

    @Test
    public void testGetHelloPage() {
        appRoute.run(HttpRequest.GET("/hello"))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("text/plain");
    }

    @Test
    public void testGetTestJSON() {
        /**
         * Expecting:
         *  {
         *      testData:
         *          {
         *              field: fieldData
        *           }
         *  }
         */
        appRoute.run(HttpRequest.GET("/actor"))
                .assertStatusCode(StatusCodes.OK)
                .assertMediaType("application/json")
                .assertEntity("{\"testData\":{\"field\":\"fieldData\"}}");
    }
}
