package com.example.studentmanagement;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StudentApiTest {
    @LocalServerPort
    private int port;

    @Autowired
    private StudentRepository repository;

    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/students" + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void studentLifecycleAndMissingStudents() throws Exception {
        var created = request("POST", "", """
                {"id":999,"name":"Asha","email":"asha@example.com","course":"Java","age":20}
                """);
        assertThat(created.statusCode()).isEqualTo(201);
        Long id = repository.findAll().get(0).getId();
        assertThat(id).isNotEqualTo(999L);
        assertThat(created.headers().firstValue("Location")).contains("/api/students/" + id);

        var all = request("GET", "", null);
        assertThat(all.statusCode()).isEqualTo(200);
        assertThat(all.body()).contains("Asha");

        var found = request("GET", "/" + id, null);
        assertThat(found.statusCode()).isEqualTo(200);
        assertThat(found.body()).contains("asha@example.com");

        var filtered = request("GET", "/course/java", null);
        assertThat(filtered.statusCode()).isEqualTo(200);
        assertThat(filtered.body()).contains("Asha");
        assertThat(request("GET", "/course/Python", null).body()).isEqualTo("[]");

        var updated = request("PUT", "/" + id, """
                {"name":"Asha Rao","email":"asha.rao@example.com","course":"Python","age":21}
                """);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("Asha Rao", "asha.rao@example.com", "Python", "21");
        assertThat(request("GET", "/" + id, null).body()).contains("Asha Rao");
        assertThat(request("GET", "/course/Java", null).body()).isEqualTo("[]");

        var deleted = request("DELETE", "/" + id, null);
        assertThat(deleted.statusCode()).isEqualTo(204);
        assertThat(deleted.body()).isEmpty();
        assertThat(request("GET", "", null).body()).isEqualTo("[]");

        for (String method : new String[]{"GET", "PUT", "DELETE"}) {
            var missing = request(method, "/" + id, method.equals("PUT") ? "{}" : null);
            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(missing.body()).contains("Student not found with id: " + id);
        }
    }
}
