package com.cmsc818g;

import java.io.IOException;
import java.util.concurrent.CompletionStage;

import akka.actor.typed.ActorSystem;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.AllDirectives;
import akka.http.javadsl.server.Route;

/**
 * Hello world!
 *
 */
public class App extends AllDirectives
{
    public static void main( String[] args )
    {
        ActorSystem<Void> system = ActorSystem.create(PdaSupervisor.create(), "pda-system");
        /*
        final Http http = Http.get(system);

        App app = new App();

        final CompletionStage<ServerBinding> binding = http.newServerAt("localhost", 8080)
            .bind(app.createRoute());
        System.out.println("Server online at localhost:8080\nPress RETURN to stop...");

        binding
            .thenCompose(ServerBinding::unbind)
            .thenAccept(unbound -> system.terminate());
            */

        try {
            System.in.read();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        system.terminate();
    }

    /*
    private Route createRoute() {
        return concat(
            path("hello", () ->
                get(() ->
                    complete("<h1>Hello world!</h1>"))));
    }
    */
}
