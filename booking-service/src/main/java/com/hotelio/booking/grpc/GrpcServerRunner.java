package com.hotelio.booking.grpc;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class GrpcServerRunner implements ApplicationRunner {

    private final int port;
    private final BookingGrpcService bookingGrpcService;
    private Server server;

    public GrpcServerRunner(
            @Value("${booking.grpc.port:9090}") int port,
            BookingGrpcService bookingGrpcService
    ) {
        this.port = port;
        this.bookingGrpcService = bookingGrpcService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        server = NettyServerBuilder
                .forPort(port)
                .addService(bookingGrpcService)
                .build()
                .start();

        // ВАЖНО: блокируем поток, чтобы приложение не завершалось
        server.awaitTermination();
    }

    @PreDestroy
    public void shutdown() {
        if (server != null) {
            server.shutdown();
        }
    }
}
