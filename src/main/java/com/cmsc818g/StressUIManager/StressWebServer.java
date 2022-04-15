package com.cmsc818g.StressUIManager;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;

/**
 * TODO: Maybe an Actor just should NOT be the server. Instead it is started in main...
 * Unless it is not actually blocking?
 */
public class StressWebServer extends AbstractBehavior<StressWebServer.Command> {
    public interface Command {}

    public static final class StartServer implements Command {
        final Route route;

        public StartServer(Route route) {
            this.route = route;
        }
    }

    public static final class Hello implements Command {
        final String msg;

        public Hello(String msg) {
            this.msg = msg;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(context -> new StressWebServer(context));
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(StartServer.class, this::onStartServer)
            .onMessage(Hello.class, this::onHello)
            .build();
    } 

   public StressWebServer(ActorContext<Command> context) {
        super(context);
        context.getLog().info("Web Server Actor started");
    }

    private Behavior<Command> onStartServer(StartServer msg) {
        getContext().getLog().info("Starting web server...");
        ActorSystem<?> system = getContext().getSystem();
        Route route = msg.route;

        CompletionStage<ServerBinding> futureBinding =
            Http.get(getContext().getSystem()).newServerAt("localhost", 8080).bind(route);

        futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                InetSocketAddress address = binding.localAddress();
                system.log().info("Server online at http://{}:{}",
                    address.getHostString(),
                    address.getPort());
            } else {
                system.log().error("Failed to bind to HTTP endpoint, terminating", exception);
                system.terminate(); // TODO: Just terminate the actor?
            }
        });

        return this;
    }

    private Behavior<Command> onHello(Hello msg) {
        getContext().getLog().info("Got Hello message: {}", msg.msg);
        return this;
    }
}
