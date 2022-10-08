/*
 * Copyright (c) 2021-2022 - Yupiik SAS - https://www.yupiik.com
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.yupiik.http;

import io.yupiik.uship.webserver.tomcat.TomcatWebServer;
import io.yupiik.uship.webserver.tomcat.TomcatWebServerConfiguration;
import org.apache.catalina.servlets.DefaultServlet;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import static java.util.Optional.ofNullable;

public final class SimpleHttpServer {
    private SimpleHttpServer() {
        // no-op
    }

    public static void main(final String... args) throws InterruptedException {
        final var port = args.length < 1 ?
                ofNullable(System.getenv("PORT")).map(Integer::parseInt).orElse(8080) :
                Integer.parseInt(args[0]);
        final var docBase = args.length < 2 ?
                ofNullable(System.getenv("DOC_BASE")).orElse("/opt/yupiik/www") :
                args[1];

        final var configuration = new TomcatWebServerConfiguration();
        configuration.setPort(port);
        configuration.setContextCustomizers(List.of(c -> {
            c.setDocBase(docBase);
            c.addWelcomeFile("index.html");
        }));
        configuration.setInitializers(List.of((ignored, sc) -> sc.addServlet("default", new DefaultServlet()).addMapping("/")));
        try (final var server = new TomcatWebServer(configuration)) {
            server.create();
            final var await = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.close();
                } finally {
                    await.countDown();
                }
            }, SimpleHttpServer.class.getName() + "-stop"));
            await.await();
        }
    }
}
